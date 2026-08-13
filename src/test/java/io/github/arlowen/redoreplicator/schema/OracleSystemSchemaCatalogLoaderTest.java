/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleSystemSchemaCatalogLoaderTest {

    @Test
    void requiresEveryTranslatedSystemDictionaryTable() throws Exception {
        long[] nextId = {1};
        TableSchemaLoader loader = (connection, owner, table, scn) -> {
            long objectId = nextId[0]++;
            return Optional.of(new TableSchema(
                    "FREEPDB1", owner, table,
                    objectId, objectId + 100, 0, 0, 0,
                    List.of(), List.of(), List.of()));
        };
        OracleSystemSchemaCatalogLoader catalogLoader =
                new OracleSystemSchemaCatalogLoader(loader);

        SchemaCatalog catalog = catalogLoader.load(
                connection(), Scn.of(100));

        assertEquals(SystemDictionaryTable.values().length,
                catalog.tableCount());
        assertTrue(catalog.tables().stream().allMatch(
                table -> table.owner().equals("SYS")));
    }

    @Test
    void stopsWhenPhysicalDictionarySchemaCannotBeProven() {
        TableSchemaLoader loader = (connection, owner, table, scn) ->
                Optional.empty();
        OracleSystemSchemaCatalogLoader catalogLoader =
                new OracleSystemSchemaCatalogLoader(loader);

        DataException error = assertThrows(DataException.class,
                () -> catalogLoader.load(connection(), Scn.of(100)));

        assertEquals(50071, error.getErrorCode());
        assertTrue(error.getMessage().contains("SYS.USER$"));
    }

    private static Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                OracleSystemSchemaCatalogLoaderTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> null);
    }
}
