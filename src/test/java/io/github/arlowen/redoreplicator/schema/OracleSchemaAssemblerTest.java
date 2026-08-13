/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleSchemaAssemblerTest {
    @Test
    void translatesSysColPropertiesAndCharacterSets() {
        OracleTableMetadata table = new OracleTableMetadata(
                "APP", "ORDERS", 101, 102, 12, 0, 0,
                0, 0, 0, false);
        List<OracleColumnMetadata> rawColumns = List.of(
                column(1, 1, 1, "ID", 2, 0, 0),
                column(2, 2, 2, "NAME", 1, 1, (1L << 5) | (1L << 30)),
                column(3, 3, 3, "PAYLOAD", 112, 2,
                        (1L << 7) | (1L << 10) | (1L << 39)));

        TableSchema schema = new OracleSchemaAssembler().assemble(
                "FREEPDB1",
                table,
                rawColumns,
                Map.of(1, 1),
                Map.of(3, 7),
                873,
                2000,
                List.of(),
                List.of());

        assertEquals(List.of(0), schema.primaryKeyColumnIndexes());
        assertEquals(873, schema.columns().get(1).charsetId());
        assertTrue(schema.columns().get(1).hidden());
        assertTrue(schema.columns().get(1).added());
        assertEquals(2000, schema.columns().get(2).charsetId());
        assertEquals(7, schema.columns().get(2).guardSegment());
        assertTrue(schema.columns().get(2).storedAsLob());
        assertTrue(schema.columns().get(2).nested());
        assertTrue(schema.columns().get(2).guard());
    }

    @Test
    void exposesXmlTypeUsingBaseColumnName() {
        OracleTableMetadata table = new OracleTableMetadata(
                "APP", "XML_DOCS", 201, 202, 12, 0, 0,
                0, 0, 0, false);
        List<OracleColumnMetadata> rawColumns = List.of(
                column(1, 0, 1, "DOCUMENT", 58, 0, 0),
                column(1, 1, 2, "SYS_NC00001$", 112, 1,
                        (1L << 5) | (1L << 8) | (1L << 7)));

        TableSchema schema = new OracleSchemaAssembler().assemble(
                "FREEPDB1",
                table,
                rawColumns,
                Map.of(),
                Map.of(),
                873,
                2000,
                List.of(),
                List.of());

        ColumnSchema column = schema.columns().get(0);
        assertEquals("DOCUMENT", column.name());
        assertTrue(column.xmlType());
        assertTrue(column.systemGenerated());
        assertFalse(column.hidden());
    }

    private static OracleColumnMetadata column(int columnNumber, int segmentColumn,
                                               int internalColumn, String name,
                                               int type, int charsetForm,
                                               long propertyBits) {
        return new OracleColumnMetadata(
                columnNumber,
                segmentColumn,
                internalColumn,
                name,
                type,
                100,
                -1,
                -1,
                charsetForm,
                0,
                0,
                propertyBits);
    }
}
