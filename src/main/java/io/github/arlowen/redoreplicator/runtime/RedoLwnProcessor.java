/*
 * Java translation derived from OpenLogReplicator parser checkpoint and
 * writer confirmation ordering.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.output.AssembledRedoTransaction;
import io.github.arlowen.redoreplicator.output.BuilderJson;
import io.github.arlowen.redoreplicator.output.JsonlFileWriter;
import io.github.arlowen.redoreplicator.output.JsonlPosition;
import io.github.arlowen.redoreplicator.output.RedoJsonChangeAssembler;
import io.github.arlowen.redoreplicator.redo.parser.ParsedLwn;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.SchemaCatalogLoader;
import io.github.arlowen.redoreplicator.schema.SystemTransactionManager;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.StateStore;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

public final class RedoLwnProcessor {
    private final DatabaseIdentity databaseIdentity;
    private final String configFingerprint;
    private final boolean checkpointHeartbeat;
    private final StateStore stateStore;
    private final SchemaCatalogLoader schemaCatalogLoader;
    private final SchemaCatalog systemSchemaCatalog;
    private final IntFunction<SystemTransactionManager>
            systemTransactionResolver;
    private final RedoJsonChangeAssembler changeAssembler;
    private final BuilderJson builderJson;
    private final JsonlFileWriter jsonlWriter;
    private final TableSchemaJsonCodec tableSchemaJsonCodec;
    private final Clock clock;

    public RedoLwnProcessor(
            DatabaseIdentity databaseIdentity,
            String configFingerprint,
            boolean checkpointHeartbeat,
            StateStore stateStore,
            SchemaCatalogLoader schemaCatalogLoader,
            SchemaCatalog systemSchemaCatalog,
            SystemTransactionManager systemTransactionManager,
            RedoJsonChangeAssembler changeAssembler,
            BuilderJson builderJson,
            JsonlFileWriter jsonlWriter,
            TableSchemaJsonCodec tableSchemaJsonCodec,
            Clock clock) {
        this(databaseIdentity, configFingerprint, checkpointHeartbeat,
                stateStore, schemaCatalogLoader, systemSchemaCatalog,
                ignored -> systemTransactionManager, changeAssembler,
                builderJson, jsonlWriter, tableSchemaJsonCodec, clock);
    }

    public RedoLwnProcessor(
            DatabaseIdentity databaseIdentity,
            String configFingerprint,
            boolean checkpointHeartbeat,
            StateStore stateStore,
            SchemaCatalogLoader schemaCatalogLoader,
            SchemaCatalog systemSchemaCatalog,
            IntFunction<SystemTransactionManager> systemTransactionResolver,
            RedoJsonChangeAssembler changeAssembler,
            BuilderJson builderJson,
            JsonlFileWriter jsonlWriter,
            TableSchemaJsonCodec tableSchemaJsonCodec,
            Clock clock) {
        this.databaseIdentity = Objects.requireNonNull(
                databaseIdentity, "databaseIdentity");
        this.configFingerprint = Objects.requireNonNull(
                configFingerprint, "configFingerprint");
        this.checkpointHeartbeat = checkpointHeartbeat;
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.schemaCatalogLoader = Objects.requireNonNull(
                schemaCatalogLoader, "schemaCatalogLoader");
        this.systemSchemaCatalog = Objects.requireNonNull(
                systemSchemaCatalog, "systemSchemaCatalog").copy();
        this.systemTransactionResolver = Objects.requireNonNull(
                systemTransactionResolver, "systemTransactionResolver");
        this.changeAssembler = Objects.requireNonNull(
                changeAssembler, "changeAssembler");
        this.builderJson = Objects.requireNonNull(builderJson, "builderJson");
        this.jsonlWriter = Objects.requireNonNull(jsonlWriter, "jsonlWriter");
        this.tableSchemaJsonCodec = Objects.requireNonNull(
                tableSchemaJsonCodec, "tableSchemaJsonCodec");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RuntimeState process(ParsedLwn lwn)
            throws IOException, SQLException {
        Objects.requireNonNull(lwn, "lwn");
        SchemaCatalog catalog = schemaCatalogLoader.loadAt(
                lwn.position().scn());
        catalog.addAllMissing(systemSchemaCatalog);
        List<byte[]> messages = new ArrayList<>();
        List<TableSchemaVersion> schemaVersions = new ArrayList<>();

        for (CommittedRedoTransaction transaction
                : lwn.committedTransactions()) {
            SchemaCatalog previousCatalog = catalog.copy();
            AssembledRedoTransaction assembled =
                    changeAssembler.assembleCommitted(
                            transaction, catalog, previousCatalog,
                            systemTransactionResolver.apply(
                                    transaction.containerId()));
            messages.addAll(builderJson.buildTransaction(
                    transaction, assembled.jsonChanges()));
            for (TableSchemaVersion version : assembled.schemaVersions()) {
                schemaVersions.add(version);
                if (version.dropTombstone()) {
                    catalog.remove(
                            version.container(), version.owner(),
                            version.table());
                } else {
                    catalog.replace(version.decode(tableSchemaJsonCodec));
                }
            }
        }
        if (checkpointHeartbeat) {
            messages.add(builderJson.buildCheckpoint(
                    lwn.position().scn(), lwn.timestamp(),
                    lwn.position().sequence(), lwn.position().offset(), true));
        }

        JsonlPosition jsonlPosition = jsonlWriter.writeAndSync(messages);
        RuntimeState runtimeState = new RuntimeState(
                databaseIdentity.databaseId(), databaseIdentity.incarnation(),
                databaseIdentity.resetlogsId(), lwn.position(),
                lwn.lowWatermarkPosition(), jsonlPosition.fileNumber(),
                jsonlPosition.fsyncOffset(), configFingerprint,
                OffsetDateTime.now(clock));
        stateStore.commitLwn(runtimeState, schemaVersions);
        return runtimeState;
    }
}
