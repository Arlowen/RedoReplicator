/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;

import java.util.Objects;

public record RedoPosition(Scn scn, int thread, Seq sequence, FileOffset offset) {
    public RedoPosition {
        Objects.requireNonNull(scn, "scn");
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(offset, "offset");
    }
}
