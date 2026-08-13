/*
 * Java translation derived from OpenLogReplicator src/common/table/SysTab.h.
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

public record SysTab(RowId rowId, long objectId, long dataObjectId,
                     long tablespaceId, int clusterColumns,
                     IntX flags, IntX properties) implements SystemDictionaryRow {
    public SysTab {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(flags, "flags");
        Objects.requireNonNull(properties, "properties");
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.TABLE;
    }

    @Override
    public long dependentObjectId() {
        return objectId;
    }
}
