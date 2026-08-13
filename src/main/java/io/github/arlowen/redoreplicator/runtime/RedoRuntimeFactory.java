/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.output.JsonlFileWriter;
import io.github.arlowen.redoreplicator.output.JsonlPosition;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.source.OracleDatabaseContext;
import io.github.arlowen.redoreplicator.source.OracleRedoCatalogPoller;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import io.github.arlowen.redoreplicator.state.StateStore;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

public final class RedoRuntimeFactory {
    private static final int DEFAULT_REDO_READ_BLOCKS = 128;
    private static final long BYTES_PER_MEBIBYTE = 1_048_576;

    public RedoTransactionBuffer openTransactionBuffer(
            ResolvedConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new RedoTransactionBuffer(
                configuration.transactionSpillDirectory(),
                configuration.transactionMemoryBytes());
    }

    public OracleRedoCatalogPoller openRedoCatalogPoller(
            Connection connection, ResolvedConfiguration configuration) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(configuration, "configuration");
        return new OracleRedoCatalogPoller(
                connection, configuration, new SystemClock());
    }

    public RedoThreadStream openRedoThreadStream(
            OracleRedoCatalogPoller catalogPoller,
            OracleRedoLog startLog,
            DatabaseIdentity databaseIdentity,
            Scn captureStartScn,
            FileOffset startOffset,
            RedoTransactionBuffer transactionBuffer) {
        return new RedoThreadStream(
                catalogPoller, startLog, databaseIdentity,
                captureStartScn, startOffset, transactionBuffer,
                DEFAULT_REDO_READ_BLOCKS, true);
    }

    public RedoThreadStream openRedoThreadStream(
            OracleRedoCatalogPoller catalogPoller,
            RedoCaptureStart captureStart,
            DatabaseIdentity databaseIdentity,
            RedoTransactionBuffer transactionBuffer) {
        Objects.requireNonNull(captureStart, "captureStart");
        return openRedoThreadStream(
                catalogPoller, captureStart.redoLog(), databaseIdentity,
                captureStart.captureStartScn(), captureStart.fileOffset(),
                transactionBuffer);
    }

    public RedoCaptureStart resolveCaptureStart(
            OracleRedoCatalogPoller catalogPoller,
            StateStore stateStore,
            OracleDatabaseContext databaseContext,
            ResolvedConfiguration configuration) throws SQLException {
        return new RedoCaptureStartResolver().resolve(
                catalogPoller, stateStore, databaseContext,
                configuration.configuration().capture().startScn());
    }

    public JsonlFileWriter openJsonlWriter(
            ResolvedConfiguration configuration,
            StateStore stateStore) throws IOException, SQLException {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(stateStore, "stateStore");
        long maximumFileBytes = Math.multiplyExact(
                configuration.configuration().output().maxFileSizeMb(),
                BYTES_PER_MEBIBYTE);
        Optional<JsonlPosition> durablePosition = stateStore.loadRuntimeState()
                .map(state -> new JsonlPosition(
                        state.jsonlFileNumber(),
                        state.jsonlFsyncOffset()));
        return JsonlFileWriter.open(
                configuration.outputDirectory(), maximumFileBytes,
                durablePosition);
    }
}
