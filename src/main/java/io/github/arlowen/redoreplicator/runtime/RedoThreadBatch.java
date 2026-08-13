/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.parser.ParsedLwn;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadStatus;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;

import java.util.List;
import java.util.Objects;

public record RedoThreadBatch(
        OracleRedoLog redoLog,
        RedoReadStatus status,
        FileOffset nextOffset,
        List<ParsedLwn> parsedLwns) {
    public RedoThreadBatch {
        Objects.requireNonNull(redoLog, "redoLog");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nextOffset, "nextOffset");
        parsedLwns = List.copyOf(parsedLwns);
    }
}
