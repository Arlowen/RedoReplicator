/*
 * Java translation derived from OpenLogReplicator src/common/table/SysCCol.h.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.common.RowId;

import java.util.Objects;

public record SysCCol(RowId rowId, long constraintId, int internalColumn,
                      long objectId, IntX spare1) implements SystemDictionaryRow {
    public SysCCol {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(spare1, "spare1");
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.CONSTRAINT_COLUMN;
    }

    @Override
    public long dependentObjectId() {
        return objectId;
    }
}
