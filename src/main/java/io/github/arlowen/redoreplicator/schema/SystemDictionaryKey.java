/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.RowId;

import java.util.Objects;

record SystemDictionaryKey(SystemDictionaryTable table, RowId rowId) {
    public SystemDictionaryKey {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(rowId, "rowId");
    }
}
