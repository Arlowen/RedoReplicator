/*
 * Java translation derived from OpenLogReplicator reader positioning in
 * src/replicator/ReplicatorOnline.cpp and checkpoint recovery in
 * src/metadata/State.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.source.OracleDatabaseContext;
import io.github.arlowen.redoreplicator.source.OracleRedoCatalogPoller;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.StateStore;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RedoCaptureStartResolver {
    public RedoCaptureStart resolve(
            OracleRedoCatalogPoller catalogPoller,
            StateStore stateStore,
            OracleDatabaseContext databaseContext,
            Long configuredStartScn) throws SQLException {
        Objects.requireNonNull(catalogPoller, "catalogPoller");
        Objects.requireNonNull(stateStore, "stateStore");
        Objects.requireNonNull(databaseContext, "databaseContext");
        stateStore.validateDatabaseIdentity(databaseContext.identity());

        Optional<RuntimeState> persisted = stateStore.loadRuntimeState();
        if (persisted.isPresent()) {
            return resume(catalogPoller, persisted.orElseThrow());
        }

        Scn startScn = databaseContext.currentScn();
        if (configuredStartScn != null) {
            startScn = Scn.of(configuredStartScn);
        }
        List<OracleRedoLog> startLogs = catalogPoller.awaitStart(startScn);
        if (startLogs.size() != 1) {
            throw new ConfigurationException(10045,
                    "RedoReplicator supports one active redo thread; found "
                            + startLogs.size());
        }
        return new RedoCaptureStart(
                startScn, startLogs.get(0), FileOffset.zero(),
                Optional.empty());
    }

    private static RedoCaptureStart resume(
            OracleRedoCatalogPoller catalogPoller,
            RuntimeState runtimeState) throws SQLException {
        RedoPosition replayPosition = runtimeState.lowWatermarkPosition()
                .orElse(runtimeState.durablePosition());
        OracleRedoLog redoLog = catalogPoller.awaitNext(
                replayPosition.thread(), replayPosition.sequence());
        return new RedoCaptureStart(
                runtimeState.durablePosition().scn(),
                redoLog,
                replayPosition.offset(),
                Optional.of(runtimeState));
    }
}
