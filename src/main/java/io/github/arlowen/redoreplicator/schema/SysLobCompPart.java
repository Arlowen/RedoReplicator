/*
 * Java translation derived from OpenLogReplicator src/common/table/SysLobCompPart.h.
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

public record SysLobCompPart(RowId rowId, long partitionObjectId,
                             long lobObjectId) implements SystemDictionaryRow {
    public SysLobCompPart {
        Objects.requireNonNull(rowId, "rowId");
    }

    @Override
    public SystemDictionaryTable dictionaryTable() {
        return SystemDictionaryTable.LOB_COMPOSITE_PARTITION;
    }

    @Override
    public long dependentObjectId() {
        return 0;
    }
}
