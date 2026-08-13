/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.parser.ParsedLwn;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.source.OracleRedoLogKind;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimeStatusWriterTest {
    private static final long REDO_TIME =
            (((((2018 - 1988) * 12L + 9) * 31 + 14) * 24 + 22)
                    * 60 + 25) * 60 + 36;
    private static final Clock CLOCK = Clock.fixed(
            Instant.ofEpochSecond(1_539_613_546L), ZoneOffset.UTC);

    @TempDir
    private Path temporaryDirectory;

    @Test
    void atomicallyPublishesTheLastCommittedLwnStatus() throws Exception {
        Path statusFile = temporaryDirectory.resolve("data/status.json");
        RuntimeStatusWriter writer = new RuntimeStatusWriter(
                statusFile, CLOCK, 8 * 60 * 60);
        RedoPosition durable = position(200, 4096);
        RuntimeState state = new RuntimeState(
                1, 2, 3, durable,
                Optional.of(position(100, 512)),
                7, 8192, "fingerprint",
                OffsetDateTime.now(CLOCK));
        ParsedLwn lwn = new ParsedLwn(
                durable, RedoTime.of(REDO_TIME),
                List.of(), state.lowWatermarkPosition());

        writer.write(redoLog(), lwn, state);

        JsonNode status = new ObjectMapper().readTree(statusFile.toFile());
        assertEquals("RUNNING", status.path("state").textValue());
        assertEquals("ARCHIVED", status.path("redoKind").textValue());
        assertEquals("/oracle/archive/redo_7.arc",
                status.path("redoFile").textValue());
        assertEquals(1, status.path("redoThread").intValue());
        assertEquals(7, status.path("redoSequence").longValue());
        assertEquals("200", status.path("safeScn").textValue());
        assertEquals("4096", status.path("safeOffset").textValue());
        assertEquals("100", status.path("lowWatermarkScn").textValue());
        assertEquals(10, status.path("lagSeconds").longValue());
        assertEquals("2018-10-15T14:25:46Z",
                status.path("updatedAt").textValue());
        assertFalse(Files.exists(
                statusFile.resolveSibling("status.json.tmp")));
    }

    @Test
    void statusFailureDoesNotAffectTheCommittedCapturePath() throws Exception {
        Path invalidParent = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(invalidParent, "occupied");
        RuntimeStatusWriter writer = new RuntimeStatusWriter(
                invalidParent.resolve("status.json"), CLOCK, 0);
        RuntimeState state = new RuntimeState(
                1, 2, 3, position(200, 4096), Optional.empty(),
                1, 0, "fingerprint", OffsetDateTime.now(CLOCK));
        ParsedLwn lwn = new ParsedLwn(
                state.durablePosition(), RedoTime.of(REDO_TIME),
                List.of(), Optional.empty());

        assertDoesNotThrow(() -> writer.write(redoLog(), lwn, state));
    }

    private OracleRedoLog redoLog() {
        return new OracleRedoLog(
                OracleRedoLogKind.ARCHIVED, 1, Seq.of(7),
                Scn.of(1), Scn.of(300), "A",
                "/oracle/archive/redo_7.arc",
                temporaryDirectory.resolve("redo_7.arc"));
    }

    private static RedoPosition position(long scn, long offset) {
        return new RedoPosition(
                Scn.of(scn), 1, Seq.of(7), FileOffset.of(offset));
    }
}
