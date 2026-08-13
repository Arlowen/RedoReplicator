/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.SystemTransactionRegistry;
import io.github.arlowen.redoreplicator.source.OracleRuntimeProperties;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.util.List;
import java.util.Objects;

public record OracleCaptureBootstrap(
        OracleRuntimeProperties runtimeProperties,
        SchemaCatalog staticSchemaCatalog,
        List<TableSchemaVersion> initialSchemaVersions,
        SystemTransactionRegistry systemTransactions) {
    public OracleCaptureBootstrap {
        Objects.requireNonNull(runtimeProperties, "runtimeProperties");
        staticSchemaCatalog = Objects.requireNonNull(
                staticSchemaCatalog, "staticSchemaCatalog").copy();
        initialSchemaVersions = List.copyOf(initialSchemaVersions);
        Objects.requireNonNull(systemTransactions, "systemTransactions");
    }
}
