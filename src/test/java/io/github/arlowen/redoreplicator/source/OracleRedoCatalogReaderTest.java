/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.config.RedoPathMapper;
import io.github.arlowen.redoreplicator.config.RedoPathMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OracleRedoCatalogReaderTest {
    @TempDir
    Path mountedRedo;

    @Test
    void readsCurrentIncarnationArchiveAndOnlineCatalog() throws Exception {
        RedoPathMapper mapper = new RedoPathMapper(List.of(
                new RedoPathMapping("/oracle/redo", mountedRedo.toString())));

        OracleRedoCatalog catalog = new OracleRedoCatalogReader().read(
                connection(), mapper);

        assertEquals(100L,
                catalog.databaseContext().identity().databaseId());
        assertEquals(900L, catalog.databaseContext().currentScn().rawValue());
        assertEquals(1, catalog.archivedLogs().size());
        OracleRedoLog archived = catalog.archivedLogs().get(0);
        assertEquals(OracleRedoLogKind.ARCHIVED, archived.kind());
        assertEquals(1, archived.thread());
        assertEquals(10L, archived.sequence().value());
        assertEquals(mountedRedo.resolve("archive10.arc"),
                archived.localPath());
        assertEquals(1, catalog.onlineLogs().size());
        assertEquals("CURRENT", catalog.onlineLogs().get(0).status());
        assertEquals(mountedRedo.resolve("redo01.log"),
                catalog.onlineLogs().get(0).localPath());
    }

    private static Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                OracleRedoCatalogReaderTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareStatement")) {
                        return statement((String) arguments[0]);
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement statement(String sql) {
        return (PreparedStatement) Proxy.newProxyInstance(
                OracleRedoCatalogReaderTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("executeQuery")) {
                        return resultSet(rows(sql));
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ResultSet resultSet(List<Object[]> rows) {
        int[] current = {-1};
        return (ResultSet) Proxy.newProxyInstance(
                OracleRedoCatalogReaderTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        current[0]++;
                        return current[0] < rows.size();
                    }
                    if (method.getName().equals("getString")) {
                        return rows.get(current[0])[(Integer) arguments[0] - 1]
                                .toString();
                    }
                    if (method.getName().equals("getLong")) {
                        return ((Number) rows.get(current[0])[
                                (Integer) arguments[0] - 1]).longValue();
                    }
                    if (method.getName().equals("getInt")) {
                        return ((Number) rows.get(current[0])[
                                (Integer) arguments[0] - 1]).intValue();
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static List<Object[]> rows(String sql) {
        if (sql.contains("VERSION_FULL")) {
            return List.<Object[]>of(new Object[]{"19.25.0.0.0"});
        }
        if (sql.contains("D.DBID")) {
            return List.<Object[]>of(new Object[]{
                    100L, 900L, "ARCHIVELOG", "YES", "YES", "YES",
                    3L, 4L, "FREE", "FREEPDB1"});
        }
        if (sql.contains("FROM SYS.V_$ARCHIVED_LOG A")) {
            return List.<Object[]>of(new Object[]{
                    1, 10L, 100L, 200L, "A",
                    "/oracle/redo/archive10.arc"});
        }
        if (sql.contains("FROM SYS.V_$LOG L")) {
            return List.<Object[]>of(new Object[]{
                    1, 11L, 200L, 1_000L, "CURRENT",
                    "/oracle/redo/redo01.log"});
        }
        return List.of();
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }
}
