/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OracleTableSupportTest {
    @Test
    void acceptsRegularHeapTable() {
        assertDoesNotThrow(() -> OracleTableSupport.validate(metadata(0, 0, 0, false)));
        assertDoesNotThrow(() -> OracleTableSupport.validate(metadata(0, 0, 0, true)));
    }

    @Test
    void rejectsEveryOutOfScopeTableLayout() {
        assertUnsupported(metadata(1L << 1, 0, 0, false), "temporary table");
        assertUnsupported(metadata(0, 0, 1, false), "binary table");
        assertUnsupported(metadata(0, 0, 1L << 6, false), "index-organized table");
        assertUnsupported(metadata(0, 1L << 29, 0, false), "index-organized table");
        assertUnsupported(metadata(0, 0, 1L << 13, false), "nested table");
        assertUnsupported(metadata(0, 0, 1L << 26, false), "materialized view table");
        assertUnsupported(metadata(0, 0, 1L << 31, false), "external table");
    }

    private static void assertUnsupported(OracleTableMetadata table, String reason) {
        DataException exception = assertThrows(
                DataException.class, () -> OracleTableSupport.validate(table));
        assertEquals(50030, exception.getErrorCode());
        assertEquals("Table APP.ORDERS is unsupported: " + reason,
                exception.getMessage());
    }

    private static OracleTableMetadata metadata(long objectFlags, long tableFlags,
                                                long tableProperties,
                                                boolean compressed) {
        return new OracleTableMetadata(
                "APP", "ORDERS", 101, 102, 12, 0, 0,
                objectFlags, tableFlags, tableProperties, compressed);
    }
}
