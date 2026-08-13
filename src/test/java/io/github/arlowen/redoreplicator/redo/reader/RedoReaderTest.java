/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.source.OracleRedoLogKind;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedoReaderTest {
    private static final int BLOCK_SIZE = 512;
    private static final Seq SEQUENCE = Seq.of(77);
    private static final Scn FIRST_SCN = Scn.of(0x0000_1234_5678_9ABCL);
    private static final Scn NEXT_SCN = Scn.of(0x0000_1234_5678_ABCDL);
    private static final DatabaseIdentity DATABASE_IDENTITY =
            new DatabaseIdentity(0xF000_0001L, 1, 5);

    @TempDir
    Path redoDirectory;

    @Test
    void readsArchiveBlocksAndFinishesAtHeaderBoundary() throws Exception {
        Path path = redoDirectory.resolve("archive77.arc");
        Files.write(path, redoFile(
                4, NEXT_SCN, 77,
                List.of(block(2, 77), block(3, 77)), 4));
        OracleRedoLog log = log(OracleRedoLogKind.ARCHIVED, path, 77);

        try (RedoReader reader = RedoReader.open(
                log, DATABASE_IDENTITY, FileOffset.zero(), true)
                .orElseThrow()) {
            RedoReadBatch first = reader.read(1);
            RedoReadBatch second = reader.read(8);
            RedoReadBatch finished = reader.read(8);

            assertEquals(RedoReadStatus.DATA, first.status());
            assertEquals(FileOffset.fromBlock(2, BLOCK_SIZE),
                    first.startOffset());
            assertEquals(1, first.blocks().size());
            assertEquals(RedoReadStatus.DATA, second.status());
            assertEquals(FileOffset.fromBlock(3, BLOCK_SIZE),
                    second.startOffset());
            assertEquals(RedoReadStatus.FINISHED, finished.status());
            assertEquals(FileOffset.fromBlock(4, BLOCK_SIZE),
                    reader.nextOffset());
        }
    }

    @Test
    void resumesFromPersistedBlockAlignedOffset() throws Exception {
        Path path = redoDirectory.resolve("archive77.arc");
        Files.write(path, redoFile(
                4, NEXT_SCN, 77,
                List.of(block(2, 77), block(3, 77)), 4));

        try (RedoReader reader = RedoReader.open(
                log(OracleRedoLogKind.ARCHIVED, path, 77),
                DATABASE_IDENTITY,
                FileOffset.fromBlock(3, BLOCK_SIZE), true)
                .orElseThrow()) {
            RedoReadBatch batch = reader.read(8);

            assertEquals(FileOffset.fromBlock(3, BLOCK_SIZE),
                    batch.startOffset());
            assertEquals(1, batch.blocks().size());
            assertEquals(RedoReadStatus.FINISHED,
                    reader.read(8).status());
        }
    }

    @Test
    void waitsForOnlineGrowthThenFinishesAfterLogSwitch() throws Exception {
        Path path = redoDirectory.resolve("redo01.log");
        byte[] initial = redoFile(
                3, Scn.none(), 77,
                List.of(block(2, 77)), 4);
        Files.write(path, initial);

        try (RedoReader reader = RedoReader.open(
                log(OracleRedoLogKind.ONLINE, path, 77),
                DATABASE_IDENTITY, FileOffset.zero(), true)
                .orElseThrow()) {
            assertEquals(RedoReadStatus.DATA, reader.read(8).status());
            assertEquals(RedoReadStatus.WAITING, reader.read(8).status());

            byte[] grown = redoFile(
                    4, Scn.none(), 77,
                    List.of(block(2, 77), block(3, 77)), 4);
            Files.write(path, grown);
            RedoReadBatch grownBatch = reader.read(8);
            assertEquals(RedoReadStatus.DATA, grownBatch.status());
            assertEquals(FileOffset.fromBlock(3, BLOCK_SIZE),
                    grownBatch.startOffset());

            Files.write(path, redoFile(
                    4, NEXT_SCN, 77,
                    List.of(block(2, 77), block(3, 77)), 4));
            assertEquals(RedoReadStatus.FINISHED,
                    reader.read(8).status());
        }
    }

    @Test
    void distinguishesNotInitializedFromOverwrittenOnlineFile()
            throws Exception {
        Path oldPath = redoDirectory.resolve("old-redo.log");
        Files.write(oldPath, redoFile(
                3, Scn.none(), 76,
                List.of(block(2, 76)), 3));

        Optional<RedoReader> notInitialized = RedoReader.open(
                log(OracleRedoLogKind.ONLINE, oldPath, 77),
                DATABASE_IDENTITY, FileOffset.zero(), true);
        assertFalse(notInitialized.isPresent());

        Path overwrittenPath = redoDirectory.resolve("overwritten-redo.log");
        Files.write(overwrittenPath, redoFile(
                3, Scn.none(), 78,
                List.of(block(2, 78)), 3));
        RedoRuntimeException overwritten = assertThrows(
                RedoRuntimeException.class,
                () -> RedoReader.open(
                        log(OracleRedoLogKind.ONLINE, overwrittenPath, 77),
                        DATABASE_IDENTITY, FileOffset.zero(), true));
        assertEquals(10044, overwritten.getErrorCode());
    }

    @Test
    void detectsOnlineOverwriteAndTruncationAfterReadingStarted()
            throws Exception {
        Path overwrittenPath = redoDirectory.resolve("switching-redo.log");
        Files.write(overwrittenPath, redoFile(
                3, Scn.none(), 77,
                List.of(block(2, 77)), 3));
        try (RedoReader reader = RedoReader.open(
                log(OracleRedoLogKind.ONLINE, overwrittenPath, 77),
                DATABASE_IDENTITY, FileOffset.zero(), true)
                .orElseThrow()) {
            assertEquals(RedoReadStatus.DATA, reader.read(1).status());
            Files.write(overwrittenPath, redoFile(
                    3, Scn.none(), 78,
                    List.of(block(2, 78)), 3));

            RedoRuntimeException error = assertThrows(
                    RedoRuntimeException.class, () -> reader.read(1));
            assertEquals(10044, error.getErrorCode());
        }

        Path truncatedPath = redoDirectory.resolve("shrinking-redo.log");
        Files.write(truncatedPath, redoFile(
                4, Scn.none(), 77,
                List.of(block(2, 77), block(3, 77)), 4));
        try (RedoReader reader = RedoReader.open(
                log(OracleRedoLogKind.ONLINE, truncatedPath, 77),
                DATABASE_IDENTITY, FileOffset.zero(), true)
                .orElseThrow()) {
            assertEquals(RedoReadStatus.DATA, reader.read(1).status());
            try (FileChannel channel = FileChannel.open(
                    truncatedPath, StandardOpenOption.WRITE)) {
                channel.truncate(BLOCK_SIZE * 2L);
            }

            RedoLogException error = assertThrows(
                    RedoLogException.class, () -> reader.read(1));
            assertEquals(40003, error.getErrorCode());
        }
    }

    @Test
    void stopsOnTruncatedArchiveAndWrongArchiveSequence()
            throws Exception {
        Path truncatedPath = redoDirectory.resolve("truncated.arc");
        Files.write(truncatedPath, redoFile(
                4, NEXT_SCN, 77,
                List.of(block(2, 77)), 3));
        try (RedoReader reader = RedoReader.open(
                log(OracleRedoLogKind.ARCHIVED, truncatedPath, 77),
                DATABASE_IDENTITY, FileOffset.zero(), true)
                .orElseThrow()) {
            RedoLogException truncated = assertThrows(
                    RedoLogException.class, () -> reader.read(8));
            assertEquals(40003, truncated.getErrorCode());
        }

        Path wrongSequencePath = redoDirectory.resolve("wrong-sequence.arc");
        Files.write(wrongSequencePath, redoFile(
                3, NEXT_SCN, 76,
                List.of(block(2, 76)), 3));
        RedoLogException wrongSequence = assertThrows(
                RedoLogException.class,
                () -> RedoReader.open(
                        log(OracleRedoLogKind.ARCHIVED,
                                wrongSequencePath, 77),
                        DATABASE_IDENTITY, FileOffset.zero(), true));
        assertEquals(60024, wrongSequence.getErrorCode());
    }

    @Test
    void stopsWhenMappedFileBelongsToAnotherDatabase() throws Exception {
        Path path = redoDirectory.resolve("wrong-database.arc");
        Files.write(path, redoFile(
                3, NEXT_SCN, 77,
                List.of(block(2, 77)), 3));
        DatabaseIdentity wrongIdentity = new DatabaseIdentity(9, 1, 5);

        RedoLogException error = assertThrows(
                RedoLogException.class,
                () -> RedoReader.open(
                        log(OracleRedoLogKind.ARCHIVED, path, 77),
                        wrongIdentity, FileOffset.zero(), true));

        assertEquals(40008, error.getErrorCode());
    }

    @Test
    void boundsTransientOnlineChecksumRetries() throws Exception {
        Path path = redoDirectory.resolve("partial-online-write.log");
        byte[] damagedBlock = block(2, 77);
        damagedBlock[40] ^= 1;
        Files.write(path, redoFile(
                3, Scn.none(), 77, List.of(damagedBlock), 3));

        try (RedoReader reader = RedoReader.open(
                log(OracleRedoLogKind.ONLINE, path, 77),
                DATABASE_IDENTITY, FileOffset.zero(), true)
                .orElseThrow()) {
            for (int attempt = 0; attempt < 19; attempt++) {
                assertEquals(RedoReadStatus.WAITING,
                        reader.read(1).status());
            }
            RedoLogException error = assertThrows(
                    RedoLogException.class, () -> reader.read(1));
            assertEquals(60025, error.getErrorCode());
        }
    }

    private static OracleRedoLog log(
            OracleRedoLogKind kind, Path path, long sequence) {
        String status = "CURRENT";
        if (kind == OracleRedoLogKind.ARCHIVED) {
            status = "A";
        }
        return new OracleRedoLog(
                kind, 2, Seq.of(sequence), FIRST_SCN, NEXT_SCN,
                status, path.toString(), path);
    }

    private static byte[] redoFile(
            long blockCount,
            Scn nextScn,
            long sequence,
            List<byte[]> dataBlocks,
            int physicalBlockCount) {
        byte[] file = new byte[physicalBlockCount * BLOCK_SIZE];
        byte[] header = RedoBinaryTestSupport.fileHeader(
                ByteOrder.LITTLE_ENDIAN, BLOCK_SIZE, 0x1300_0000L);
        System.arraycopy(header, 0, file, 0, header.length);
        int metadata = BLOCK_SIZE;
        RedoBinaryTestSupport.writeUnsignedInt(
                file, metadata + 8, sequence, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                file, metadata + 156, blockCount, ByteOrder.LITTLE_ENDIAN);
        writeScn(file, metadata + 192, nextScn);
        rewriteChecksum(file, metadata);
        for (int index = 0; index < dataBlocks.size(); index++) {
            System.arraycopy(dataBlocks.get(index), 0, file,
                    (index + 2) * BLOCK_SIZE, BLOCK_SIZE);
        }
        return file;
    }

    private static byte[] block(long blockNumber, long sequence) {
        byte[] block = new byte[BLOCK_SIZE];
        block[0] = 1;
        block[1] = 0x22;
        RedoBinaryTestSupport.writeUnsignedInt(
                block, 4, blockNumber, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                block, 8, sequence, ByteOrder.LITTLE_ENDIAN);
        block[32] = (byte) blockNumber;
        rewriteChecksum(block, 0);
        return block;
    }

    private static void writeScn(byte[] bytes, int offset, Scn scn) {
        if (scn.isNone()) {
            Arrays.fill(bytes, offset, offset + 6, (byte) 0xFF);
            return;
        }
        RedoBinaryTestSupport.writeScn(
                bytes, offset, scn.rawValue(), ByteOrder.LITTLE_ENDIAN);
    }

    private static void rewriteChecksum(byte[] bytes, int offset) {
        RedoBinaryTestSupport.writeUnsignedShort(
                bytes, offset + 14, 0, ByteOrder.LITTLE_ENDIAN);
        RedoBlockHeaderParser parser = new RedoBlockHeaderParser(
                ByteOrder.LITTLE_ENDIAN, BLOCK_SIZE);
        int checksum = parser.calculateChecksum(bytes, offset);
        RedoBinaryTestSupport.writeUnsignedShort(
                bytes, offset + 14, checksum, ByteOrder.LITTLE_ENDIAN);
        checksum = parser.calculateChecksum(bytes, offset);
        RedoBinaryTestSupport.writeUnsignedShort(
                bytes, offset + 14, checksum, ByteOrder.LITTLE_ENDIAN);
    }
}
