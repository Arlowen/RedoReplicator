/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arlowen.redoreplicator.redo.parser.ParsedLwn;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.Objects;

final class RuntimeStatusWriter {
    private static final Logger log = LoggerFactory.getLogger(
            RuntimeStatusWriter.class);

    private final Path statusFile;
    private final Path temporaryFile;
    private final Clock clock;
    private final long hostTimezoneSeconds;
    private final ObjectMapper objectMapper;

    RuntimeStatusWriter(
            Path statusFile,
            Clock clock,
            long hostTimezoneSeconds) {
        this.statusFile = Objects.requireNonNull(
                statusFile, "statusFile").toAbsolutePath().normalize();
        temporaryFile = this.statusFile.resolveSibling(
                this.statusFile.getFileName() + ".tmp");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.hostTimezoneSeconds = hostTimezoneSeconds;
        objectMapper = new ObjectMapper();
    }

    void write(
            OracleRedoLog redoLog,
            ParsedLwn lwn,
            RuntimeState state) {
        Objects.requireNonNull(redoLog, "redoLog");
        Objects.requireNonNull(lwn, "lwn");
        Objects.requireNonNull(state, "state");
        String lowWatermarkScn = null;
        if (state.lowWatermarkPosition().isPresent()) {
            lowWatermarkScn = state.lowWatermarkPosition().orElseThrow()
                    .scn().toDecimalString();
        }
        long redoEpochSeconds = lwn.timestamp().toEpochSeconds(
                hostTimezoneSeconds);
        long lagSeconds = Math.max(
                0, clock.instant().getEpochSecond() - redoEpochSeconds);
        RuntimeStatus status = new RuntimeStatus(
                "RUNNING", ProcessHandle.current().pid(),
                redoLog.kind().name(), redoLog.oraclePath(),
                redoLog.localPath().toString(),
                state.durablePosition().thread(),
                state.durablePosition().sequence().value(),
                state.durablePosition().scn().toDecimalString(),
                state.durablePosition().offset().toString(),
                lowWatermarkScn,
                state.jsonlFileNumber(), state.jsonlFsyncOffset(),
                lagSeconds, state.updatedAt().toString());
        boolean temporaryMayExist = false;
        try {
            Files.createDirectories(statusFile.getParent());
            String json = objectMapper.writeValueAsString(status)
                    + System.lineSeparator();
            temporaryMayExist = true;
            Files.writeString(
                    temporaryFile, json, StandardCharsets.UTF_8);
            Files.move(temporaryFile, statusFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            String msg = "Failed to update non-authoritative runtime status";
            log.error(msg, e);
            if (temporaryMayExist) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupError) {
                    log.error("Failed to remove temporary runtime status",
                            cleanupError);
                }
            }
        }
    }
}
