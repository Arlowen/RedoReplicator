/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OracleContainerCatalogReaderTest {

    @Test
    void discoversTheRootAndOpenPdbs() throws Exception {
        OracleContainerRegistry registry =
                new OracleContainerCatalogReader().read(
                        connection(), context("CDB$ROOT", true));

        assertEquals(List.of(
                        new OracleContainer(1, "CDB$ROOT"),
                        new OracleContainer(3, "FREEPDB1"),
                        new OracleContainer(4, "REPORTING")),
                registry.containers());
    }

    @Test
    void keepsAPdbConnectionScopedToItsCurrentContainer() throws Exception {
        OracleContainerRegistry registry =
                new OracleContainerCatalogReader().read(
                        connection(3, "FREEPDB1"),
                        context("FREEPDB1", true));

        assertEquals(List.of(new OracleContainer(3, "FREEPDB1")),
                registry.containers());
    }

    @Test
    void switchesOnlyToValidatedContainerIdentifiers() throws Exception {
        AtomicReference<String> sql = new AtomicReference<>();
        Connection connection = connection(sql);

        new OracleContainerSession().switchTo(connection, "FREEPDB1");

        assertEquals("ALTER SESSION SET CONTAINER = \"FREEPDB1\"",
                sql.get());
        assertThrows(IllegalArgumentException.class,
                () -> new OracleContainerSession().switchTo(
                        connection, "FREEPDB1\"; DROP USER APP"));
    }

    private static OracleDatabaseContext context(
            String container, boolean cdb) {
        return new OracleDatabaseContext(
                new DatabaseIdentity(1, 2, 3), Scn.of(100),
                "FREE", container, "19.25.0.0.0", cdb,
                "ARCHIVELOG", true, true);
    }

    private static Connection connection() {
        return connection(1, "CDB$ROOT");
    }

    private static Connection connection(int id, String name) {
        return (Connection) Proxy.newProxyInstance(
                OracleContainerCatalogReaderTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareStatement")) {
                        return statement(
                                (String) arguments[0], id, name);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Connection connection(AtomicReference<String> sql) {
        return (Connection) Proxy.newProxyInstance(
                OracleContainerCatalogReaderTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareStatement")) {
                        sql.set((String) arguments[0]);
                        return statement(List.of());
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement statement(
            String sql, int currentId, String currentName) {
        if (sql.contains("V_$PDBS")) {
            return statement(List.of(
                    new Object[]{3, "FREEPDB1"},
                    new Object[]{4, "REPORTING"}));
        }
        return statement(List.<Object[]>of(
                new Object[]{currentId, currentName}));
    }

    private static PreparedStatement statement(List<Object[]> rows) {
        return (PreparedStatement) Proxy.newProxyInstance(
                OracleContainerCatalogReaderTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("executeQuery")) {
                        return resultSet(rows);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ResultSet resultSet(List<Object[]> rows) {
        int[] current = {-1};
        return (ResultSet) Proxy.newProxyInstance(
                OracleContainerCatalogReaderTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        current[0]++;
                        return current[0] < rows.size();
                    }
                    if (method.getName().equals("getInt")) {
                        return rows.get(current[0])[
                                (Integer) arguments[0] - 1];
                    }
                    if (method.getName().equals("getString")) {
                        return rows.get(current[0])[
                                (Integer) arguments[0] - 1];
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }
}
