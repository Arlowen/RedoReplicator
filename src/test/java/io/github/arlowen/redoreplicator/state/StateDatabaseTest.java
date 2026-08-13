/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateDatabaseTest {
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.of(
            2026, 8, 13, 12, 0, 0, 0, ZoneOffset.ofHours(8));

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsSchemaAndPersistsRuntimeState() throws Exception {
        RuntimeState expected = runtimeState(Scn.of(100), Optional.of(position(90)));

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            assertEquals(1, database.schemaVersion());
            assertFalse(database.lastBackup().isPresent());
            database.store().commitLwn(expected, List.of());
        }

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            assertEquals(Optional.of(expected), database.store().loadRuntimeState());
            assertFalse(database.lastBackup().isPresent());
        }
    }

    @Test
    void roundTripsUnsignedScnAndFileOffset() throws Exception {
        RedoPosition durable = new RedoPosition(
                Scn.of(0xF123_4567_89AB_CDEFL),
                2,
                Seq.of(0xF123_4567L),
                FileOffset.of(0xE123_4567_89AB_CDEFL));
        RuntimeState expected = new RuntimeState(
                1, 2, 3, durable, Optional.empty(), 4, 5,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                UPDATED_AT);

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(expected, List.of());
            assertEquals(Optional.of(expected), database.store().loadRuntimeState());
        }
    }

    @Test
    void validatesDatabaseIncarnationBeforeResume() throws Exception {
        RuntimeState state = runtimeState(Scn.of(100), Optional.empty());
        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(state, List.of());
            database.store().validateDatabaseIdentity(new DatabaseIdentity(
                    state.databaseId(), state.incarnation(), state.resetlogsId()));

            assertThrows(ConfigurationException.class,
                    () -> database.store().validateDatabaseIdentity(new DatabaseIdentity(
                            state.databaseId(), state.incarnation(), state.resetlogsId() + 1)));
        }
    }

    @Test
    void resolvesLatestCompleteSchemaAtTargetScn() throws Exception {
        TableSchemaVersion first = schemaVersion(100, 101, false, SchemaSource.INITIAL);
        TableSchemaVersion second = schemaVersion(200, 202, true, SchemaSource.REDO);

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(
                    runtimeState(Scn.of(200), Optional.empty()), List.of(first, second));

            assertTrue(database.store().findSchemaAt(
                    "FREEPDB1", "APP", "ORDERS", Scn.of(99)).isEmpty());
            assertEquals(Optional.of(first), database.store().findSchemaAt(
                    "FREEPDB1", "APP", "ORDERS", Scn.of(150)));
            assertEquals(Optional.of(second), database.store().findSchemaAt(
                    "FREEPDB1", "APP", "ORDERS", Scn.of(200)));
        }
    }

    @Test
    void rollsBackSchemaAndRuntimeStateTogether() throws Exception {
        RuntimeState firstState = runtimeState(Scn.of(100), Optional.empty());
        TableSchemaVersion schema = schemaVersion(100, 101, false, SchemaSource.INITIAL);

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(firstState, List.of(schema));
            RuntimeState rejectedState = runtimeState(Scn.of(200), Optional.empty());

            assertThrows(SQLException.class,
                    () -> database.store().commitLwn(rejectedState, List.of(schema)));

            assertEquals(Optional.of(firstState), database.store().loadRuntimeState());
            assertEquals(Optional.of(schema), database.store().findSchemaAt(
                    "FREEPDB1", "APP", "ORDERS", Scn.of(200)));
        }
    }

    @Test
    void backsUpExistingDatabaseBeforeInitialMigration() throws Exception {
        Path databaseBase = temporaryDirectory.resolve("redo-replicator");
        String jdbcUrl = "jdbc:h2:file:" + databaseBase
                + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_ON_EXIT=FALSE";
        try (var ignored = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertTrue(Files.exists(Path.of(databaseBase + ".mv.db")));
        }

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            assertEquals(1, database.schemaVersion());
            assertTrue(database.lastBackup().isPresent());
            assertTrue(Files.exists(database.lastBackup().orElseThrow()));
        }
    }

    @Test
    void rejectsStateDatabaseFromNewerApplicationVersion() throws Exception {
        Path databaseBase = temporaryDirectory.resolve("redo-replicator");
        String jdbcUrl = "jdbc:h2:file:" + databaseBase
                + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_ON_EXIT=FALSE";
        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_migration ("
                    + "version INTEGER PRIMARY KEY,"
                    + "description VARCHAR(255) NOT NULL,"
                    + "checksum CHAR(64) NOT NULL,"
                    + "applied_at TIMESTAMP WITH TIME ZONE NOT NULL)");
            statement.executeUpdate("INSERT INTO schema_migration VALUES"
                    + " (2, 'future', '0000000000000000000000000000000000000000000000000000000000000000',"
                    + " CURRENT_TIMESTAMP)");
        }

        assertThrows(ConfigurationException.class,
                () -> StateDatabase.open(temporaryDirectory));
    }

    private static RuntimeState runtimeState(Scn durableScn,
                                             Optional<RedoPosition> lowWatermark) {
        return new RuntimeState(
                0xF100_0001L,
                7,
                9,
                new RedoPosition(durableScn, 1, Seq.of(11), FileOffset.of(4096)),
                lowWatermark,
                3,
                2048,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                UPDATED_AT);
    }

    private static RedoPosition position(long scn) {
        return new RedoPosition(Scn.of(scn), 1, Seq.of(10), FileOffset.of(2048));
    }

    private static TableSchemaVersion schemaVersion(long scn, long objectId,
                                                    boolean dropped, SchemaSource source) {
        return new TableSchemaVersion(
                "FREEPDB1",
                "APP",
                "ORDERS",
                objectId,
                objectId + 1,
                Scn.of(scn),
                "{\"columns\":[{\"name\":\"ID\",\"type\":2}]}",
                dropped ? "DROP" : "INITIAL",
                dropped ? "DROP TABLE APP.ORDERS" : "",
                source,
                dropped);
    }
}
