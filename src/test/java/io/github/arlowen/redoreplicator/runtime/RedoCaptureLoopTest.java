/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.parser.ParsedLwn;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadStatus;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.source.OracleRedoLogKind;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedoCaptureLoopTest {

    @Test
    void processesCompleteLwnsAndStopsBeforeTheNextOne() throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        List<Long> processed = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        RedoThreadBatch batch = batch(
                RedoReadStatus.DATA, lwn(100), lwn(200));
        RedoCaptureLoop loop = new RedoCaptureLoop(
                () -> batch,
                lwn -> {
                    processed.add(lwn.position().scn().rawValue());
                    stop.set(true);
                    return state(lwn.position());
                },
                (redoLog, lwn, state) -> statuses.add(
                        redoLog.oraclePath() + ":"
                                + state.durablePosition().scn()),
                stop::get,
                10,
                ignored -> {
                });

        loop.run();

        assertEquals(List.of(100L), processed);
        assertEquals(List.of("/redo01.log:100"), statuses);
    }

    @Test
    void waitsWithoutAdvancingWhenOnlineRedoHasNoCompleteLwn() throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger reads = new AtomicInteger();
        List<Long> waits = new ArrayList<>();
        RedoCaptureLoop loop = new RedoCaptureLoop(
                () -> {
                    reads.incrementAndGet();
                    return batch(RedoReadStatus.WAITING);
                },
                lwn -> {
                    return state(lwn.position());
                },
                (redoLog, lwn, state) -> {
                },
                stop::get,
                25,
                millis -> {
                    waits.add(millis);
                    stop.set(true);
                });

        loop.run();

        assertEquals(1, reads.get());
        assertEquals(List.of(25L), waits);
    }

    private static RedoThreadBatch batch(
            RedoReadStatus status, ParsedLwn... lwns) {
        return new RedoThreadBatch(
                new OracleRedoLog(
                        OracleRedoLogKind.ONLINE, 1, Seq.of(7),
                        Scn.of(1), Scn.of(1000), "CURRENT",
                        "/redo01.log", Path.of("/redo01.log")),
                status, FileOffset.of(4096), List.of(lwns));
    }

    private static ParsedLwn lwn(long scn) {
        return new ParsedLwn(
                new RedoPosition(
                        Scn.of(scn), 1, Seq.of(7), FileOffset.of(2048)),
                RedoTime.zero(), List.of(), Optional.empty());
    }

    private static RuntimeState state(RedoPosition position) {
        return new RuntimeState(
                1, 2, 3, position, Optional.empty(),
                1, 0, "fingerprint",
                java.time.OffsetDateTime.parse("2026-08-13T00:00:00Z"));
    }
}
