/*
 * Java translation derived from OpenLogReplicator:
 * src/reader/Reader.cpp and src/reader/ReaderFilesystem.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.source.OracleRedoLogKind;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class RedoReader implements AutoCloseable {
    private static final int MAX_HEADER_BYTES = 8_192;
    private static final int MAX_TRANSIENT_CHECKSUM_FAILURES = 20;

    private final OracleRedoLog redoLog;
    private final DatabaseIdentity databaseIdentity;
    private final boolean verifyChecksum;
    private final FileChannel channel;
    private final RedoFileHeaderParser fileHeaderParser;

    private RedoFileHeader header;
    private RedoBlockHeaderParser blockHeaderParser;
    private long nextBlockNumber;
    private boolean finished;
    private int badHeaderChecksumCount;
    private int badBlockChecksumCount;
    private long badChecksumBlockNumber = -1;

    private RedoReader(
            OracleRedoLog redoLog,
            DatabaseIdentity databaseIdentity,
            boolean verifyChecksum,
            FileChannel channel) {
        this.redoLog = redoLog;
        this.databaseIdentity = databaseIdentity;
        this.verifyChecksum = verifyChecksum;
        this.channel = channel;
        fileHeaderParser = new RedoFileHeaderParser();
        nextBlockNumber = 0;
    }

    public static Optional<RedoReader> open(
            OracleRedoLog redoLog,
            DatabaseIdentity databaseIdentity,
            FileOffset startOffset,
            boolean verifyChecksum) throws IOException {
        FileChannel channel = FileChannel.open(
                redoLog.localPath(), StandardOpenOption.READ);
        RedoReader reader = new RedoReader(
                redoLog, databaseIdentity, verifyChecksum, channel);
        try {
            if (!reader.reloadHeader()) {
                reader.close();
                return Optional.empty();
            }
            reader.initializeOffset(startOffset);
            return Optional.of(reader);
        } catch (IOException | RuntimeException e) {
            try {
                reader.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    public RedoFileHeader header() {
        return header;
    }

    public FileOffset nextOffset() {
        return FileOffset.fromBlock(nextBlockNumber, header.blockSize());
    }

    public RedoReadBatch read(int maximumBlocks) throws IOException {
        if (maximumBlocks <= 0) {
            throw new IllegalArgumentException(
                    "maximumBlocks must be positive");
        }
        FileOffset startOffset = nextOffset();
        if (finished) {
            return new RedoReadBatch(
                    RedoReadStatus.FINISHED, startOffset, List.of());
        }
        if (redoLog.kind() == OracleRedoLogKind.ONLINE
                && !reloadHeader()) {
            return new RedoReadBatch(
                    RedoReadStatus.WAITING, startOffset, List.of());
        }

        long readableEnd = readableEnd();
        long offset = startOffset.value();
        if (Long.compareUnsigned(offset, readableEnd) >= 0) {
            return atEnd(startOffset, readableEnd);
        }

        long availableBlocks = (readableEnd - offset) / header.blockSize();
        int blocksToRead = (int) Math.min(maximumBlocks, availableBlocks);
        List<byte[]> blocks = readBlocks(blocksToRead, offset);
        if (blocks.isEmpty()) {
            if (redoLog.kind() == OracleRedoLogKind.ONLINE) {
                return new RedoReadBatch(
                        RedoReadStatus.WAITING, startOffset, List.of());
            }
            throw truncated("archive contains an empty block at "
                    + nextBlockNumber);
        }

        nextBlockNumber += blocks.size();
        if (!header.nextScn().isNone() && header.blockCount() > 0
                && nextBlockNumber == header.blockCount()) {
            finished = true;
        }
        return new RedoReadBatch(
                RedoReadStatus.DATA, startOffset, blocks);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private List<byte[]> readBlocks(
            int blocksToRead, long offset) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(
                blocksToRead * header.blockSize());
        int bytesRead = readAtMost(buffer, offset);
        int completeBlocks = bytesRead / header.blockSize();
        if (completeBlocks == 0) {
            if (redoLog.kind() == OracleRedoLogKind.ONLINE) {
                return List.of();
            }
            throw truncated("archive ended inside block " + nextBlockNumber);
        }

        return parseBlocks(buffer.array(), completeBlocks);
    }

    private List<byte[]> parseBlocks(byte[] bytes, int completeBlocks) {
        List<byte[]> blocks = new ArrayList<>(completeBlocks);
        for (int index = 0; index < completeBlocks; index++) {
            int blockOffset = index * header.blockSize();
            long blockNumber = nextBlockNumber + index;
            RedoBlockHeader blockHeader;
            try {
                blockHeader = blockHeaderParser.parse(
                        bytes, blockOffset, blockNumber, Seq.zero(),
                        verifyChecksum);
            } catch (RedoLogException e) {
                if (!isTransientOnlineChecksum(e, blockNumber)) {
                    throw e;
                }
                break;
            }
            if (blockHeader.isEmpty()) {
                break;
            }
            badBlockChecksumCount = 0;
            badChecksumBlockNumber = -1;
            if (!acceptSequence(blockHeader.sequence(), blockNumber)) {
                break;
            }
            blocks.add(Arrays.copyOfRange(
                    bytes, blockOffset, blockOffset + header.blockSize()));
        }
        return List.copyOf(blocks);
    }

    private boolean reloadHeader() throws IOException {
        byte[] bytes = readHeaderBytes();
        if (bytes.length < 32) {
            if (redoLog.kind() == OracleRedoLogKind.ONLINE) {
                return false;
            }
            throw truncated("archive header is shorter than 32 bytes");
        }

        RedoFileHeader current = parseHeader(bytes);
        if (current == null || !acceptHeaderSequence(current)) {
            return false;
        }

        validateHeader(current);
        if (header != null) {
            validateStableHeader(current);
        }
        header = current;
        blockHeaderParser = new RedoBlockHeaderParser(
                current.byteOrder(), current.blockSize());
        return true;
    }

    private RedoFileHeader parseHeader(byte[] bytes) {
        RedoFileHeader current;
        try {
            current = fileHeaderParser.parse(bytes, verifyChecksum);
        } catch (RedoLogException e) {
            if (redoLog.kind() == OracleRedoLogKind.ONLINE
                    && e.getErrorCode() == 40003) {
                return null;
            }
            if (redoLog.kind() == OracleRedoLogKind.ONLINE
                    && e.getErrorCode() == 60025
                    && badHeaderChecksumCount
                    < MAX_TRANSIENT_CHECKSUM_FAILURES - 1) {
                badHeaderChecksumCount++;
                return null;
            }
            throw e;
        }
        badHeaderChecksumCount = 0;
        if (current.isEmpty()) {
            if (redoLog.kind() == OracleRedoLogKind.ONLINE) {
                return null;
            }
            throw truncated("archive header is not initialized");
        }
        return current;
    }

    private boolean acceptHeaderSequence(RedoFileHeader current) {
        int sequenceComparison = current.sequence().compareTo(
                redoLog.sequence());
        if (sequenceComparison == 0) {
            return true;
        }
        if (redoLog.kind() == OracleRedoLogKind.ARCHIVED) {
            throw new RedoLogException(60024,
                    "invalid archive header sequence, found: "
                            + current.sequence() + ", expected: "
                            + redoLog.sequence());
        }
        if (sequenceComparison < 0) {
            return false;
        }
        throw new RedoRuntimeException(10044,
                "online redo was overwritten: " + redoLog.localPath()
                        + ", expected sequence " + redoLog.sequence()
                        + ", found " + current.sequence());
    }

    private byte[] readHeaderBytes() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_HEADER_BYTES);
        int bytesRead = readAtMost(buffer, 0);
        return Arrays.copyOf(buffer.array(), bytesRead);
    }

    private int readAtMost(ByteBuffer buffer, long offset) throws IOException {
        long position = offset;
        int total = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read <= 0) {
                break;
            }
            total += read;
            position += read;
        }
        return total;
    }

    private void initializeOffset(FileOffset startOffset) {
        long firstDataOffset = (long) header.blockSize() * 2;
        if (startOffset.isZero()) {
            nextBlockNumber = 2;
            return;
        }
        if (!startOffset.isBlockAligned(header.blockSize())
                || Long.compareUnsigned(
                startOffset.value(), firstDataOffset) < 0) {
            throw new IllegalArgumentException(
                    "redo start offset must be block aligned and after the file header");
        }
        nextBlockNumber = startOffset.block(header.blockSize());
    }

    private long readableEnd() throws IOException {
        long size = channel.size();
        long alignedSize = size - size % header.blockSize();
        long offset = nextOffset().value();
        if (Long.compareUnsigned(offset, alignedSize) > 0) {
            throw truncated("file shrank below consumed offset " + offset);
        }

        if (header.blockCount() == 0) {
            return alignedSize;
        }
        long headerEnd = header.blockCount() * header.blockSize();
        if (redoLog.kind() == OracleRedoLogKind.ARCHIVED) {
            if (Long.compareUnsigned(alignedSize, headerEnd) < 0) {
                throw truncated("archive size " + alignedSize
                        + " is below header size " + headerEnd);
            }
            return headerEnd;
        }
        return Math.min(alignedSize, headerEnd);
    }

    private RedoReadBatch atEnd(
            FileOffset startOffset, long readableEnd) {
        if (Long.compareUnsigned(startOffset.value(), readableEnd) > 0) {
            throw truncated("file ended before offset " + startOffset);
        }
        if (!header.nextScn().isNone()) {
            finished = true;
            return new RedoReadBatch(
                    RedoReadStatus.FINISHED, startOffset, List.of());
        }
        if (redoLog.kind() == OracleRedoLogKind.ONLINE) {
            return new RedoReadBatch(
                    RedoReadStatus.WAITING, startOffset, List.of());
        }
        throw truncated("archive ended without a next SCN");
    }

    private boolean acceptSequence(Seq sequence, long blockNumber) {
        int comparison = sequence.compareTo(redoLog.sequence());
        if (comparison == 0) {
            return true;
        }
        if (redoLog.kind() == OracleRedoLogKind.ARCHIVED) {
            throw new RedoLogException(60024,
                    "invalid archive block sequence, found: " + sequence
                            + ", expected: " + redoLog.sequence());
        }
        if (comparison < 0) {
            return false;
        }
        throw new RedoRuntimeException(10044,
                "online redo was overwritten at block " + blockNumber
                        + ": expected sequence " + redoLog.sequence()
                        + ", found " + sequence);
    }

    private boolean isTransientOnlineChecksum(
            RedoLogException error, long blockNumber) {
        if (redoLog.kind() != OracleRedoLogKind.ONLINE
                || error.getErrorCode() != 60025) {
            return false;
        }
        if (badChecksumBlockNumber != blockNumber) {
            badChecksumBlockNumber = blockNumber;
            badBlockChecksumCount = 0;
        }
        if (badBlockChecksumCount
                >= MAX_TRANSIENT_CHECKSUM_FAILURES - 1) {
            return false;
        }
        badBlockChecksumCount++;
        return true;
    }

    private void validateHeader(RedoFileHeader current) {
        if (current.databaseId() != databaseIdentity.databaseId()) {
            throw new RedoLogException(40008,
                    "redo DBID " + current.databaseId()
                            + " does not match Oracle DBID "
                            + databaseIdentity.databaseId());
        }
        if (current.resetlogs() != databaseIdentity.resetlogsId()) {
            throw new RedoLogException(40008,
                    "redo resetlogs " + current.resetlogs()
                            + " does not match Oracle resetlogs "
                            + databaseIdentity.resetlogsId());
        }
        if (current.thread() != redoLog.thread()) {
            throw new RedoLogException(40008,
                    "redo thread " + current.thread()
                            + " does not match catalog thread "
                            + redoLog.thread());
        }
        if (!current.firstScn().equals(redoLog.firstScn())) {
            throw new RedoLogException(40008,
                    "redo first SCN " + current.firstScn()
                            + " does not match catalog first SCN "
                            + redoLog.firstScn());
        }
        if (redoLog.kind() == OracleRedoLogKind.ARCHIVED
                && !current.nextScn().equals(redoLog.nextScn())) {
            throw new RedoLogException(40009,
                    "redo next SCN " + current.nextScn()
                            + " does not match catalog next SCN "
                            + redoLog.nextScn());
        }
    }

    private void validateStableHeader(RedoFileHeader current) {
        if (current.blockSize() != header.blockSize()
                || !current.byteOrder().equals(header.byteOrder())
                || current.compatibleVersion() != header.compatibleVersion()
                || current.databaseId() != header.databaseId()
                || current.resetlogs() != header.resetlogs()
                || current.thread() != header.thread()
                || !current.firstScn().equals(header.firstScn())) {
            throw new RedoLogException(40008,
                    "online redo header changed within sequence "
                            + redoLog.sequence());
        }
    }

    private RedoLogException truncated(String reason) {
        return new RedoLogException(40003,
                "file " + redoLog.localPath() + ": " + reason);
    }
}
