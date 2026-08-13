/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.source.OracleDatabaseContext;
import io.github.arlowen.redoreplicator.source.OracleDatabaseInspector;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.StateDatabase;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "oracle.test.ddl.username", matches = ".+")
class OracleDdlSchemaHistoryIntegrationTest {
    private static final String TABLE = "REDO_DDL_HISTORY_TEST";

    @TempDir
    Path temporaryDirectory;

    @Test
    void recordsCreateAlterTruncateAndDropHistory() throws Exception {
        String url = System.getProperty("oracle.test.url");
        String captureUsername = System.getProperty("oracle.test.username");
        String capturePassword = System.getProperty("oracle.test.password");
        String ddlUsername = System.getProperty("oracle.test.ddl.username");
        String ddlPassword = System.getProperty("oracle.test.ddl.password");
        try (Connection capture = DriverManager.getConnection(
                     url, captureUsername, capturePassword);
             Connection ddl = DriverManager.getConnection(url, ddlUsername, ddlPassword);
             StateDatabase stateDatabase = StateDatabase.open(temporaryDirectory)) {
            dropTestTable(ddl);
            OracleDatabaseContext context = new OracleDatabaseInspector().inspect(capture);
            OracleDictionaryReader dictionaryReader = new OracleDictionaryReader();
            TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
            DdlSchemaVersionBuilder builder = new DdlSchemaVersionBuilder(
                    stateDatabase.store(), dictionaryReader, codec);

            try {
                execute(ddl, "CREATE TABLE " + TABLE + " (ID NUMBER PRIMARY KEY)");
                Scn createScn = currentScn(capture);
                TableSchemaVersion created = buildAndCommit(
                        stateDatabase, builder, capture, context,
                        new DdlSchemaChange(
                                context.containerName(), ddlUsername, TABLE, 1,
                                "CREATE TABLE " + TABLE + " (ID NUMBER PRIMARY KEY)",
                                createScn));
                TableSchema createdSchema = created.decode(codec);
                assertEquals(1, createdSchema.columns().size());
                SystemDictionaryState coreDictionary =
                        new OracleSystemDictionaryReader().loadCoreTable(
                                capture, ddlUsername, TABLE, createScn).orElseThrow();
                TableSchema replaySchema = new SystemDictionarySchemaAssembler().assemble(
                        coreDictionary,
                        context.containerName(),
                        created.objectId(),
                        0,
                        0).orElseThrow();
                assertEquals(createdSchema, replaySchema);
                assertTrue(coreDictionary.rows().stream()
                        .allMatch(row -> row.rowId().toString().length() == RowId.SIZE));

                execute(ddl, "ALTER TABLE " + TABLE + " ADD DESCRIPTION VARCHAR2(100)");
                Scn alterScn = currentScn(capture);
                TableSchemaVersion altered = buildAndCommit(
                        stateDatabase, builder, capture, context,
                        new DdlSchemaChange(
                                context.containerName(), ddlUsername, TABLE, 15,
                                "ALTER TABLE " + TABLE + " ADD DESCRIPTION VARCHAR2(100)",
                                alterScn));
                TableSchema alteredSchema = altered.decode(codec);
                assertEquals(2, alteredSchema.columns().size());
                assertEquals("DESCRIPTION", alteredSchema.columns().get(1).name());

                execute(ddl, "TRUNCATE TABLE " + TABLE);
                Scn truncateScn = currentScn(capture);
                TableSchemaVersion truncated = buildAndCommit(
                        stateDatabase, builder, capture, context,
                        new DdlSchemaChange(
                                context.containerName(), ddlUsername, TABLE, 85,
                                "TRUNCATE TABLE " + TABLE,
                                truncateScn));
                assertEquals("TRUNCATE", truncated.ddlType());
                assertEquals(truncateScn, truncated.effectiveScn());
                assertEquals(alteredSchema.columns(), truncated.decode(codec).columns());

                execute(ddl, "DROP TABLE " + TABLE);
                Scn dropScn = currentScn(capture);
                TableSchemaVersion dropped = buildAndCommit(
                        stateDatabase, builder, capture, context,
                        new DdlSchemaChange(
                                context.containerName(), ddlUsername, TABLE, 12,
                                "DROP TABLE " + TABLE,
                                dropScn));
                assertTrue(dropped.dropTombstone());
                assertEquals(truncated.schemaJson(), dropped.schemaJson());

                SchemaHistoryResolver resolver = new SchemaHistoryResolver(
                        stateDatabase.store(), dictionaryReader,
                        (container, owner, table, targetScn, baseVersion) ->
                                SchemaResolution.unproven("Redo fallback must not run"),
                        codec);
                assertEquals(SchemaResolutionStatus.PRESENT,
                        resolver.resolve(
                                capture, context.containerName(), ddlUsername,
                                TABLE, truncateScn).status());
                assertEquals(SchemaResolutionStatus.NOT_PRESENT,
                        resolver.resolve(
                                capture, context.containerName(), ddlUsername,
                                TABLE, dropScn).status());
            } finally {
                dropTestTable(ddl);
            }
        }
    }

    private static TableSchemaVersion buildAndCommit(
            StateDatabase stateDatabase,
            DdlSchemaVersionBuilder builder,
            Connection capture,
            OracleDatabaseContext context,
            DdlSchemaChange change) throws Exception {
        List<TableSchemaVersion> versions = builder.build(capture, change);
        assertEquals(1, versions.size());
        stateDatabase.store().commitLwn(runtimeState(context, change.commitScn()), versions);
        return versions.get(0);
    }

    private static RuntimeState runtimeState(OracleDatabaseContext context, Scn scn) {
        return new RuntimeState(
                context.identity().databaseId(),
                context.identity().incarnation(),
                context.identity().resetlogsId(),
                new RedoPosition(scn, 1, Seq.of(1), FileOffset.of(0)),
                Optional.empty(),
                1,
                0,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                OffsetDateTime.now());
    }

    private static Scn currentScn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT CURRENT_SCN FROM SYS.V_$DATABASE")) {
            resultSet.next();
            return Scn.of(resultSet.getLong(1));
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void dropTestTable(Connection connection) throws SQLException {
        boolean exists;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = '" + TABLE + "'")) {
            resultSet.next();
            exists = resultSet.getInt(1) > 0;
        }
        if (exists) {
            execute(connection, "DROP TABLE " + TABLE + " PURGE");
        }
    }
}
