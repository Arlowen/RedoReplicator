/*
 * Java translation derived from OpenLogReplicator per-container dictionary
 * bootstrap in src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.config.TableFilter;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.schema.InitialSchemaSnapshot;
import io.github.arlowen.redoreplicator.schema.OracleDictionaryReader;
import io.github.arlowen.redoreplicator.schema.OracleInitialSchemaLoader;
import io.github.arlowen.redoreplicator.schema.OracleSystemDictionaryReader;
import io.github.arlowen.redoreplicator.schema.OracleSystemSchemaCatalogLoader;
import io.github.arlowen.redoreplicator.schema.OracleTableCatalogReader;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryState;
import io.github.arlowen.redoreplicator.schema.SystemTransactionManager;
import io.github.arlowen.redoreplicator.schema.SystemTransactionRegistry;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;
import io.github.arlowen.redoreplicator.source.OracleContainer;
import io.github.arlowen.redoreplicator.source.OracleContainerRegistry;
import io.github.arlowen.redoreplicator.source.OracleContainerSession;
import io.github.arlowen.redoreplicator.source.OracleRuntimeProperties;
import io.github.arlowen.redoreplicator.source.OracleRuntimePropertiesReader;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OracleContainerBootstrapLoader {
    private final OracleRuntimePropertiesReader propertiesReader;
    private final OracleTableCatalogReader tableCatalogReader;
    private final OracleContainerSession containerSession;

    public OracleContainerBootstrapLoader() {
        propertiesReader = new OracleRuntimePropertiesReader();
        tableCatalogReader = new OracleTableCatalogReader();
        containerSession = new OracleContainerSession();
    }

    public OracleCaptureBootstrap load(
            Connection connection,
            String initialContainer,
            OracleContainerRegistry containerRegistry,
            TableFilter tableFilter,
            Scn dictionaryScn,
            TableSchemaJsonCodec jsonCodec)
            throws IOException, SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(initialContainer, "initialContainer");
        Objects.requireNonNull(containerRegistry, "containerRegistry");
        Objects.requireNonNull(tableFilter, "tableFilter");
        Objects.requireNonNull(dictionaryScn, "dictionaryScn");
        Objects.requireNonNull(jsonCodec, "jsonCodec");

        SchemaCatalog staticCatalog = new SchemaCatalog();
        List<TableSchemaVersion> initialVersions = new ArrayList<>();
        Map<Integer, SystemTransactionManager> systemManagers =
                new LinkedHashMap<>();
        OracleRuntimeProperties runtimeProperties = null;
        String currentContainer = initialContainer;
        try {
            for (OracleContainer container : containerRegistry.containers()) {
                if (!container.name().equals(currentContainer)) {
                    containerSession.switchTo(connection, container.name());
                    currentContainer = container.name();
                }
                OracleRuntimeProperties containerProperties =
                        propertiesReader.read(connection);
                if (runtimeProperties == null) {
                    runtimeProperties = containerProperties;
                }
                InitialSchemaSnapshot initialSchema;
                if (OracleContainer.ROOT_NAME.equals(container.name())) {
                    initialSchema = new InitialSchemaSnapshot(
                            new SchemaCatalog(),
                            SystemDictionaryState.empty(),
                            List.of());
                } else {
                    initialSchema = loadContainer(
                            connection, container.name(), tableFilter,
                            dictionaryScn, jsonCodec, staticCatalog);
                }
                initialVersions.addAll(initialSchema.schemaVersions());
                systemManagers.put(container.id(),
                        new SystemTransactionManager(
                                initialSchema.dictionaryState(),
                                container.name(),
                                containerProperties.databaseCharacterSetId(),
                                containerProperties.nationalCharacterSetId(),
                                containerProperties.databaseCharacterSet(),
                                jsonCodec));
            }
        } finally {
            if (!currentContainer.equals(initialContainer)) {
                containerSession.switchTo(connection, initialContainer);
            }
        }
        return new OracleCaptureBootstrap(
                runtimeProperties, staticCatalog, initialVersions,
                new SystemTransactionRegistry(systemManagers));
    }

    private InitialSchemaSnapshot loadContainer(
            Connection connection,
            String container,
            TableFilter tableFilter,
            Scn dictionaryScn,
            TableSchemaJsonCodec jsonCodec,
            SchemaCatalog staticCatalog) throws IOException, SQLException {
        SchemaCatalog identityCatalog = tableCatalogReader.load(
                connection, container, dictionaryScn);
        OracleDictionaryReader dictionaryReader = new OracleDictionaryReader();
        InitialSchemaSnapshot initialSchema = new OracleInitialSchemaLoader(
                dictionaryReader, new OracleSystemDictionaryReader(),
                jsonCodec).load(connection, identityCatalog, tableFilter,
                dictionaryScn);
        SchemaCatalog containerCatalog = identityCatalog.copy();
        for (TableSchemaVersion version : initialSchema.schemaVersions()) {
            containerCatalog.remove(
                    version.container(), version.owner(), version.table());
        }
        containerCatalog.addAll(new OracleSystemSchemaCatalogLoader(
                dictionaryReader).load(connection, dictionaryScn));
        staticCatalog.addAll(containerCatalog);
        return initialSchema;
    }
}
