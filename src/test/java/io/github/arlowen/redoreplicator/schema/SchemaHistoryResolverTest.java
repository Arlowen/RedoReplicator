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
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaHistoryResolverTest {
    private static final String CONTAINER = "FREEPDB1";
    private static final String OWNER = "APP";
    private static final String TABLE = "ORDERS";
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.of(
            2026, 8, 13, 14, 0, 0, 0, ZoneOffset.ofHours(8));

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesCompleteH2HistoryWhenDurableStateCoversTarget() throws Exception {
        TableSchema schema = tableSchema(101, 102);
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
        TableSchemaVersion initial = TableSchemaVersion.initial(schema, Scn.of(100), codec);

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(runtimeState(200), List.of(initial));
            SchemaHistoryResolver resolver = new SchemaHistoryResolver(
                    database.store(),
                    (connection, owner, table, targetScn) -> {
                        throw new AssertionError("Flashback must not run for covered H2 history");
                    },
                    (container, owner, table, targetScn, baseVersion) -> {
                        throw new AssertionError("Redo must not run for covered H2 history");
                    },
                    codec);

            SchemaResolution resolution = resolver.resolve(
                    null, CONTAINER, OWNER, TABLE, Scn.of(150));

            assertEquals(SchemaResolutionStatus.PRESENT, resolution.status());
            assertEquals(initial, resolution.version().orElseThrow());
        }
    }

    @Test
    void returnsCommittedDropTombstoneFromH2() throws Exception {
        TableSchema schema = tableSchema(101, 102);
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
        TableSchemaVersion tombstone = new TableSchemaVersion(
                CONTAINER, OWNER, TABLE, 101, 102, Scn.of(140),
                codec.write(schema), "DROP", "DROP TABLE APP.ORDERS",
                SchemaSource.REDO, true);

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(runtimeState(200), List.of(tombstone));
            SchemaHistoryResolver resolver = new SchemaHistoryResolver(
                    database.store(),
                    (connection, owner, table, targetScn) -> Optional.empty(),
                    (container, owner, table, targetScn, baseVersion) ->
                            SchemaResolution.unproven("should not run"),
                    codec);

            SchemaResolution resolution = resolver.resolve(
                    null, CONTAINER, OWNER, TABLE, Scn.of(150));

            assertEquals(SchemaResolutionStatus.NOT_PRESENT, resolution.status());
            assertEquals(tombstone, resolution.version().orElseThrow());
        }
    }

    @Test
    void usesFlashbackWhenH2DoesNotCoverTarget() throws Exception {
        TableSchema oldSchema = tableSchema(101, 102);
        TableSchema currentSchema = tableSchema(201, 202);
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
        TableSchemaVersion initial = TableSchemaVersion.initial(
                oldSchema, Scn.of(100), codec);

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(runtimeState(120), List.of(initial));
            SchemaHistoryResolver resolver = new SchemaHistoryResolver(
                    database.store(),
                    (connection, owner, table, targetScn) -> Optional.of(currentSchema),
                    (container, owner, table, targetScn, baseVersion) ->
                            SchemaResolution.unproven("should not run"),
                    codec);

            SchemaResolution resolution = resolver.resolve(
                    null, CONTAINER, OWNER, TABLE, Scn.of(150));

            TableSchemaVersion version = resolution.version().orElseThrow();
            assertEquals(SchemaResolutionStatus.PRESENT, resolution.status());
            assertEquals(SchemaSource.FLASHBACK, version.source());
            assertEquals(Scn.of(150), version.effectiveScn());
            assertEquals(currentSchema, version.decode(codec));
        }
    }

    @Test
    void treatsSuccessfulEmptyFlashbackAsProvenAbsence() throws Exception {
        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            SchemaHistoryResolver resolver = new SchemaHistoryResolver(
                    database.store(),
                    (connection, owner, table, targetScn) -> Optional.empty(),
                    (container, owner, table, targetScn, baseVersion) ->
                            SchemaResolution.unproven("should not run"),
                    new TableSchemaJsonCodec());

            SchemaResolution resolution = resolver.resolve(
                    null, CONTAINER, OWNER, TABLE, Scn.of(80));

            assertEquals(SchemaResolutionStatus.NOT_PRESENT, resolution.status());
            assertFalse(resolution.version().isPresent());
        }
    }

    @Test
    void rejectsFlashbackSchemaFromAnotherContainer() throws Exception {
        TableSchema wrongContainer = new TableSchema(
                "OTHERPDB", OWNER, TABLE,
                101, 102, 12, 0, 0,
                tableSchema(101, 102).columns(), List.of(), List.of());
        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            SchemaHistoryResolver resolver = new SchemaHistoryResolver(
                    database.store(),
                    (connection, owner, table, targetScn) -> Optional.of(wrongContainer),
                    (container, owner, table, targetScn, baseVersion) ->
                            SchemaResolution.unproven("should not run"),
                    new TableSchemaJsonCodec());

            DataException exception = assertThrows(DataException.class,
                    () -> resolver.resolve(
                            null, CONTAINER, OWNER, TABLE, Scn.of(150)));

            assertTrue(exception.getMessage().contains("schema identity does not match"));
        }
    }

    @Test
    void fallsBackToRedoOnlyForUnavailableFlashbackHistory() throws Exception {
        TableSchema schema = tableSchema(101, 102);
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
        TableSchemaVersion base = TableSchemaVersion.initial(schema, Scn.of(100), codec);
        AtomicReference<Optional<TableSchemaVersion>> receivedBase = new AtomicReference<>();

        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            database.store().commitLwn(runtimeState(120), List.of(base));
            SchemaHistoryResolver resolver = new SchemaHistoryResolver(
                    database.store(),
                    (connection, owner, table, targetScn) -> {
                        throw new SQLException("snapshot too old", "72000", 1555);
                    },
                    (container, owner, table, targetScn, baseVersion) -> {
                        receivedBase.set(baseVersion);
                        return SchemaResolution.present(base);
                    },
                    codec);

            SchemaResolution resolution = resolver.resolve(
                    null, CONTAINER, OWNER, TABLE, Scn.of(150));

            assertEquals(SchemaResolutionStatus.PRESENT, resolution.status());
            assertEquals(Optional.of(base), receivedBase.get());
        }
    }

    @Test
    void rejectsUnprovenRedoReconstruction() throws Exception {
        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            SchemaHistoryResolver resolver = new SchemaHistoryResolver(
                    database.store(),
                    (connection, owner, table, targetScn) -> {
                        throw new SQLException("snapshot too old", "72000", 30052);
                    },
                    (container, owner, table, targetScn, baseVersion) ->
                            SchemaResolution.unproven(
                                    "Archive redo does not reach target SCN " + targetScn),
                    new TableSchemaJsonCodec());

            DataException exception = assertThrows(DataException.class,
                    () -> resolver.resolve(
                            null, CONTAINER, OWNER, TABLE, Scn.of(150)));

            assertEquals(50071, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("does not reach target SCN"));
        }
    }

    @Test
    void propagatesPrivilegeErrorsWithoutTryingRedo() throws Exception {
        AtomicBoolean redoCalled = new AtomicBoolean();
        try (StateDatabase database = StateDatabase.open(temporaryDirectory)) {
            SchemaHistoryResolver resolver = new SchemaHistoryResolver(
                    database.store(),
                    (connection, owner, table, targetScn) -> {
                        throw new SQLException("insufficient privileges", "42000", 1031);
                    },
                    (container, owner, table, targetScn, baseVersion) -> {
                        redoCalled.set(true);
                        return SchemaResolution.unproven("must not run");
                    },
                    new TableSchemaJsonCodec());

            SQLException exception = assertThrows(SQLException.class,
                    () -> resolver.resolve(
                            null, CONTAINER, OWNER, TABLE, Scn.of(150)));

            assertEquals(1031, exception.getErrorCode());
            assertFalse(redoCalled.get());
        }
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
