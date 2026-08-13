/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.schema.TableSchema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DecodedRedoRow(
        RedoRowOperation operation,
        TableSchema table,
        RowId rowId,
        FileOffset fileOffset,
        Map<String, RedoColumnValue> before,
        Map<String, RedoColumnValue> after) {
    public DecodedRedoRow {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(fileOffset, "fileOffset");
        before = Collections.unmodifiableMap(
                new LinkedHashMap<>(before));
        after = Collections.unmodifiableMap(
                new LinkedHashMap<>(after));
    }
}
