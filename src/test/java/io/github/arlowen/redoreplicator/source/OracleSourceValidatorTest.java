/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.config.ConfigurationLoader;
import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleSourceValidatorTest {
    @TempDir
    Path installationDirectory;

    @Test
    void validatesDatabasePrivilegesMappingsAndReadableRedoFiles()
            throws Exception {
        Path redoDirectory = installationDirectory.resolve("mounted-redo");
        Files.createDirectory(redoDirectory);
        Path redoFile = redoDirectory.resolve("redo01.log");
        Files.write(redoFile, new byte[]{1});
        ResolvedConfiguration configuration = configuration(
                "/opt/oracle/oradata", redoDirectory);
        List<String> executedSql = new ArrayList<>();

        OracleSourceValidation validation = new OracleSourceValidator()
                .validate(connection(List.<Object[]>of(
                        new Object[]{"/opt/oracle/oradata/redo01.log", "ONLINE"}),
                        executedSql),
                        configuration);

        assertEquals("19.25.0.0.0",
                validation.databaseContext().version());
        assertEquals(List.of(redoFile), validation.redoFiles());
        assertTrue(executedSql.stream().anyMatch(
                sql -> sql.contains("SYS.LOBFRAG$")));
        assertTrue(executedSql.stream().anyMatch(
                sql -> sql.contains("SYS.V_$ARCHIVED_LOG")));
        assertTrue(executedSql.stream().anyMatch(
                sql -> sql.contains("SYS.V_$LOGFILE")));
    }

    @Test
    void stopsWhenOracleRedoPathIsNotMapped() throws Exception {
        Path redoDirectory = installationDirectory.resolve("mounted-redo");
        Files.createDirectory(redoDirectory);
        ResolvedConfiguration configuration = configuration(
                "/different/path", redoDirectory);

        ConfigurationException error = assertThrows(
                ConfigurationException.class,
                () -> new OracleSourceValidator().validate(
                        connection(List.<Object[]>of(new Object[]{
                                "/opt/oracle/oradata/redo01.log", "ONLINE"}),
                                new ArrayList<>()),
                        configuration));

        assertEquals(10007, error.getErrorCode());
    }

    @Test
    void stopsWhenMappedRedoFileIsMissing() throws Exception {
        Path redoDirectory = installationDirectory.resolve("mounted-redo");
        Files.createDirectory(redoDirectory);
        ResolvedConfiguration configuration = configuration(
                "/opt/oracle/oradata", redoDirectory);

        ConfigurationException error = assertThrows(
                ConfigurationException.class,
                () -> new OracleSourceValidator().validate(
                        connection(List.<Object[]>of(new Object[]{
                                "/opt/oracle/oradata/missing.log", "ONLINE"}),
                                new ArrayList<>()),
                        configuration));

        assertEquals(10008, error.getErrorCode());
    }

    private ResolvedConfiguration configuration(
            String oraclePath, Path localPath) throws Exception {
        Path configurationFile = installationDirectory.resolve("config.yaml");
        Files.writeString(configurationFile, """
                database:
                  url: jdbc:oracle:thin:@//oracle:1521/FREE
                  username: REDO_REPLICATOR
                  password: top-secret
                  redoPathMappings:
                    - oracle: %s
                      local: %s
                capture:
                  includeTables:
                    - FREEPDB1.APP.ORDERS
                """.formatted(oraclePath, localPath));
        Files.setPosixFilePermissions(configurationFile,
                PosixFilePermissions.fromString("rw-------"));
        return new ConfigurationLoader().load(
                installationDirectory, configurationFile);
    }

    private static Connection connection(
            List<Object[]> redoFiles, List<String> executedSql) {
        return (Connection) Proxy.newProxyInstance(
                OracleSourceValidatorTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareStatement")) {
                        String sql = (String) arguments[0];
                        executedSql.add(sql);
                        return statement(sql, redoFiles);
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement statement(
            String sql, List<Object[]> redoFiles) {
        return (PreparedStatement) Proxy.newProxyInstance(
                OracleSourceValidatorTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("executeQuery")) {
                        return resultSet(rows(sql, redoFiles));
                    }
                    if (method.getName().equals("close")
                            || method.getName().startsWith("set")) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ResultSet resultSet(List<Object[]> rows) {
        int[] current = {-1};
        return (ResultSet) Proxy.newProxyInstance(
                OracleSourceValidatorTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        current[0]++;
                        return current[0] < rows.size();
                    }
                    if (method.getName().equals("getString")) {
                        Object value = rows.get(current[0])[
                                (Integer) arguments[0] - 1];
                        return value.toString();
                    }
                    if (method.getName().equals("getLong")) {
                        Object value = rows.get(current[0])[
                                (Integer) arguments[0] - 1];
                        return ((Number) value).longValue();
                    }
                    if (method.getName().equals("getInt")) {
                        Object value = rows.get(current[0])[
                                (Integer) arguments[0] - 1];
                        return ((Number) value).intValue();
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static List<Object[]> rows(
            String sql, List<Object[]> redoFiles) {
        if (sql.contains("VERSION_FULL")) {
            return List.<Object[]>of(new Object[]{"19.25.0.0.0"});
        }
        if (sql.contains("D.DBID")) {
            return List.<Object[]>of(new Object[]{
                    100L, 200L, "ARCHIVELOG", "YES", "YES", "YES",
                    3L, 4L, "FREE", "FREEPDB1"});
        }
        if (sql.contains("JOIN SYS.V_$LOGFILE")) {
            List<Object[]> rows = new ArrayList<>();
            for (Object[] redoFile : redoFiles) {
                rows.add(new Object[]{
                        1, 10L, 100L, 1_000L, "CURRENT", redoFile[0]});
            }
            return rows;
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
