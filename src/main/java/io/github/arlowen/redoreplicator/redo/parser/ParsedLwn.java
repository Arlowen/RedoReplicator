/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.state.RedoPosition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ParsedLwn(
        RedoPosition position,
        List<CommittedRedoTransaction> committedTransactions,
        Optional<RedoPosition> lowWatermarkPosition) {
    public ParsedLwn {
        Objects.requireNonNull(position, "position");
        committedTransactions = List.copyOf(committedTransactions);
        Objects.requireNonNull(lowWatermarkPosition, "lowWatermarkPosition");
    }
}
