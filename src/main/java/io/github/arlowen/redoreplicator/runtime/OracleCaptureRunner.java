/*
 * Java translation derived from OpenLogReplicator online capture bootstrap
 * and lifecycle in src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.config.ConfigurationFingerprint;
import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.output.BuilderJson;
import io.github.arlowen.redoreplicator.output.JsonlFileWriter;
import io.github.arlowen.redoreplicator.output.JsonlPosition;
import io.github.arlowen.redoreplicator.output.OracleJsonValueDecoder;
import io.github.arlowen.redoreplicator.output.RedoJsonChangeAssembler;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.schema.InitialSchemaSnapshot;
import io.github.arlowen.redoreplicator.schema.OracleDictionaryReader;
import io.github.arlowen.redoreplicator.schema.OracleInitialSchemaLoader;
import io.github.arlowen.redoreplicator.schema.OracleSystemDictionaryReader;
import io.github.arlowen.redoreplicator.schema.OracleSystemSchemaCatalogLoader;
import io.github.arlowen.redoreplicator.schema.OracleTableCatalogReader;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.SchemaCatalogLoader;
import io.github.arlowen.redoreplicator.schema.SystemTransactionManager;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;
import io.github.arlowen.redoreplicator.source.OracleRedoCatalogPoller;
import io.github.arlowen.redoreplicator.source.OracleRuntimeProperties;
import io.github.arlowen.redoreplicator.source.OracleRuntimePropertiesReader;
import io.github.arlowen.redoreplicator.source.OracleSourceValidation;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.StateDatabase;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class OracleCaptureRunner {
    private static final long ONLINE_IDLE_WAIT_MILLIS = 1_000;

    private final RedoRuntimeFactory runtimeFactory;
    private final OracleRuntimePropertiesReader propertiesReader;
    private final OracleTableCatalogReader tableCatalogReader;
    private final ConfigurationFingerprint configurationFingerprint;

    public OracleCaptureRunner() {
        runtimeFactory = new RedoRuntimeFactory();
        propertiesReader = new OracleRuntimePropertiesReader();
        tableCatalogReader = new OracleTableCatalogReader();
        configurationFingerprint = new ConfigurationFingerprint();
    }

    public void run(
            Connection connection,
            ResolvedConfiguration configuration,
            OracleSourceValidation source,
            StateDatabase stateDatabase,
            BooleanSupplier stopRequested) throws IOException, SQLException {
        OracleRedoCatalogPoller catalogPoller =
                runtimeFactory.openRedoCatalogPoller(connection, configuration);
        RedoCaptureStart captureStart = runtimeFactory.resolveCaptureStart(
                catalogPoller, stateDatabase.store(),
                source.databaseContext(), configuration);
        Scn dictionaryScn = captureStart.recoveredState()
                .flatMap(RuntimeState::lowWatermarkPosition)
                .map(RedoPosition::scn)
                .orElse(captureStart.captureStartScn());
        OracleRuntimeProperties properties = propertiesReader.read(connection);
        TableSchemaJsonCodec jsonCodec = new TableSchemaJsonCodec();
        SchemaCatalog identityCatalog = tableCatalogReader.load(
                connection, source.databaseContext().containerName(),
                dictionaryScn);
        OracleInitialSchemaLoader initialSchemaLoader =
                new OracleInitialSchemaLoader(
                        new OracleDictionaryReader(),
                        new OracleSystemDictionaryReader(), jsonCodec);
        InitialSchemaSnapshot initialSchema = initialSchemaLoader.load(
                connection, identityCatalog, configuration.tableFilter(),
                dictionaryScn);
        SchemaCatalog staticCatalog = identityCatalog.copy();
        for (var version : initialSchema.schemaVersions()) {
            staticCatalog.remove(
                    version.container(), version.owner(), version.table());
        }
        SchemaCatalog systemCatalog =
                new OracleSystemSchemaCatalogLoader(
                        new OracleDictionaryReader()).load(
                        connection, dictionaryScn);
        staticCatalog.addAll(systemCatalog);

        String fingerprint = configurationFingerprint.calculate(configuration);
        try (JsonlFileWriter writer = runtimeFactory.openJsonlWriter(
                configuration, stateDatabase.store());
             RedoTransactionBuffer transactionBuffer =
                     runtimeFactory.openTransactionBuffer(configuration);
             RedoThreadStream stream = runtimeFactory.openRedoThreadStream(
                     catalogPoller, captureStart,
                     source.databaseContext().identity(), transactionBuffer)) {
            if (captureStart.recoveredState().isEmpty()) {
                JsonlPosition outputPosition = writer.position();
                RedoPosition initialPosition = new RedoPosition(
                        captureStart.captureStartScn(),
                        captureStart.redoLog().thread(),
                        captureStart.redoLog().sequence(),
                        captureStart.fileOffset());
                RuntimeState initialState = new RuntimeState(
                        source.databaseContext().identity().databaseId(),
                        source.databaseContext().identity().incarnation(),
                        source.databaseContext().identity().resetlogsId(),
                        initialPosition, Optional.empty(),
                        outputPosition.fileNumber(),
                        outputPosition.fsyncOffset(), fingerprint,
                        OffsetDateTime.now(Clock.systemUTC()));
                stateDatabase.store().commitLwn(
                        initialState, initialSchema.schemaVersions());
            }

            SystemTransactionManager systemTransactionManager =
                    new SystemTransactionManager(
                            initialSchema.dictionaryState(),
                            source.databaseContext().containerName(),
                            properties.databaseCharacterSetId(),
                            properties.nationalCharacterSetId(),
                            properties.databaseCharacterSet(), jsonCodec);
            RedoJsonChangeAssembler changeAssembler =
                    new RedoJsonChangeAssembler(
                            stream.openHeader().orElseThrow(() ->
                                    new RedoRuntimeException(10041,
                                            "redo header is not ready: "
                                                    + captureStart.redoLog()
                                                    .localPath()))
                                    .byteOrder(),
                            properties.databaseCharacterSet(),
                            configuration.tableFilter()::matches);
            long hostTimezoneSeconds = ZoneId.systemDefault().getRules()
                    .getOffset(Instant.now()).getTotalSeconds();
            BuilderJson builder = new BuilderJson(
                    new OracleJsonValueDecoder(
                            properties.databaseCharacterSet(),
                            properties.databaseTimeZone()),
                    source.databaseContext().containerName(),
                    hostTimezoneSeconds);
            RedoLwnProcessor lwnProcessor = new RedoLwnProcessor(
                    source.databaseContext().identity(), fingerprint,
                    configuration.configuration().output()
                            .checkpointHeartbeat(),
                    stateDatabase.store(),
                    new SchemaCatalogLoader(
                            stateDatabase.store(), jsonCodec),
                    staticCatalog, systemTransactionManager,
                    changeAssembler, builder, writer, jsonCodec,
                    Clock.systemUTC());
            RedoCaptureLoop captureLoop = new RedoCaptureLoop(
                    stream::read, lwnProcessor::process,
                    stopRequested, ONLINE_IDLE_WAIT_MILLIS);
            captureLoop.run();
        }
    }
}
