/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.Xid;

import java.util.Objects;

public record SystemDictionaryRedoChange(Xid xid,
                                         SystemDictionaryChange change) {
    public SystemDictionaryRedoChange {
        Objects.requireNonNull(xid, "xid");
        Objects.requireNonNull(change, "change");
        if (xid.isEmpty()) {
            throw new IllegalArgumentException(
                    "A system dictionary redo change requires an XID");
        }
    }
}
