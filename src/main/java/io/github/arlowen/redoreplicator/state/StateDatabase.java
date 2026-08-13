/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class StateDatabase implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(StateDatabase.class);
    private static final String DATABASE_NAME = "redo-replicator";

    private final Connection connection;
    private final StateMigrationRunner migrationRunner;
    private final StateStore store;
    private final Path databaseFile;
    private final Path lastBackup;

    private StateDatabase(Connection connection, StateMigrationRunner migrationRunner,
                          Path databaseFile, Path lastBackup) {
        this.connection = connection;
        this.migrationRunner = migrationRunner;
        this.databaseFile = databaseFile;
        this.lastBackup = lastBackup;
        store = new StateStore(connection);
    }

    public static StateDatabase open(Path stateDirectory) throws IOException, SQLException {
        Path directory = stateDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path databaseBase = directory.resolve(DATABASE_NAME);
        Path databaseFile = Path.of(databaseBase + ".mv.db");
        String jdbcUrl = "jdbc:h2:file:" + databaseBase
                + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_ON_EXIT=FALSE";
        StateMigrationRunner migrationRunner = new StateMigrationRunner();
        boolean existing = Files.exists(databaseFile);
        Path backup = null;

        if (existing) {
            boolean migrationRequired;
            try (Connection inspection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
                migrationRequired = migrationRunner.requiresMigration(inspection);
            }
            if (migrationRequired) {
                backup = createBackup(directory, databaseFile);
            }
        }

        Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        try {
            migrationRunner.migrate(connection);
            return new StateDatabase(connection, migrationRunner, databaseFile, backup);
        } catch (SQLException | RuntimeException e) {
            String msg = "Failed to migrate state database";
            log.error(msg, e);
            try {
                connection.close();
            } catch (SQLException closeError) {
                log.error("Failed to close state database", closeError);
                e.addSuppressed(closeError);
            }
            if (backup != null) {
                restoreBackup(backup, databaseFile, e);
            } else if (!existing) {
                deleteFailedDatabase(databaseFile, e);
            }
            throw e;
        }
    }

    public StateStore store() {
        return store;
    }

    public int schemaVersion() throws SQLException {
        return migrationRunner.currentVersion(connection);
    }

    public Path databaseFile() {
        return databaseFile;
    }

    public Optional<Path> lastBackup() {
        return Optional.ofNullable(lastBackup);
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private static Path createBackup(Path directory, Path databaseFile) throws IOException {
        Path backupDirectory = directory.resolve("backups");
        Files.createDirectories(backupDirectory);
        Path backup = backupDirectory.resolve(
                DATABASE_NAME + "-" + Instant.now().toEpochMilli() + ".mv.db");
        Files.copy(databaseFile, backup);
        return backup;
    }

    private static void restoreBackup(Path backup, Path databaseFile, Throwable cause)
            throws IOException {
        try {
            Files.copy(backup, databaseFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException restoreError) {
            log.error("Failed to restore state database backup", restoreError);
            cause.addSuppressed(restoreError);
            throw restoreError;
        }
    }

    private static void deleteFailedDatabase(Path databaseFile, Throwable cause)
            throws IOException {
        try {
            Files.deleteIfExists(databaseFile);
        } catch (IOException deleteError) {
            log.error("Failed to delete incomplete state database", deleteError);
            cause.addSuppressed(deleteError);
            throw deleteError;
        }
    }
}
