/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OracleRedoCatalogPollerTest {
    @TempDir
    Path redoDirectory;

    @Test
    void pollsUntilExpectedSequenceAppears() throws Exception {
        OracleRedoLog previous = readableLog(10, "redo10.log");
        OracleRedoLog expected = readableLog(11, "archive11.arc");
        Deque<OracleRedoCatalog> catalogs = new ArrayDeque<>();
        catalogs.add(catalog(List.of(), List.of(previous)));
        catalogs.add(catalog(List.of(expected), List.of(previous)));
        long[] now = {0};
        OracleRedoCatalogPoller poller = poller(
                () -> catalogs.removeFirst(), 5, 20, now);

        OracleRedoLog selected = poller.awaitNext(1, Seq.of(11));

        assertEquals(expected, selected);
        assertEquals(5, now[0]);
    }

    @Test
    void stopsAfterConfiguredMissingFileTimeout() throws Exception {
        OracleRedoLog previous = readableLog(10, "redo10.log");
        OracleRedoCatalog catalog = catalog(List.of(), List.of(previous));
        long[] now = {0};
        OracleRedoCatalogPoller poller = poller(
                () -> catalog, 4, 10, now);

        RedoRuntimeException error = assertThrows(
                RedoRuntimeException.class,
                () -> poller.awaitNext(1, Seq.of(11)));

        assertEquals(10042, error.getErrorCode());
        assertEquals(10, now[0]);
    }

    @Test
    void doesNotPollPastProvenSequenceGap() throws Exception {
        OracleRedoLog later = readableLog(12, "redo12.log");
        OracleRedoCatalog catalog = catalog(List.of(), List.of(later));
        long[] now = {0};
        OracleRedoCatalogPoller poller = poller(
                () -> catalog, 5, 20, now);

        RedoRuntimeException error = assertThrows(
                RedoRuntimeException.class,
                () -> poller.awaitNext(1, Seq.of(11)));

        assertEquals(10039, error.getErrorCode());
        assertEquals(0, now[0]);
    }

    @Test
    void stopsIfDatabaseIdentityChangesWhilePolling() throws Exception {
        OracleRedoLog previous = readableLog(10, "redo10.log");
        OracleRedoLog expected = readableLog(11, "archive11.arc");
        Deque<OracleRedoCatalog> catalogs = new ArrayDeque<>();
        catalogs.add(catalog(3, List.of(), List.of(previous)));
        catalogs.add(catalog(4, List.of(expected), List.of(previous)));
        long[] now = {0};
        OracleRedoCatalogPoller poller = poller(
                () -> catalogs.removeFirst(), 5, 20, now);

        RedoRuntimeException error = assertThrows(
                RedoRuntimeException.class,
                () -> poller.awaitNext(1, Seq.of(11)));

        assertEquals(10043, error.getErrorCode());
    }

    private OracleRedoLog readableLog(long sequence, String name)
            throws Exception {
        Path path = redoDirectory.resolve(name);
        Files.write(path, new byte[]{1});
        OracleRedoLogKind kind = OracleRedoLogKind.ONLINE;
        if (name.endsWith(".arc")) {
            kind = OracleRedoLogKind.ARCHIVED;
        }
        return new OracleRedoLog(
                kind,
                1, Seq.of(sequence), Scn.of(sequence * 100),
                Scn.of((sequence + 1) * 100), "CURRENT",
                path.toString(), path);
    }

    private static OracleRedoCatalogPoller poller(
            Callable<OracleRedoCatalog> loader,
            long interval,
            long timeout,
            long[] now) {
        return new OracleRedoCatalogPoller(
                loader, new RedoLogPlanner(), interval, timeout,
                () -> now[0], seconds -> now[0] += seconds);
    }

    private static OracleRedoCatalog catalog(
            List<OracleRedoLog> archived, List<OracleRedoLog> online) {
        return catalog(3, archived, online);
    }

    private static OracleRedoCatalog catalog(
            long resetlogsId,
            List<OracleRedoLog> archived,
            List<OracleRedoLog> online) {
        OracleDatabaseContext context = new OracleDatabaseContext(
                new DatabaseIdentity(1, 2, resetlogsId), Scn.of(10_000),
                "FREE", "FREEPDB1", "19.25.0.0.0", true,
                "ARCHIVELOG", true, true);
        return new OracleRedoCatalog(context, archived, online);
    }
}
