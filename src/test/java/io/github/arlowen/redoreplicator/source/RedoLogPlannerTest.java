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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedoLogPlannerTest {
    @TempDir
    Path redoDirectory;

    @Test
    void selectsArchivePerThreadThenSwitchesToOnline() throws Exception {
        OracleRedoLog archiveThread1 = log(
                OracleRedoLogKind.ARCHIVED, 1, 10, 100, 200,
                readable("archive-t1-10.arc"));
        OracleRedoLog archiveThread2 = log(
                OracleRedoLogKind.ARCHIVED, 2, 20, 120, 210,
                readable("archive-t2-20.arc"));
        OracleRedoLog archiveNext = log(
                OracleRedoLogKind.ARCHIVED, 1, 11, 200, 300,
                readable("archive-t1-11.arc"));
        OracleRedoLog unavailableCopy = log(
                OracleRedoLogKind.ARCHIVED, 1, 11, 200, 300,
                redoDirectory.resolve("archive-copy-not-mounted.arc"));
        OracleRedoLog onlineThread1 = log(
                OracleRedoLogKind.ONLINE, 1, 12, 300, 1_000,
                readable("redo01.log"));
        OracleRedoLog onlineThread2 = log(
                OracleRedoLogKind.ONLINE, 2, 21, 210, 1_000,
                readable("redo02.log"));
        OracleRedoCatalog catalog = catalog(
                List.of(archiveThread1, archiveThread2, unavailableCopy,
                        archiveNext),
                List.of(onlineThread1, onlineThread2));
        RedoLogPlanner planner = new RedoLogPlanner();

        List<OracleRedoLog> start = planner.locateStart(
                catalog, Scn.of(150)).orElseThrow();

        assertEquals(List.of(archiveThread1, archiveThread2), start);
        assertEquals(archiveNext,
                planner.locateNext(catalog, 1, Seq.of(11)).orElseThrow());
        assertEquals(onlineThread1,
                planner.locateNext(catalog, 1, Seq.of(12)).orElseThrow());
    }

    @Test
    void waitsForCatalogRowOrMappedFileToBecomeReadable() throws Exception {
        OracleRedoLog unavailable = log(
                OracleRedoLogKind.ARCHIVED, 1, 11, 200, 300,
                redoDirectory.resolve("not-mounted.arc"));
        OracleRedoLog previous = log(
                OracleRedoLogKind.ONLINE, 1, 10, 100, 200,
                readable("redo01.log"));
        OracleRedoCatalog catalog = catalog(
                List.of(unavailable), List.of(previous));
        RedoLogPlanner planner = new RedoLogPlanner();

        assertFalse(planner.locateNext(
                catalog, 1, Seq.of(11)).isPresent());
        assertFalse(planner.locateNext(
                catalog, 1, Seq.of(12)).isPresent());
        assertFalse(planner.locateStart(
                catalog, Scn.of(250)).isPresent());
    }

    @Test
    void stopsOnSequenceGapOrLostStartHistory() throws Exception {
        OracleRedoLog later = log(
                OracleRedoLogKind.ONLINE, 1, 12, 200, 1_000,
                readable("redo01.log"));
        OracleRedoCatalog catalog = catalog(List.of(), List.of(later));
        RedoLogPlanner planner = new RedoLogPlanner();

        RedoRuntimeException sequenceGap = assertThrows(
                RedoRuntimeException.class,
                () -> planner.locateNext(catalog, 1, Seq.of(11)));
        assertEquals(10039, sequenceGap.getErrorCode());

        RedoRuntimeException historyGap = assertThrows(
                RedoRuntimeException.class,
                () -> planner.locateStart(catalog, Scn.of(100)));
        assertEquals(10039, historyGap.getErrorCode());
    }

    @Test
    void rejectsStartScnAheadOfDatabase() {
        OracleRedoCatalog catalog = new OracleRedoCatalog(
                context(500), List.of(), List.of());

        RedoRuntimeException error = assertThrows(
                RedoRuntimeException.class,
                () -> new RedoLogPlanner().locateStart(
                        catalog, Scn.of(501)));

        assertEquals(10040, error.getErrorCode());
    }

    private Path readable(String name) throws Exception {
        Path path = redoDirectory.resolve(name);
        Files.write(path, new byte[]{1});
        return path;
    }

    private static OracleRedoLog log(
            OracleRedoLogKind kind,
            int thread,
            long sequence,
            long firstScn,
            long nextScn,
            Path path) {
        String status = "CURRENT";
        if (kind == OracleRedoLogKind.ARCHIVED) {
            status = "A";
        }
        return new OracleRedoLog(
                kind, thread, Seq.of(sequence), Scn.of(firstScn),
                Scn.of(nextScn), status, path.toString(), path);
    }

    private static OracleRedoCatalog catalog(
            List<OracleRedoLog> archived, List<OracleRedoLog> online) {
        return new OracleRedoCatalog(context(900), archived, online);
    }

    private static OracleDatabaseContext context(long currentScn) {
        return new OracleDatabaseContext(
                new DatabaseIdentity(1, 2, 3), Scn.of(currentScn),
                "FREE", "FREEPDB1", "19.25.0.0.0", true,
                "ARCHIVELOG", true, true);
    }
}
