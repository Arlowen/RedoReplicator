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

import java.util.Objects;

public record OracleColumnMetadata(int columnNumber, int segmentColumn,
                                   int internalColumn, String name, int typeCode,
                                   int length, int precision, int scale,
                                   int charsetForm, long charsetId, int nullFlag,
                                   long propertyBits) {
    public OracleColumnMetadata {
        Objects.requireNonNull(name, "name");
    }
}
