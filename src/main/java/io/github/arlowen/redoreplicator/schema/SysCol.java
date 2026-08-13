/*
 * Java translation derived from OpenLogReplicator src/common/table/SysCol.h.
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

public record SysCol(RowId rowId, long objectId, int columnNumber,
                     int segmentColumn, int internalColumn, String name,
                     int typeCode, int length, int precision, int scale,
                     int charsetForm, long charsetId, int nullFlag,
                     IntX properties) implements SystemDictionaryRow {
    public static final int NAME_LENGTH = 128;

    public SysCol {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(properties, "properties");
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.COLUMN;
    }

    @Override
    public long dependentObjectId() {
        return objectId;
    }
}
