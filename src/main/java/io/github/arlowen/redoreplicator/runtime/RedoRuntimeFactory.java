/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.source.OracleRedoCatalogPoller;

import java.sql.Connection;
import java.util.Objects;

public final class RedoRuntimeFactory {
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
}
