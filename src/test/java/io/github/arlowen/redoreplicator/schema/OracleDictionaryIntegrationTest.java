/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "oracle.test.url", matches = ".+")
class OracleDictionaryIntegrationTest {
    private static final String TABLE_NAME = "REDO_DICTIONARY_TEST";

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsFlashbackDictionaryAndPersistsCompleteH2Version() throws Exception {
        String url = System.getProperty("oracle.test.url");
        String username = System.getProperty("oracle.test.username");
        String password = System.getProperty("oracle.test.password");
        String owner = System.getProperty("oracle.test.owner", "APP");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            OracleDatabaseContext context = new OracleDatabaseInspector().inspect(connection);
            context.validateSupportedSource();
            Optional<TableSchema> loaded = new OracleDictionaryReader().loadTable(
                    connection, owner, TABLE_NAME, context.currentScn());

            assertTrue(loaded.isPresent());
            TableSchema schema = loaded.orElseThrow();
            assertEquals(context.containerName(), schema.container());
            assertEquals(owner, schema.owner());
            assertEquals(TABLE_NAME, schema.name());
            assertEquals(List.of(0, 1), schema.primaryKeyColumnIndexes());
            assertTrue(schema.columns().stream().anyMatch(
                    column -> "SYS_VERSION".equals(column.name()) && column.hidden()));
            assertEquals(1, schema.lobs().size());
            assertFalse(schema.lobs().get(0).indexDataObjectIds().isEmpty());

            persistInitialSchema(context, schema);
        }
    }

    private void persistInitialSchema(OracleDatabaseContext context, TableSchema schema)
            throws Exception {
        TableSchemaJsonCodec codec = new TableSchemaJsonCodec();
        TableSchemaVersion initial = TableSchemaVersion.initial(
                schema, context.currentScn(), codec);
        RuntimeState state = new RuntimeState(
                context.identity().databaseId(),
                context.identity().incarnation(),
                context.identity().resetlogsId(),
                new RedoPosition(context.currentScn(), 1, Seq.of(1), FileOffset.of(0)),
                Optional.empty(),
                1,
                0,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                OffsetDateTime.now());

        try (StateDatabase stateDatabase = StateDatabase.open(temporaryDirectory)) {
            stateDatabase.store().validateDatabaseIdentity(context.identity());
            stateDatabase.store().commitLwn(state, List.of(initial));
            TableSchema persisted = stateDatabase.store().findSchemaAt(
                    schema.container(), schema.owner(), schema.name(), context.currentScn())
                    .orElseThrow()
                    .decode(codec);
            assertEquals(schema, persisted);
        }
    }

}
