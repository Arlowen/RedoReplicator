/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import io.github.arlowen.redoreplicator.error.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StateMigrationRunner {
    private static final String TRACKING_TABLE = "schema_migration";
    private static final String INITIAL_SCHEMA_RESOURCE =
            "/db/migration/V001__initial_state.sql";
    private static final String FINGERPRINT_VARCHAR_RESOURCE =
            "/db/migration/V002__runtime_fingerprint_varchar.sql";

    private final List<StateMigration> migrations;

    StateMigrationRunner() throws IOException {
        migrations = List.of(
                loadMigration(1, "initial state", INITIAL_SCHEMA_RESOURCE),
                loadMigration(2, "runtime fingerprint varchar",
                        FINGERPRINT_VARCHAR_RESOURCE));
    }

    boolean requiresMigration(Connection connection) throws SQLException {
        if (!trackingTableExists(connection)) {
            return true;
        }
        Map<Integer, String> applied = readAppliedMigrations(connection);
        validateAppliedMigrations(applied);
        return applied.size() < migrations.size();
    }

    void migrate(Connection connection) throws SQLException {
        createTrackingTable(connection);
        Map<Integer, String> applied = readAppliedMigrations(connection);
        validateAppliedMigrations(applied);

        for (StateMigration migration : migrations) {
            if (applied.containsKey(migration.version())) {
                continue;
            }
            applySql(connection, migration.sql());
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO schema_migration"
                            + " (version, description, checksum, applied_at)"
                            + " VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.description());
                statement.setString(3, migration.checksum());
                statement.executeUpdate();
            }
        }
    }

    int currentVersion(Connection connection) throws SQLException {
        if (!trackingTableExists(connection)) {
            return 0;
        }
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COALESCE(MAX(version), 0) FROM schema_migration")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void validateAppliedMigrations(Map<Integer, String> applied) {
        int latestKnownVersion = migrations.get(migrations.size() - 1).version();
        for (Map.Entry<Integer, String> entry : applied.entrySet()) {
            if (entry.getKey() > latestKnownVersion) {
                throw new ConfigurationException(10001,
                        "State database version " + entry.getKey()
                                + " is newer than supported version " + latestKnownVersion);
            }
            StateMigration expected = migrations.stream()
                    .filter(migration -> migration.version() == entry.getKey())
                    .findFirst()
                    .orElseThrow(() -> new ConfigurationException(10001,
                            "Unknown state migration version " + entry.getKey()));
            if (!expected.checksum().equals(entry.getValue())) {
                throw new ConfigurationException(10001,
                        "State migration checksum mismatch for version " + entry.getKey());
            }
        }
    }

    private static boolean trackingTableExists(Connection connection) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, "PUBLIC", TRACKING_TABLE, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private static Map<Integer, String> readAppliedMigrations(Connection connection)
            throws SQLException {
        Map<Integer, String> applied = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version, checksum FROM schema_migration ORDER BY version")) {
            while (resultSet.next()) {
                applied.put(resultSet.getInt("version"), resultSet.getString("checksum"));
            }
        }
        return applied;
    }

    private static void createTrackingTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_migration ("
                    + "version INTEGER PRIMARY KEY,"
                    + "description VARCHAR(255) NOT NULL,"
                    + "checksum CHAR(64) NOT NULL,"
                    + "applied_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        }
    }

    private static void applySql(Connection connection, String sql) throws SQLException {
        List<String> statements = new ArrayList<>();
        for (String candidate : sql.split(";")) {
            String statement = candidate.trim();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
        }
        try (Statement statement = connection.createStatement()) {
            for (String sqlStatement : statements) {
                statement.execute(sqlStatement);
            }
        }
    }

    private static StateMigration loadMigration(int version, String description,
                                                String resource) throws IOException {
        byte[] bytes;
        try (InputStream input = StateMigrationRunner.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing state migration resource " + resource);
            }
            bytes = input.readAllBytes();
        }
        String sql = new String(bytes, StandardCharsets.UTF_8);
        return new StateMigration(version, description, sql, sha256(bytes));
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
