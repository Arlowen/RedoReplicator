/*
 * Java translation derived from OpenLogReplicator src/metadata/Schema.cpp table checks.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;

final class OracleTableSupport {
    private static final long OBJECT_TEMPORARY = 1L << 1;
    private static final long TABLE_BINARY = 1L;
    private static final long TABLE_IOT_INDEX_ONLY = 1L << 6;
    private static final long TABLE_IOT_ROW_OVERFLOW = 1L << 7;
    private static final long TABLE_NESTED = 1L << 13;
    private static final long TABLE_GLOBAL_TEMPORARY = 1L << 22;
    private static final long TABLE_MATERIALIZED_VIEW = 1L << 26;
    private static final long TABLE_EXTERNAL = 1L << 31;
    private static final long TABLE_IOT_MAPPING = 1L << 29;

    static void validate(OracleTableMetadata table) {
        if (hasFlag(table.objectFlags(), OBJECT_TEMPORARY)
                || hasFlag(table.tableProperties(), TABLE_GLOBAL_TEMPORARY)) {
            unsupported(table, "temporary table");
        }
        if (hasFlag(table.tableProperties(), TABLE_BINARY)) {
            unsupported(table, "binary table");
        }
        if (hasFlag(table.tableProperties(), TABLE_IOT_INDEX_ONLY)
                || hasFlag(table.tableProperties(), TABLE_IOT_ROW_OVERFLOW)
                || hasFlag(table.tableFlags(), TABLE_IOT_MAPPING)) {
            unsupported(table, "index-organized table");
        }
        if (hasFlag(table.tableProperties(), TABLE_NESTED)) {
            unsupported(table, "nested table");
        }
        if (hasFlag(table.tableProperties(), TABLE_MATERIALIZED_VIEW)) {
            unsupported(table, "materialized view table");
        }
        if (hasFlag(table.tableProperties(), TABLE_EXTERNAL)) {
            unsupported(table, "external table");
        }
        if (table.delayedStorageCompressed()) {
            unsupported(table, "compressed table");
        }
    }

    private static void unsupported(OracleTableMetadata table, String reason) {
        throw new DataException(
                50030,
                "Table " + table.owner() + "." + table.name()
                        + " is unsupported: " + reason);
    }

    private static boolean hasFlag(long value, long flag) {
        return (value & flag) != 0;
    }

    private OracleTableSupport() {
    }
}
