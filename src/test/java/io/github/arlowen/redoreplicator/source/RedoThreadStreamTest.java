/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadStatus;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.runtime.RedoThreadBatch;
import io.github.arlowen.redoreplicator.runtime.RedoThreadStream;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoThreadStreamTest {
    private static final int BLOCK_SIZE = 512;
    private static final int THREAD = 2;
    private static final Scn FIRST_SCN =
            Scn.of(0x0000_1234_5678_9ABCL);
    private static final Scn NEXT_SCN =
            Scn.of(0x0000_1234_5678_ABCDL);
    private static final DatabaseIdentity DATABASE_IDENTITY =
            new DatabaseIdentity(0xF000_0001L, 1, 5);

    @TempDir
    Path redoDirectory;

    @Test
    void preservesTransactionsWhileSwitchingArchiveSequence()
            throws Exception {
        OracleRedoLog first = archive(
                77, "archive77.arc",
                RedoBinaryTestSupport.transactionLwn(
                        FIRST_SCN, 1, 4, 5, 0x0502, 0x1801));
        Scn commitScn = Scn.of(FIRST_SCN.rawValue() + 100);
        OracleRedoLog second = archive(
                78, "archive78.arc",
                RedoBinaryTestSupport.transactionLwn(
                        commitScn, 1, 4, 5, 0x0504));
        OracleRedoCatalogPoller poller = poller(
                List.of(first, second), List.of());

        try (RedoTransactionBuffer transactionBuffer =
                     new RedoTransactionBuffer();
             RedoThreadStream stream = new RedoThreadStream(
                     poller, first, DATABASE_IDENTITY, FIRST_SCN,
                     FileOffset.zero(), transactionBuffer, 8, true)) {
            RedoThreadBatch begin = stream.read();
            assertEquals(RedoReadStatus.DATA, begin.status());
            assertEquals(1, begin.parsedLwns().size());
            assertTrue(begin.parsedLwns().get(0)
                    .committedTransactions().isEmpty());
            assertTrue(begin.parsedLwns().get(0)
                    .lowWatermarkPosition().isPresent());

            assertEquals(RedoReadStatus.FINISHED, stream.read().status());
            assertEquals(Seq.of(78), stream.expectedSequence());
            assertTrue(stream.currentLog().isEmpty());

            RedoThreadBatch commit = stream.read();
            assertEquals(RedoReadStatus.DATA, commit.status());
            assertEquals(second, commit.redoLog());
            assertEquals(1, commit.parsedLwns().size());
            assertEquals(1, commit.parsedLwns().get(0)
                    .committedTransactions().size());
            assertEquals(commitScn, commit.parsedLwns().get(0)
                    .committedTransactions().get(0)
                    .commitPosition().scn());
            assertTrue(commit.parsedLwns().get(0)
                    .lowWatermarkPosition().isEmpty());

            assertEquals(RedoReadStatus.FINISHED, stream.read().status());
            assertEquals(Seq.of(79), stream.expectedSequence());
        }
    }

    @Test
    void waitsForOnlineGrowthThenFinishesSwitchedLog()
            throws Exception {
        long sequence = 90;
        Path path = redoDirectory.resolve("redo90.log");
        Files.write(path, redoFile(
                sequence, 0, Scn.none(), List.of(), 2));
        OracleRedoLog online = new OracleRedoLog(
                OracleRedoLogKind.ONLINE, THREAD, Seq.of(sequence),
                FIRST_SCN, Scn.none(), "CURRENT",
                path.toString(), path);
        OracleRedoCatalogPoller poller = poller(
                List.of(), List.of(online));

        try (RedoTransactionBuffer transactionBuffer =
                     new RedoTransactionBuffer();
             RedoThreadStream stream = new RedoThreadStream(
                     poller, online, DATABASE_IDENTITY, FIRST_SCN,
                     FileOffset.zero(), transactionBuffer, 8, true)) {
            RedoThreadBatch waiting = stream.read();
            assertEquals(RedoReadStatus.WAITING, waiting.status());
            assertEquals(FileOffset.fromBlock(2, BLOCK_SIZE),
                    waiting.nextOffset());

            Scn commitScn = Scn.of(FIRST_SCN.rawValue() + 200);
            byte[] transaction = RedoBinaryTestSupport.transactionLwn(
                    commitScn, 1, 4, 6,
                    0x0502, 0x1801, 0x0504);
            Files.write(path, redoFile(
                    sequence, 3, NEXT_SCN,
                    List.of(redoBlock(2, sequence, transaction)), 3));

            RedoThreadBatch data = stream.read();
            assertEquals(RedoReadStatus.DATA, data.status());
            assertEquals(1, data.parsedLwns().size());
            assertEquals(1, data.parsedLwns().get(0)
                    .committedTransactions().size());
            assertEquals(commitScn, data.parsedLwns().get(0)
                    .position().scn());

            assertEquals(RedoReadStatus.FINISHED, stream.read().status());
            assertEquals(Seq.of(91), stream.expectedSequence());
        }
    }

    private OracleRedoLog archive(
            long sequence, String name, byte[] payload) throws Exception {
        Path path = redoDirectory.resolve(name);
        Files.write(path, redoFile(
                sequence, 3, NEXT_SCN,
                List.of(redoBlock(2, sequence, payload)), 3));
        return new OracleRedoLog(
                OracleRedoLogKind.ARCHIVED, THREAD, Seq.of(sequence),
                FIRST_SCN, NEXT_SCN, "A", path.toString(), path);
    }

    private static byte[] redoFile(
            long sequence,
            long blockCount,
            Scn nextScn,
            List<byte[]> blocks,
            int physicalBlockCount) {
        return RedoBinaryTestSupport.redoFile(
                ByteOrder.LITTLE_ENDIAN, BLOCK_SIZE, 0x1300_0000L,
                blockCount, nextScn, sequence, blocks,
                physicalBlockCount);
    }

    private static byte[] redoBlock(
            long blockNumber, long sequence, byte[] payload) {
        return RedoBinaryTestSupport.redoBlock(
                ByteOrder.LITTLE_ENDIAN, BLOCK_SIZE,
                blockNumber, sequence, payload);
    }

    private static OracleRedoCatalogPoller poller(
            List<OracleRedoLog> archived,
            List<OracleRedoLog> online) {
        OracleDatabaseContext context = new OracleDatabaseContext(
                DATABASE_IDENTITY, Scn.of(Long.MAX_VALUE),
                "FREE", "FREEPDB1", "19.25.0.0.0", true,
                "ARCHIVELOG", true, true);
        OracleRedoCatalog catalog = new OracleRedoCatalog(
                context, archived, online);
        long[] now = {0};
        return new OracleRedoCatalogPoller(
                () -> catalog, new RedoLogPlanner(), 1, 5,
                () -> now[0], seconds -> now[0] += seconds);
    }
}
