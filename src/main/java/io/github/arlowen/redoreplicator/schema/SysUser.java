/*
 * Java translation derived from OpenLogReplicator src/common/table/SysUser.h.
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

public record SysUser(RowId rowId, long userId, String name,
                      IntX spare1) implements SystemDictionaryRow {
    public static final int NAME_LENGTH = 128;

    public SysUser {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(spare1, "spare1");
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.USER;
    }

    @Override
    public long dependentObjectId() {
        return 0;
    }
}
