/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.SchemaSource;
import io.github.arlowen.redoreplicator.state.StateDatabase;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DdlSchemaVersionBuilderTest {
    private static final String CONTAINER = "FREEPDB1";
    private static final String OWNER = "APP";
    private static final String TABLE = "ORDERS";
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.of(
            2026, 8, 13, 15, 0, 0, 0, ZoneOffset.ofHours(8));

    @TempDir
    Path temporaryDirectory;

    @Test
    void recordsCompleteSchemaAtAlterCommitScn() throws Exception {
        TableSchema altered = tableSchema(201, 202);
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
        DdlSchemaChange change = change(
                15, "ALTER TABLE APP.ORDERS ADD DESCRIPTION VARCHAR2(100)", 200);

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            DdlSchemaVersionBuilder builder = new DdlSchemaVersionBuilder(
                    database.store(),
                    (connection, owner, table, targetScn) -> Optional.of(altered),
                    codec);

            List<TableSchemaVersion> versions = builder.build(null, change);

            assertEquals(1, versions.size());
            TableSchemaVersion version = versions.get(0);
            assertEquals(Scn.of(200), version.effectiveScn());
            assertEquals("ALTER", version.ddlType());
            assertEquals(change.ddlText(), version.ddlText());
            assertEquals(SchemaSource.FLASHBACK, version.source());
            assertEquals(altered, version.decode(codec));
        }
    }

    @Test
    void recordsDropTombstoneFromH2Base() throws Exception {
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
        TableSchemaVersion initial = TableSchemaVersion.initial(
                tableSchema(101, 102), Scn.of(100), codec);
        DdlSchemaChange change = change(12, "DROP TABLE APP.ORDERS", 200);

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(runtimeState(150), List.of(initial));
            DdlSchemaVersionBuilder builder = new DdlSchemaVersionBuilder(
                    database.store(),
                    (connection, owner, table, targetScn) -> Optional.empty(),
                    codec);

            List<TableSchemaVersion> versions = builder.build(null, change);

            assertEquals(1, versions.size());
            TableSchemaVersion tombstone = versions.get(0);
            assertTrue(tombstone.dropTombstone());
            assertEquals(initial.schemaJson(), tombstone.schemaJson());
            assertEquals("DROP", tombstone.ddlType());
            assertEquals(Scn.of(200), tombstone.effectiveScn());
        }
    }

    @Test
    void restoresPreDropSchemaWhenH2HasNoBase() throws Exception {
        TableSchema beforeDrop = tableSchema(101, 102);
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
        DdlSchemaChange change = change(12, "DROP TABLE APP.ORDERS", 200);
        List<Scn> requestedScns = new ArrayList<>();

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            DdlSchemaVersionBuilder builder = new DdlSchemaVersionBuilder(
                    database.store(),
                    (connection, owner, table, targetScn) -> {
                        requestedScns.add(targetScn);
                        if (targetScn.equals(Scn.of(199))) {
                            return Optional.of(beforeDrop);
                        }
                        return Optional.empty();
                    },
                    codec);

            List<TableSchemaVersion> versions = builder.build(null, change);

            assertEquals(List.of(Scn.of(200), Scn.of(199)), requestedScns);
            assertEquals(2, versions.size());
            assertEquals(SchemaSource.FLASHBACK, versions.get(0).source());
            assertEquals(Scn.of(199), versions.get(0).effectiveScn());
            assertEquals(beforeDrop, versions.get(0).decode(codec));
            assertTrue(versions.get(1).dropTombstone());
            assertEquals(Scn.of(200), versions.get(1).effectiveScn());
        }
    }

    @Test
    void ignoresPurgeAfterPersistedDrop() throws Exception {
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
        TableSchemaVersion initial = TableSchemaVersion.initial(
                tableSchema(101, 102), Scn.of(100), codec);
        TableSchemaVersion dropped = TableSchemaVersion.drop(
                initial, Scn.of(200), "DROP", "DROP TABLE APP.ORDERS",
                SchemaSource.REDO);

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(runtimeState(200), List.of(initial, dropped));
            DdlSchemaVersionBuilder builder = new DdlSchemaVersionBuilder(
                    database.store(),
                    (connection, owner, table, targetScn) -> Optional.empty(),
                    codec);

            List<TableSchemaVersion> versions = builder.build(
                    null, change(198, "PURGE TABLE APP.ORDERS", 210));

            assertTrue(versions.isEmpty());
        }
    }

    @Test
    void stopsWhenNonDropDdlHasNoResultingSchema() throws Exception {
        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            DdlSchemaVersionBuilder builder = new DdlSchemaVersionBuilder(
                    database.store(),
                    (connection, owner, table, targetScn) -> Optional.empty(),
                    new TableSchemaJsonCodec());

            DataException exception = assertThrows(DataException.class,
                    () -> builder.build(
                            null, change(15, "ALTER TABLE APP.ORDERS MOVE", 200)));

            assertEquals(50071, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("cannot be proved"));
        }
    }

    @Test
    void stopsWhenDropHasNeitherH2NorFlashbackBase() throws Exception {
        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            DdlSchemaVersionBuilder builder = new DdlSchemaVersionBuilder(
                    database.store(),
                    (connection, owner, table, targetScn) -> Optional.empty(),
                    new TableSchemaJsonCodec());

            DataException exception = assertThrows(DataException.class,
                    () -> builder.build(
                            null, change(12, "DROP TABLE APP.ORDERS", 200)));

            assertEquals(50071, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("No complete schema exists"));
        }
    }

    @Test
    void mapsOracleDdlCodesLikeOpenLogReplicator() {
        assertEquals(DdlOperation.CREATE, DdlOperation.fromOracleCode(1));
        assertEquals(DdlOperation.CREATE, DdlOperation.fromOracleCode(9));
        assertEquals(DdlOperation.ALTER, DdlOperation.fromOracleCode(15));
        assertEquals(DdlOperation.DROP, DdlOperation.fromOracleCode(12));
        assertEquals(DdlOperation.TRUNCATE, DdlOperation.fromOracleCode(85));
        assertEquals(DdlOperation.PURGE, DdlOperation.fromOracleCode(198));
        assertEquals(DdlOperation.OTHER, DdlOperation.fromOracleCode(28));
    }

    private static DdlSchemaChange change(int oracleType, String ddlText, long commitScn) {
        return new DdlSchemaChange(
                CONTAINER, OWNER, TABLE, oracleType, ddlText, Scn.of(commitScn));
    }

    private static RuntimeState runtimeState(long durableScn) {
        return new RuntimeState(
                1, 2, 3,
                new RedoPosition(
                        Scn.of(durableScn), 1, Seq.of(10), FileOffset.of(4096)),
                Optional.empty(),
                1,
                0,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                UPDATED_AT);
    }

    private static TableSchema tableSchema(long objectId, long dataObjectId) {
        ColumnSchema id = new ColumnSchema(
                1, -1, 1, 1, "ID", OracleColumnType.NUMBER,
                22, 10, 0, 0, 1, false, false, false,
                false, false, false, false, false, false);
        return new TableSchema(
                CONTAINER, OWNER, TABLE,
                objectId, dataObjectId, 12, 0, 0,
                List.of(id), List.of(), List.of());
    }
}
