/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.config.TableFilter;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;
import io.github.arlowen.redoreplicator.source.OracleContainer;
import io.github.arlowen.redoreplicator.source.OracleContainerCatalogReader;
import io.github.arlowen.redoreplicator.source.OracleContainerRegistry;
import io.github.arlowen.redoreplicator.source.OracleDatabaseContext;
import io.github.arlowen.redoreplicator.source.OracleDatabaseInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "oracle.test.root.url", matches = ".+")
class OracleContainerBootstrapIntegrationTest {

    @Test
    void loadsIndependentRootAndPdbDictionaryState() throws Exception {
        String url = System.getProperty("oracle.test.root.url");
        String username = System.getProperty("oracle.test.root.username");
        String password = System.getProperty("oracle.test.root.password");
        try (Connection connection = DriverManager.getConnection(
                url, username, password)) {
            OracleDatabaseContext context =
                    new OracleDatabaseInspector().inspect(connection);
            OracleContainerRegistry containers =
                    new OracleContainerCatalogReader().read(
                            connection, context);
            TableFilter filter = new TableFilter(
                    List.of(Pattern.compile(
                            "FREEPDB1\\.SYSTEM\\.CODEX_REDO_TEST")),
                    List.of());

            OracleCaptureBootstrap bootstrap =
                    new OracleContainerBootstrapLoader().load(
                            connection, context.containerName(), containers,
                            filter, context.currentScn(),
                            new TableSchemaJsonCodec());

            assertEquals(List.of("CDB$ROOT", "FREEPDB1"),
                    containers.containers().stream()
                            .map(container -> container.name())
                            .toList());
            assertEquals(1, bootstrap.initialSchemaVersions().size());
            assertEquals("FREEPDB1",
                    bootstrap.initialSchemaVersions().get(0).container());
            assertEquals("CODEX_REDO_TEST",
                    bootstrap.initialSchemaVersions().get(0).table());
            for (var container : containers.containers()) {
                var manager = bootstrap.systemTransactions().require(
                        container.id());
                assertNotNull(manager);
                if (!OracleContainer.ROOT_NAME.equals(container.name())) {
                    assertFalse(manager.dictionaryState().users().isEmpty());
                    assertFalse(manager.dictionaryState()
                            .tablespaces().isEmpty());
                }
            }
        }
    }
}
