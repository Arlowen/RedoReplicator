/*
 * Java translation derived from OpenLogReplicator src/common/DbColumn.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import java.util.Objects;

public record ColumnSchema(int columnNumber, int guardSegment, int segmentColumn,
                           int internalColumn, String name, OracleColumnType type,
                           int length, int precision, int scale, long charsetId,
                           int primaryKeyMembership, boolean nullable, boolean hidden,
                           boolean storedAsLob, boolean systemGenerated, boolean nested,
                           boolean unused, boolean added, boolean guard, boolean xmlType) {
    public ColumnSchema {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
