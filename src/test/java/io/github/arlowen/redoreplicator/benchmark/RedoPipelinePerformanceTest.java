/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.benchmark;

import io.github.arlowen.redoreplicator.output.BuilderJson;
import io.github.arlowen.redoreplicator.output.JsonlFileWriter;
import io.github.arlowen.redoreplicator.output.OracleJsonValueDecoder;
import io.github.arlowen.redoreplicator.output.RedoJsonChange;
import io.github.arlowen.redoreplicator.output.RedoJsonDmlChange;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.DecodedRedoRow;
import io.github.arlowen.redoreplicator.redo.transaction.RedoColumnValue;
import io.github.arlowen.redoreplicator.redo.transaction.RedoRowOperation;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.StateDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(
        named = "redoreplicator.test.performance", matches = "true")
class RedoPipelinePerformanceTest {
    private static final int ROWS_PER_COMMIT = 100;
    private static final long MINIMUM_ROWS_PER_SECOND = 5_000;
    private static final long MAXIMUM_P95_FSYNC_MILLIS = 5_000;

    @TempDir
    Path temporaryDirectory;

    @Test
    void sustainsRequiredJsonlAndH2Throughput() throws Exception {
        long requestedRows = Long.getLong(
                "redoreplicator.performance.rows", 100_000L);
        int durationMinutes = Integer.getInteger(
                "redoreplicator.performance.minutes", 0);
        long deadline = Long.MAX_VALUE;
        if (durationMinutes > 0) {
            deadline = System.nanoTime()
                    + durationMinutes * 60L * 1_000_000_000L;
        }

        BuilderJson builder = new BuilderJson(
                new OracleJsonValueDecoder(
                        StandardCharsets.UTF_8, ZoneOffset.UTC),
                "FREEPDB1", 0);
        List<RedoJsonChange> changes = changes();
        List<Long> fsyncMillis = new ArrayList<>();
        long rows = 0;
        long transactionNumber = 0;
        long started = System.nanoTime();
        try (StateDatabase database = StateDatabase.open(
                temporaryDirectory.resolve("state"));
             JsonlFileWriter writer = JsonlFileWriter.open(
                     temporaryDirectory.resolve("output"),
                     256L * 1024 * 1024, Optional.empty())) {
            while (rows < requestedRows || System.nanoTime() < deadline) {
                transactionNumber++;
                CommittedRedoTransaction transaction = transaction(
                        transactionNumber);
                long commitStarted = System.nanoTime();
                var messages = builder.buildTransaction(
                        transaction, changes);
                var jsonlPosition = writer.writeAndSync(messages);
                fsyncMillis.add(
                        (System.nanoTime() - commitStarted) / 1_000_000);
                RuntimeState state = new RuntimeState(
                        1, 2, 3, transaction.commitPosition(),
                        Optional.empty(), jsonlPosition.fileNumber(),
                        jsonlPosition.fsyncOffset(), "benchmark",
                        OffsetDateTime.now());
                database.store().commitLwn(state, List.of());
                rows += ROWS_PER_COMMIT;
                if (durationMinutes == 0 && rows >= requestedRows) {
                    break;
                }
            }
        }
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        double rowsPerSecond = rows / seconds;
        Collections.sort(fsyncMillis);
        long p95 = fsyncMillis.get(
                (int) Math.ceil(fsyncMillis.size() * 0.95) - 1);

        System.out.printf(
                "Redo pipeline benchmark: rows=%d seconds=%.3f rows/s=%.0f p95-fsync-ms=%d%n",
                rows, seconds, rowsPerSecond, p95);
        assertTrue(rowsPerSecond >= MINIMUM_ROWS_PER_SECOND,
                "Throughput was " + rowsPerSecond + " rows/s");
        assertTrue(p95 <= MAXIMUM_P95_FSYNC_MILLIS,
                "P95 commit-to-fsync was " + p95 + " ms");
    }

    private static List<RedoJsonChange> changes() {
        byte[] payload = "X".repeat(1_024)
                .getBytes(StandardCharsets.UTF_8);
        RedoColumnValue value = RedoColumnValue.of(
                OracleColumnType.VARCHAR, 873, payload);
        Map<String, RedoColumnValue> image = Map.of("PAYLOAD", value);
        List<TableSchema> tables = new ArrayList<>();
        for (int table = 0; table < 10; table++) {
            tables.add(table(table));
        }
        List<RedoJsonChange> changes = new ArrayList<>(ROWS_PER_COMMIT);
        for (int row = 0; row < ROWS_PER_COMMIT; row++) {
            RedoRowOperation operation = RedoRowOperation.INSERT;
            Map<String, RedoColumnValue> before = Map.of();
            Map<String, RedoColumnValue> after = image;
            if (row >= 60 && row < 90) {
                operation = RedoRowOperation.UPDATE;
                before = image;
            } else if (row >= 90) {
                operation = RedoRowOperation.DELETE;
                before = image;
                after = Map.of();
            }
            changes.add(new RedoJsonDmlChange(new DecodedRedoRow(
                    operation, tables.get(row % tables.size()),
                    RowId.of(100 + row % 10, 200, row),
                    FileOffset.of(row), before, after)));
        }
        return List.copyOf(changes);
    }

    private static TableSchema table(int index) {
        return new TableSchema(
                "FREEPDB1", "APP", "BENCHMARK_" + index,
                100 + index, 200 + index, 10, 0, 0,
                List.of(new ColumnSchema(
                        1, -1, 1, 1, "PAYLOAD", OracleColumnType.VARCHAR,
                        1_024, -1, -1, 1, 873,
                        false, false, false, false, false,
                        false, false, false, false)),
                List.of(), List.of());
    }

    private static CommittedRedoTransaction transaction(long number) {
        Scn scn = Scn.of(number + 1);
        RedoPosition position = new RedoPosition(
                scn, 1, Seq.of(1), FileOffset.of(number * 512));
        return new CommittedRedoTransaction(
                Xid.of(1, (int) (number % 65_535), number),
                3, 1, position, RedoTime.zero(), position, RedoTime.of(1),
                Map.of(), List.of());
    }
}
