/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.state.RuntimeState;

import java.util.Objects;
import java.util.Optional;

public record RedoCaptureStart(
        Scn captureStartScn,
        OracleRedoLog redoLog,
        FileOffset fileOffset,
        Optional<RuntimeState> recoveredState) {
    public RedoCaptureStart {
        Objects.requireNonNull(captureStartScn, "captureStartScn");
        Objects.requireNonNull(redoLog, "redoLog");
        Objects.requireNonNull(fileOffset, "fileOffset");
        Objects.requireNonNull(recoveredState, "recoveredState");
    }
}
