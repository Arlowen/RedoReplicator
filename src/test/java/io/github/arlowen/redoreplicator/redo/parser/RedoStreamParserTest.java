/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadBatch;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadStatus;
import io.github.arlowen.redoreplicator.redo.reader.RedoReader;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.source.OracleRedoLogKind;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoStreamParserTest {
    private static final int BLOCK_SIZE = 512;
    private static final long SEQUENCE = 77;
    private static final Scn FIRST_SCN =
            Scn.of(0x0000_1234_5678_9ABCL);
    private static final Scn NEXT_SCN =
            Scn.of(0x0000_1234_5678_ABCDL);
    private static final DatabaseIdentity DATABASE_IDENTITY =
            new DatabaseIdentity(0xF000_0001L, 1, 5);

    @TempDir
    Path redoDirectory;

    @Test
    void carriesIncompleteLwnAcrossReaderBatches() throws Exception {
        byte[] largeBeginField = Arrays.copyOf(beginField(2, 3), 520);
        byte[] lwn = lwnRecord(
                FIRST_SCN, 2, vector(0x0502, largeBeginField));
        byte[] block2 = redoBlock(
                2, Arrays.copyOfRange(lwn, 0, 496));
        byte[] block3 = redoBlock(
                3, Arrays.copyOfRange(lwn, 496, lwn.length));
        Path path = writeArchive(
                "spanning.arc", List.of(block2, block3));

        try (RedoTransactionBuffer transactionBuffer =
                     new RedoTransactionBuffer();
             RedoReader reader = RedoReader.open(
                     log(path), DATABASE_IDENTITY,
                     FileOffset.zero(), true).orElseThrow()) {
            RedoStreamParser parser = new RedoStreamParser(
                    reader.header(), FIRST_SCN, FileOffset.zero(),
                    transactionBuffer);

            assertTrue(parser.accept(reader.read(1)).isEmpty());
            assertTrue(parser.parsedPosition().isEmpty());
            assertEquals(0, transactionBuffer.openTransactionCount());

            List<ParsedLwn> parsed = parser.accept(reader.read(1));
            assertEquals(1, parsed.size());
            assertEquals(FileOffset.fromBlock(4, BLOCK_SIZE),
                    parser.parsedPosition().orElseThrow().offset());
            assertEquals(1, transactionBuffer.openTransactionCount());
            assertEquals(FileOffset.fromBlock(2, BLOCK_SIZE),
                    parsed.get(0).lowWatermarkPosition()
                            .orElseThrow().offset());

            parser.accept(reader.read(1));
            assertTrue(parser.isFinished());
        }
    }

    @Test
    void processesPreStartTransactionsButOnlyReturnsLaterCommits()
            throws Exception {
        Scn firstCommitScn = FIRST_SCN;
        Scn secondCommitScn = Scn.of(FIRST_SCN.rawValue() + 100);
        byte[] first = redoBlock(
                2, transactionLwn(firstCommitScn, 2, 3));
        byte[] second = redoBlock(
                3, transactionLwn(secondCommitScn, 4, 5));
        Path path = writeArchive(
                "two-transactions.arc", List.of(first, second));
        Scn captureStartScn = Scn.of(FIRST_SCN.rawValue() + 50);

        try (RedoTransactionBuffer transactionBuffer =
                     new RedoTransactionBuffer();
             RedoReader reader = RedoReader.open(
                     log(path), DATABASE_IDENTITY,
                     FileOffset.zero(), true).orElseThrow()) {
            RedoStreamParser parser = new RedoStreamParser(
                    reader.header(), captureStartScn, FileOffset.zero(),
                    transactionBuffer);

            RedoReadBatch readBatch = reader.read(8);
            AssembledLwn firstLwn = new RedoLwnAssembler(
                    ByteOrder.LITTLE_ENDIAN, BLOCK_SIZE)
                    .tryAssemble(readBatch.blocks(), 2,
                            FIRST_SCN, NEXT_SCN).orElseThrow();
            List<RedoLogRecord> firstVectors = new RedoVectorParser(
                    ByteOrder.LITTLE_ENDIAN, 0x1300_0000L, BLOCK_SIZE)
                    .parseAll(firstLwn.records().get(0),
                            Seq.of(SEQUENCE), firstLwn.timestamp(), 2);
            assertEquals(List.of(0x0502, 0x1801, 0x0504),
                    firstVectors.stream()
                    .map(vector -> vector.opCode).toList());

            List<ParsedLwn> parsed = parser.accept(
                    readBatch);

            assertEquals(2, parsed.size());
            assertTrue(parsed.get(0).committedTransactions().isEmpty());
            assertTrue(parsed.get(0).lowWatermarkPosition().isEmpty());
            List<CommittedRedoTransaction> committed =
                    parsed.get(1).committedTransactions();
            assertEquals(1, committed.size());
            assertEquals(secondCommitScn,
                    committed.get(0).commitPosition().scn());
            assertEquals("0x0001.004.00000005",
                    committed.get(0).xid().toString());
            assertEquals(0, transactionBuffer.openTransactionCount());
            assertEquals(secondCommitScn,
                    parser.parsedPosition().orElseThrow().scn());
            assertEquals(FileOffset.fromBlock(4, BLOCK_SIZE),
                    parser.parsedPosition().orElseThrow().offset());
            assertTrue(parsed.get(1).lowWatermarkPosition().isEmpty());

            assertTrue(parser.accept(reader.read(8)).isEmpty());
            assertTrue(parser.isFinished());
        }
    }

    @Test
    void rejectsDiscontinuousBatchAndIncompleteLwnAtFileEnd()
            throws Exception {
        byte[] largeBeginField = Arrays.copyOf(beginField(2, 3), 520);
        byte[] lwn = lwnRecord(
                FIRST_SCN, 2, vector(0x0502, largeBeginField));
        Path path = writeArchive("incomplete.arc", List.of(
                redoBlock(2, Arrays.copyOfRange(lwn, 0, 496)),
                redoBlock(3, Arrays.copyOfRange(
                        lwn, 496, lwn.length))));

        try (RedoTransactionBuffer transactionBuffer =
                     new RedoTransactionBuffer();
             RedoReader reader = RedoReader.open(
                     log(path), DATABASE_IDENTITY,
                     FileOffset.zero(), true).orElseThrow()) {
            RedoStreamParser parser = new RedoStreamParser(
                    reader.header(), FIRST_SCN, FileOffset.zero(),
                    transactionBuffer);
            RedoReadBatch first = reader.read(1);

            RedoLogException discontinuous = assertThrows(
                    RedoLogException.class,
                    () -> parser.accept(new RedoReadBatch(
                            RedoReadStatus.DATA,
                            FileOffset.fromBlock(3, BLOCK_SIZE),
                            first.blocks())));
            assertEquals(50046, discontinuous.getErrorCode());

            assertTrue(parser.accept(first).isEmpty());
            RedoLogException incomplete = assertThrows(
                    RedoLogException.class,
                    () -> parser.accept(new RedoReadBatch(
                            RedoReadStatus.FINISHED,
                            FileOffset.fromBlock(3, BLOCK_SIZE),
                            List.of())));
            assertEquals(50046, incomplete.getErrorCode());
            assertFalse(parser.isFinished());
        }
    }

    private Path writeArchive(String name, List<byte[]> blocks)
            throws Exception {
        Path path = redoDirectory.resolve(name);
        Files.write(path, RedoBinaryTestSupport.redoFile(
                ByteOrder.LITTLE_ENDIAN, BLOCK_SIZE, 0x1300_0000L,
                blocks.size() + 2L, NEXT_SCN, SEQUENCE,
                blocks, blocks.size() + 2));
        return path;
    }

    private static OracleRedoLog log(Path path) {
        return new OracleRedoLog(
                OracleRedoLogKind.ARCHIVED, 2, Seq.of(SEQUENCE),
                FIRST_SCN, NEXT_SCN, "A", path.toString(), path);
    }

    private static byte[] redoBlock(long blockNumber, byte[] payload) {
        return RedoBinaryTestSupport.redoBlock(
                ByteOrder.LITTLE_ENDIAN, BLOCK_SIZE,
                blockNumber, SEQUENCE, payload);
    }

    private static byte[] transactionLwn(
            Scn scn, int slot, long xidSequence) {
        return RedoBinaryTestSupport.transactionLwn(
                scn, 1, slot, xidSequence,
                0x0502, 0x1801, 0x0504);
    }

    private static byte[] lwnRecord(
            Scn scn, long blockCount, byte[]... vectors) {
        int size = 68;
        for (byte[] vector : vectors) {
            size += vector.length;
        }
        byte[] record = new byte[size];
        RedoBinaryTestSupport.writeUnsignedInt(
                record, 0, size, ByteOrder.LITTLE_ENDIAN);
        record[4] = 0x05;
        RedoBinaryTestSupport.writeUnsignedShort(
                record, 6, (int) (scn.rawValue() >>> 32),
                ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                record, 8, scn.rawValue(), ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                record, 12, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                record, 24, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                record, 26, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                record, 28, blockCount, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                record, 32, size, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeScn(
                record, 40, scn.rawValue(), ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                record, 64, 989_619_936L, ByteOrder.LITTLE_ENDIAN);
        int offset = 68;
        for (byte[] vector : vectors) {
            System.arraycopy(vector, 0, record, offset, vector.length);
            offset += vector.length;
        }
        return record;
    }

    private static byte[] vector(int opCode, byte[] field) {
        int fieldListLength = 4;
        int fieldPosition = 32 + ((fieldListLength + 2) & 0xFFFC);
        byte[] vector = new byte[fieldPosition + align4(field.length)];
        vector[0] = (byte) (opCode >>> 8);
        vector[1] = (byte) opCode;
        RedoBinaryTestSupport.writeUnsignedShort(
                vector, 2, 17, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                vector, 32, fieldListLength, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                vector, 34, field.length, ByteOrder.LITTLE_ENDIAN);
        System.arraycopy(field, 0, vector, fieldPosition, field.length);
        return vector;
    }

    private static byte[] beginField(int slot, long xidSequence) {
        byte[] field = new byte[32];
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 0, slot, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, xidSequence, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static int align4(int size) {
        return (size + 3) & 0xFFFC;
    }
}
