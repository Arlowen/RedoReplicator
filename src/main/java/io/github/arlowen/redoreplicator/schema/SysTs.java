/*
 * Java translation derived from OpenLogReplicator src/common/table/SysTs.h.
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

public record SysTs(RowId rowId, long tablespaceId, String name,
                    int blockSize) implements SystemDictionaryRow {
    public static final int NAME_LENGTH = 30;

    public SysTs {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.TABLESPACE;
    }

    @Override
    public long dependentObjectId() {
        return 0;
    }
}
