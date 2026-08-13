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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaCatalogTest {

    @Test
    void rebuildsObjectIndexesWhenSchemaIsReplacedOrRemoved() {
        SchemaCatalog catalog = new SchemaCatalog();
        TableSchema first = table(100, 101);
        TableSchema replacement = table(200, 201);
        catalog.add(first);

        catalog.replace(replacement);

        assertTrue(catalog.findByObjectId(100).isEmpty());
        assertTrue(catalog.findByDataObjectId(101).isEmpty());
        assertEquals(replacement,
                catalog.findByObjectId(200).orElseThrow());
        assertEquals(1, catalog.tableCount());

        catalog.remove("FREEPDB1", "APP", "ORDERS");

        assertEquals(0, catalog.tableCount());
        assertTrue(catalog.findByObjectId(200).isEmpty());
        assertTrue(catalog.findByDataObjectId(201).isEmpty());
    }

    @Test
    void isolatesDuplicateObjectIdsAcrossPdbs() {
        SchemaCatalog catalog = new SchemaCatalog();
        TableSchema first = table("SALES", 100, 101);
        TableSchema second = table("REPORTING", 100, 101);

        catalog.add(first);
        catalog.add(second);

        assertEquals(first,
                catalog.findByObjectId("SALES", 100).orElseThrow());
        assertEquals(second,
                catalog.findByDataObjectId("REPORTING", 101)
                        .orElseThrow());
        IllegalStateException ambiguous = assertThrows(
                IllegalStateException.class,
                () -> catalog.findByObjectId(100));
        assertEquals("Ambiguous object id 100 across Oracle containers",
                ambiguous.getMessage());
    }

    @Test
    void keepsClusteredTablesAddressableByObjectId() {
        SchemaCatalog catalog = new SchemaCatalog();
        TableSchema first = table("FREEPDB1", 100, 2);
        TableSchema second = new TableSchema(
                "FREEPDB1", "APP", "CLUSTERED_ORDERS",
                200, 2, 12, 0, 0,
                List.of(), List.of(), List.of());

        catalog.add(first);
        catalog.add(second);

        assertEquals(first,
                catalog.findByObjectId("FREEPDB1", 100).orElseThrow());
        assertEquals(second,
                catalog.findByObjectId("FREEPDB1", 200).orElseThrow());
        IllegalStateException ambiguous = assertThrows(
                IllegalStateException.class,
                () -> catalog.findByDataObjectId("FREEPDB1", 2));
        assertEquals("Ambiguous data object id 2 in Oracle container FREEPDB1",
                ambiguous.getMessage());
    }

    private static TableSchema table(long objectId, long dataObjectId) {
        return table("FREEPDB1", objectId, dataObjectId);
    }

    private static TableSchema table(
            String container, long objectId, long dataObjectId) {
        return new TableSchema(
                container, "APP", "ORDERS",
                objectId, dataObjectId, 12, 0, 0,
                List.of(), List.of(), List.of());
    }
}
