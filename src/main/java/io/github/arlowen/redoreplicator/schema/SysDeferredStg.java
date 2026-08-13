/*
 * Java translation derived from OpenLogReplicator src/common/table/SysDeferredStg.h.
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

public record SysDeferredStg(RowId rowId, long objectId,
                             IntX flagsStg) implements SystemDictionaryRow {
    private static final long FLAG_COMPRESSED = 4;

    public SysDeferredStg {
        Objects.requireNonNull(rowId, "rowId");
        Objects.requireNonNull(flagsStg, "flagsStg");
    }

    public boolean compressed() {
        return flagsStg.isSet64(FLAG_COMPRESSED);
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.DEFERRED_STORAGE;
    }

    @Override
    public long dependentObjectId() {
        return objectId;
    }
}
