/*
 * Java translation derived from OpenLogReplicator src/common/table/SysCDef.h.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.RowId;

import java.util.Objects;

public record SysCDef(RowId rowId, long constraintId, long objectId,
                      int typeCode) implements SystemDictionaryRow {
    public static final int TYPE_PRIMARY_KEY = 2;

    public SysCDef {
        Objects.requireNonNull(rowId, "rowId");
    }

    public boolean primaryKey() {
        return typeCode == TYPE_PRIMARY_KEY;
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.CONSTRAINT;
    }

    @Override
    public long dependentObjectId() {
        return objectId;
    }
}
