/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.config.TableFilter;
import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleInitialSchemaLoaderTest {

    @Test
    void keepsReferenceRowsWhileWaitingForFutureTables() throws Exception {
        SysUser user = new SysUser(
                RowId.of(300, 10, 1), 12, "APP", IntX.zero());
        SysTs tablespace = new SysTs(
                RowId.of(300, 10, 2), 7, "USERS", 16_384);
        OracleInitialSchemaLoader loader = new OracleInitialSchemaLoader(
                (connection, owner, table, scn) -> Optional.empty(),
                new FixedReferenceDictionaryLoader(
                        SystemDictionaryState.of(List.of(user, tablespace))),
                new TableSchemaJsonCodec());
        TableFilter filter = new TableFilter(
                List.of(Pattern.compile("FREEPDB1\\.APP\\.FUTURE_.*")),
                List.of());

        InitialSchemaSnapshot snapshot = loader.load(
                connection(), new SchemaCatalog(), filter, Scn.of(500));

        assertEquals(List.of(user), snapshot.dictionaryState().users());
        assertEquals(List.of(tablespace),
                snapshot.dictionaryState().tablespaces());
        assertTrue(snapshot.schemaVersions().isEmpty());
    }

    @Test
    void loadsFullHistoryOnlyForSelectedTablesAndKeepsOtherIdentities()
            throws Exception {
        SchemaCatalog identities = new SchemaCatalog();
        identities.add(identity("ORDERS", 100, 101));
        identities.add(identity("AUDIT", 200, 201));
        TableSchema fullOrders = fullTable("ORDERS", 100, 101);
        SysUser sharedUser = new SysUser(
                RowId.of(300, 10, 1), 12, "APP", IntX.zero());
        OracleInitialSchemaLoader loader = new OracleInitialSchemaLoader(
                (connection, owner, table, scn) -> {
                    if (table.equals("ORDERS")) {
                        return Optional.of(fullOrders);
                    }
                    return Optional.empty();
                },
                (connection, owner, table, scn) -> Optional.of(
                        SystemDictionaryState.of(List.of(sharedUser))),
                new TableSchemaJsonCodec());
        TableFilter filter = new TableFilter(
                List.of(Pattern.compile(Pattern.quote(
                        "FREEPDB1.APP.ORDERS"))),
                List.of());

        InitialSchemaSnapshot snapshot = loader.load(
                connection(), identities, filter, Scn.of(500));

        assertEquals(2, snapshot.tableCatalog().tableCount());
        assertEquals(1,
                snapshot.tableCatalog().findByObjectId(100)
                        .orElseThrow().columns().size());
        assertTrue(snapshot.tableCatalog().findByObjectId(200)
                .orElseThrow().columns().isEmpty());
        assertEquals(List.of(sharedUser),
                snapshot.dictionaryState().users());
        assertEquals(1, snapshot.schemaVersions().size());
        assertEquals("ORDERS", snapshot.schemaVersions().get(0).table());
        assertEquals(Scn.of(500),
                snapshot.schemaVersions().get(0).effectiveScn());
    }

    private static TableSchema identity(
            String name, long objectId, long dataObjectId) {
        return new TableSchema(
                "FREEPDB1", "APP", name,
                objectId, dataObjectId, 12, 0, 0,
                List.of(), List.of(), List.of());
    }

    private static TableSchema fullTable(
            String name, long objectId, long dataObjectId) {
        return new TableSchema(
                "FREEPDB1", "APP", name,
                objectId, dataObjectId, 12, 0, 0,
                List.of(new ColumnSchema(
                        1, -1, 1, 1, "ID", OracleColumnType.NUMBER,
                        22, 0, 0, 0, 1,
                        false, false, false, false, false,
                        false, false, false, false)),
                List.of(), List.of());
    }

    private static Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                OracleInitialSchemaLoaderTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> null);
    }
}
