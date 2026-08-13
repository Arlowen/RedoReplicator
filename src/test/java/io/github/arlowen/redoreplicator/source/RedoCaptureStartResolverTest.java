/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.runtime.RedoCaptureStart;
import io.github.arlowen.redoreplicator.runtime.RedoCaptureStartResolver;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.StateDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoCaptureStartResolverTest {
    private static final DatabaseIdentity DATABASE_IDENTITY =
            new DatabaseIdentity(1, 2, 3);

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesConfiguredScnOnlyForFirstStart() throws Exception {
        OracleRedoLog archive = log(
                OracleRedoLogKind.ARCHIVED, 1, 10,
                100, 200, "archive10.arc");
        OracleRedoLog online = log(
                OracleRedoLogKind.ONLINE, 1, 11,
                200, Long.MAX_VALUE, "redo11.log");

        try (StateDatabase state = StateDatabase.open(
                temporaryDirectory.resolve("state"))) {
            RedoCaptureStart start = new RedoCaptureStartResolver().resolve(
                    poller(List.of(archive), List.of(online)), state.store(),
                    context(DATABASE_IDENTITY), 150L);

            assertEquals(Scn.of(150), start.captureStartScn());
            assertEquals(archive, start.redoLog());
            assertEquals(FileOffset.zero(), start.fileOffset());
            assertTrue(start.recoveredState().isEmpty());
        }
    }

    @Test
    void defaultsFirstStartToOracleCurrentScn() throws Exception {
        OracleRedoLog online = log(
                OracleRedoLogKind.ONLINE, 1, 11,
                200, Long.MAX_VALUE, "redo11.log");

        try (StateDatabase state = StateDatabase.open(
                temporaryDirectory.resolve("state"))) {
            RedoCaptureStart start = new RedoCaptureStartResolver().resolve(
                    poller(List.of(), List.of(online)), state.store(),
                    context(DATABASE_IDENTITY), null);

            assertEquals(Scn.of(250), start.captureStartScn());
            assertEquals(online, start.redoLog());
        }
    }

    @Test
    void resumesFromLowWatermarkAndIgnoresConfiguredScn()
            throws Exception {
        OracleRedoLog replayLog = log(
                OracleRedoLogKind.ARCHIVED, 1, 9,
                50, 150, "archive9.arc");
        OracleRedoLog online = log(
                OracleRedoLogKind.ONLINE, 1, 11,
                200, Long.MAX_VALUE, "redo11.log");
        RedoPosition durable = position(190, 10, 1_536);
        RedoPosition lowWatermark = position(90, 9, 1_024);
        RuntimeState runtimeState = runtimeState(
                DATABASE_IDENTITY, durable, Optional.of(lowWatermark));

        try (StateDatabase state = StateDatabase.open(
                temporaryDirectory.resolve("state"))) {
            state.store().commitLwn(runtimeState, List.of());

            RedoCaptureStart start = new RedoCaptureStartResolver().resolve(
                    poller(List.of(replayLog), List.of(online)), state.store(),
                    context(DATABASE_IDENTITY), 240L);

            assertEquals(durable.scn(), start.captureStartScn());
            assertEquals(replayLog, start.redoLog());
            assertEquals(lowWatermark.offset(), start.fileOffset());
            assertEquals(Optional.of(runtimeState), start.recoveredState());
        }
    }

    @Test
    void resumesFromDurableOffsetWithoutOpenTransaction()
            throws Exception {
        OracleRedoLog replayLog = log(
                OracleRedoLogKind.ARCHIVED, 1, 10,
                100, 200, "archive10.arc");
        OracleRedoLog online = log(
                OracleRedoLogKind.ONLINE, 1, 11,
                200, Long.MAX_VALUE, "redo11.log");
        RedoPosition durable = position(190, 10, 1_536);
        RuntimeState runtimeState = runtimeState(
                DATABASE_IDENTITY, durable, Optional.empty());

        try (StateDatabase state = StateDatabase.open(
                temporaryDirectory.resolve("state"))) {
            state.store().commitLwn(runtimeState, List.of());

            RedoCaptureStart start = new RedoCaptureStartResolver().resolve(
                    poller(List.of(replayLog), List.of(online)), state.store(),
                    context(DATABASE_IDENTITY), 120L);

            assertEquals(replayLog, start.redoLog());
            assertEquals(durable.offset(), start.fileOffset());
        }
    }

    @Test
    void rejectsChangedDatabaseIdentityAndMultipleActiveThreads()
            throws Exception {
        OracleRedoLog first = log(
                OracleRedoLogKind.ONLINE, 1, 11,
                200, Long.MAX_VALUE, "redo11.log");
        OracleRedoLog second = log(
                OracleRedoLogKind.ONLINE, 2, 11,
                200, Long.MAX_VALUE, "redo12.log");

        try (StateDatabase state = StateDatabase.open(
                temporaryDirectory.resolve("state"))) {
            RuntimeState runtimeState = runtimeState(
                    DATABASE_IDENTITY, position(190, 10, 1_536),
                    Optional.empty());
            state.store().commitLwn(runtimeState, List.of());

            assertThrows(ConfigurationException.class,
                    () -> new RedoCaptureStartResolver().resolve(
                            poller(List.of(), List.of(first)), state.store(),
                            context(new DatabaseIdentity(1, 2, 4)), null));
        }

        try (StateDatabase state = StateDatabase.open(
                temporaryDirectory.resolve("empty-state"))) {
            ConfigurationException error = assertThrows(
                    ConfigurationException.class,
                    () -> new RedoCaptureStartResolver().resolve(
                            poller(List.of(), List.of(first, second)),
                            state.store(), context(DATABASE_IDENTITY), null));
            assertEquals(10045, error.getErrorCode());
        }
    }

    private OracleRedoLog log(
            OracleRedoLogKind kind,
            int thread,
            long sequence,
            long firstScn,
            long nextScn,
            String name) throws Exception {
        Path path = temporaryDirectory.resolve(name);
        Files.write(path, new byte[]{1});
        Scn next = Scn.of(nextScn);
        if (nextScn == Long.MAX_VALUE) {
            next = Scn.none();
        }
        String status = "A";
        if (kind == OracleRedoLogKind.ONLINE) {
            status = "CURRENT";
        }
        return new OracleRedoLog(
                kind, thread, Seq.of(sequence), Scn.of(firstScn), next,
                status, path.toString(), path);
    }

    private static OracleRedoCatalogPoller poller(
            List<OracleRedoLog> archived,
            List<OracleRedoLog> online) {
        OracleRedoCatalog catalog = new OracleRedoCatalog(
                context(DATABASE_IDENTITY), archived, online);
        long[] now = {0};
        return new OracleRedoCatalogPoller(
                () -> catalog, new RedoLogPlanner(), 1, 5,
                () -> now[0], seconds -> now[0] += seconds);
    }

    private static OracleDatabaseContext context(
            DatabaseIdentity identity) {
        return new OracleDatabaseContext(
                identity, Scn.of(250), "FREE", "FREEPDB1",
                "19.25.0.0.0", true, "ARCHIVELOG", true, true);
    }

    private static RedoPosition position(
            long scn, long sequence, long offset) {
        return new RedoPosition(
                Scn.of(scn), 1, Seq.of(sequence), FileOffset.of(offset));
    }

    private static RuntimeState runtimeState(
            DatabaseIdentity identity,
            RedoPosition durable,
            Optional<RedoPosition> lowWatermark) {
        return new RuntimeState(
                identity.databaseId(), identity.incarnation(),
                identity.resetlogsId(), durable, lowWatermark,
                1, 0,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                OffsetDateTime.of(
                        2026, 8, 13, 16, 0, 0, 0,
                        ZoneOffset.ofHours(8)));
    }
}
