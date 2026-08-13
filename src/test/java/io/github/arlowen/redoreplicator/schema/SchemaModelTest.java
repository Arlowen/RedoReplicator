/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaModelTest {
    @Test
    void mapsInternalOracleTypeCodesAndSupportBoundary() {
        assertEquals(OracleColumnType.VARCHAR, OracleColumnType.fromCode(1));
        assertEquals(OracleColumnType.TIMESTAMP_WITH_LOCAL_TIME_ZONE,
                OracleColumnType.fromCode(231));
        assertEquals(OracleColumnType.NONE, OracleColumnType.fromCode(255));
        assertTrue(OracleColumnType.BOOLEAN.supported());
        assertFalse(OracleColumnType.LONG.supported());
        assertFalse(OracleColumnType.JSON.supported());
    }

    @Test
    void preservesPrimaryKeyOrderAndValidatesSegmentLayout() {
        TableSchema schema = tableSchema();

        assertEquals("FREEPDB1.APP.ORDERS", schema.qualifiedName());
        assertEquals(List.of(0, 1), schema.primaryKeyColumnIndexes());

        ColumnSchema invalid = column(3, "INVALID", 0);
        assertThrows(IllegalArgumentException.class,
                () -> new TableSchema("FREEPDB1", "APP", "INVALID",
                        1, 2, 3, 0, 0,
                        List.of(invalid), List.of(), List.of()));
    }

    @Test
    void resolvesTableFromTablePartitionAndLobIdentities() {
        TableSchema schema = tableSchema();
        SchemaCatalog catalog = new SchemaCatalog();

        catalog.add(schema);

        assertEquals(1, catalog.tableCount());
        assertEquals(schema, catalog.findByObjectId(101).orElseThrow());
        assertEquals(schema, catalog.findByDataObjectId(202).orElseThrow());
        assertEquals(schema, catalog.findByObjectId(303).orElseThrow());
        assertEquals(schema, catalog.findByDataObjectId(404).orElseThrow());
        assertEquals(schema, catalog.findByObjectId(505).orElseThrow());
        assertEquals(schema, catalog.findByDataObjectId(606).orElseThrow());
        assertEquals(schema, catalog.findByDataObjectId(707).orElseThrow());
        assertEquals(schema, catalog.findByDataObjectId(808).orElseThrow());
        assertEquals(schema.lobs().get(0),
                catalog.findLobByDataObjectId(606).orElseThrow());
        assertEquals(schema.lobs().get(0),
                catalog.findLobByDataObjectId(808).orElseThrow());
        assertEquals(schema.lobs().get(0),
                catalog.findLobIndexByDataObjectId(707).orElseThrow());
    }

    @Test
    void rejectsIdentitySharedByDifferentTables() {
        SchemaCatalog catalog = new SchemaCatalog();
        catalog.add(tableSchema());
        TableSchema conflicting = new TableSchema(
                "FREEPDB1", "APP", "CUSTOMERS",
                101, 909, 12, 0, 0,
                List.of(column(1, "ID", 1)), List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> catalog.add(conflicting));
    }

    @Test
    void roundTripsStableSchemaJsonAndLobPageSizes() throws Exception {
        TableSchema schema = tableSchema();
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();

        String first = codec.write(schema);
        TableSchema decoded = codec.read(first);

        assertEquals(schema, decoded);
        assertEquals(first, codec.write(decoded));
        assertEquals(8192, schema.lobs().get(0).pageSize(808));
        assertEquals(8132, schema.lobs().get(0).pageSize(999));
    }

    private static TableSchema tableSchema() {
        LobSchema lob = new LobSchema(
                101, 606, 505, 3, 3,
                List.of(707L), List.of(new LobPartition(808, 8192)));
        return new TableSchema(
                "FREEPDB1",
                "APP",
                "ORDERS",
                101,
                202,
                12,
                0,
                0,
                List.of(column(1, "TENANT_ID", 2),
                        column(2, "ORDER_ID", 1),
                        column(3, "PAYLOAD", 0)),
                List.of(lob),
                List.of(new TablePartition(303, 404)));
    }

    private static ColumnSchema column(int segmentColumn, String name,
                                       int primaryKeyMembership) {
        OracleColumnType type = OracleColumnType.NUMBER;
        boolean storedAsLob = false;
        if ("PAYLOAD".equals(name)) {
            type = OracleColumnType.CLOB;
            storedAsLob = true;
        }
        return new ColumnSchema(
                segmentColumn,
                -1,
                segmentColumn,
                segmentColumn,
                name,
                type,
                22,
                -1,
                -1,
                0,
                primaryKeyMembership,
                false,
                false,
                storedAsLob,
                false,
                false,
                false,
                false,
                false,
                false);
    }
}
