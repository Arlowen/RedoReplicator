/*
 * Java translation derived from OpenLogReplicator src/common/table/SysObj.h.
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

public record SysObj(RowId rowId, long ownerId, long objectId,
                     long dataObjectId, int typeCode, String name,
                     IntX flags) implements SystemDictionaryRow {
    public static final int NAME_LENGTH = 128;
    public static final int TYPE_TABLE = 2;
    private static final long FLAG_TEMPORARY = 1L << 1;
    private static final long FLAG_SECONDARY = 1L << 4;
    private static final long FLAG_IN_MEMORY_TEMPORARY = 1L << 5;
    private static final long FLAG_DROPPED = 1L << 7;

    public SysObj {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(flags, "flags");
    }

    public boolean table() {
        return typeCode == TYPE_TABLE;
    }

    public boolean dropped() {
        return flags.isSet64(FLAG_DROPPED);
    }

    public boolean temporary() {
        return flags.isSet64(FLAG_TEMPORARY)
                || flags.isSet64(FLAG_SECONDARY)
                || flags.isSet64(FLAG_IN_MEMORY_TEMPORARY);
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.OBJECT;
    }

    @Override
    public long dependentObjectId() {
        return objectId;
    }
}
