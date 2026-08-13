/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import java.util.Objects;

public record RuntimeStatus(
        String state,
        long processId,
        String redoKind,
        String redoFile,
        String localRedoFile,
        int redoThread,
        long redoSequence,
        String safeScn,
        String safeOffset,
        String lowWatermarkScn,
        long jsonlFileNumber,
        long jsonlFsyncOffset,
        long lagSeconds,
        String updatedAt) {
    public RuntimeStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(redoKind, "redoKind");
        Objects.requireNonNull(redoFile, "redoFile");
        Objects.requireNonNull(localRedoFile, "localRedoFile");
        Objects.requireNonNull(safeScn, "safeScn");
        Objects.requireNonNull(safeOffset, "safeOffset");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
