/*
 * Java translation derived from OpenLogReplicator src/common/table/SysLobFrag.h.
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

public record SysLobFrag(RowId rowId, long fragmentObjectId,
                         long parentObjectId,
                         long tablespaceId) implements SystemDictionaryRow {
    public SysLobFrag {
        Objects.requireNonNull(rowId, "rowId");
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.LOB_FRAGMENT;
    }

    @Override
    public long dependentObjectId() {
        return 0;
    }
}
