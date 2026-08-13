/*
 * Java translation derived from OpenLogReplicator transaction redo/undo pairing.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import java.util.Objects;

public record RedoRecordPair(RedoLogRecord undo, RedoLogRecord redo) {
    public RedoRecordPair {
        Objects.requireNonNull(undo, "undo");
        Objects.requireNonNull(redo, "redo");
    }
}
