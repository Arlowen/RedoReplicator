/*
 * Java translation derived from OpenLogReplicator block header handling in
 * src/reader/Reader.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.Seq;

import java.nio.ByteOrder;

public final class RedoBlockHeaderParser {
    private final RedoByteReader byteReader;
    private final RedoByteReader nativeByteReader;
    private final ByteOrder byteOrder;
    private final int blockSize;

    public RedoBlockHeaderParser(ByteOrder byteOrder, int blockSize) {
        this.byteOrder = byteOrder;
        this.blockSize = blockSize;
        byteReader = new RedoByteReader(byteOrder);
        nativeByteReader = new RedoByteReader(ByteOrder.nativeOrder());
        requireSupportedBlockSize(blockSize);
    }

    public RedoBlockHeader parse(byte[] data, int offset, long expectedBlockNumber,
                                 Seq expectedSequence, boolean verifyChecksum) {
        if (offset < 0 || offset > data.length - blockSize) {
            throw new RedoLogException(40003, "redo block is shorter than " + blockSize + " bytes");
        }
        if (data[offset] == 0 && data[offset + 1] == 0) {
            return new RedoBlockHeader(true, 0, 0, Seq.zero(), 0);
        }

        int type = data[offset + 1] & 0xFF;
        int expectedType = 0x22;
        if (blockSize == 4096) {
            expectedType = 0x82;
        }
        if (type != expectedType) {
            throw new RedoLogException(40001, "invalid block size: " + blockSize
                    + ", header[1]: " + type);
        }

        long blockNumber = byteReader.readUnsignedInt(data, offset + 4);
        Seq sequence = Seq.of(byteReader.readUnsignedInt(data, offset + 8));
        if (!expectedSequence.equals(Seq.zero()) && !expectedSequence.equals(sequence)) {
            throw new RedoLogException(60024, "invalid header sequence, found: " + sequence
                    + ", expected: " + expectedSequence);
        }
        if (blockNumber != expectedBlockNumber) {
            throw new RedoLogException(40002, "invalid header block number: " + blockNumber
                    + ", expected: " + expectedBlockNumber);
        }

        int checksum = byteReader.readUnsignedShort(data, offset + 14);
        if (verifyChecksum) {
            int calculated = calculateChecksum(data, offset);
            if (checksum != calculated) {
                throw new RedoLogException(60025, "invalid header checksum, expected: "
                        + checksum + ", calculated: " + calculated);
            }
        }
        return new RedoBlockHeader(false, type, blockNumber, sequence, checksum);
    }

    public int calculateChecksum(byte[] data, int offset) {
        if (offset < 0 || offset > data.length - blockSize) {
            throw new IllegalArgumentException("Redo block is outside the source buffer");
        }
        int oldChecksum = byteReader.readUnsignedShort(data, offset + 14);
        long checksum = 0;
        for (int position = 0; position < blockSize; position += Long.BYTES) {
            // Reader.cpp XORs native uint64_t words, while the stored checksum
            // itself follows the redo file byte order.
            checksum ^= nativeByteReader.readLong(data, offset + position);
        }
        checksum ^= checksum >>> 32;
        checksum ^= checksum >>> 16;
        checksum ^= oldChecksum;
        return (int) checksum & 0xFFFF;
    }

    public ByteOrder byteOrder() {
        return byteOrder;
    }

    public int blockSize() {
        return blockSize;
    }

    private static void requireSupportedBlockSize(int blockSize) {
        if (blockSize != 512 && blockSize != 1024 && blockSize != 4096) {
            throw new IllegalArgumentException("Unsupported redo block size: " + blockSize);
        }
    }
}
