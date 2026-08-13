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

    private static TableSchema table(long objectId, long dataObjectId) {
        return new TableSchema(
                "FREEPDB1", "APP", "ORDERS",
                objectId, dataObjectId, 12, 0, 0,
                List.of(), List.of(), List.of());
    }
}
