/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.RowId;

import java.util.Map;
import java.util.Objects;

public record SystemDictionaryChange(SystemDictionaryOperation operation,
                                     SystemDictionaryTable table,
                                     RowId rowId,
                                     Map<String, SystemDictionaryValue> values) {
    public SystemDictionaryChange {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(rowId, "rowId");
        values = Map.copyOf(values);
        if (operation == SystemDictionaryOperation.DELETE && !values.isEmpty()) {
            throw new IllegalArgumentException("A dictionary delete cannot contain values");
        }
    }

    public static SystemDictionaryChange delete(SystemDictionaryTable table,
                                                RowId rowId) {
        return new SystemDictionaryChange(
                SystemDictionaryOperation.DELETE, table, rowId, Map.of());
    }
}
