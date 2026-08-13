/*
 * Java translation derived from OpenLogReplicator online archive positioning
 * in src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.schema.InitialSchemaSnapshot;
import io.github.arlowen.redoreplicator.schema.OracleDictionaryReader;
import io.github.arlowen.redoreplicator.schema.OracleInitialSchemaLoader;
import io.github.arlowen.redoreplicator.schema.OracleSystemDictionaryReader;
import io.github.arlowen.redoreplicator.schema.OracleTableCatalogReader;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RewindSourceValidation;
import io.github.arlowen.redoreplicator.state.RewindSourceValidator;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class OracleRewindSourceValidator
        implements RewindSourceValidator {
    private final OracleRedoCatalogReader catalogReader;
    private final RedoLogPlanner logPlanner;
    private final OracleTableCatalogReader tableCatalogReader;

    public OracleRewindSourceValidator() {
        catalogReader = new OracleRedoCatalogReader();
        logPlanner = new RedoLogPlanner();
        tableCatalogReader = new OracleTableCatalogReader();
    }

    @Override
    public RewindSourceValidation validate(
            Connection connection,
            ResolvedConfiguration configuration,
            OracleDatabaseContext databaseContext,
            Scn targetScn) throws IOException, SQLException {
        OracleRedoCatalog catalog = catalogReader.read(
                connection, configuration.redoPathMapper());
        if (!catalog.databaseContext().identity().equals(
                databaseContext.identity())) {
            throw new ConfigurationException(
                    10001, "Oracle database identity changed during rewind validation");
        }
        List<OracleRedoLog> logs = logPlanner.locateStart(catalog, targetScn)
                .orElseThrow(() -> new ConfigurationException(
                        10039, "Redo file covering rewind SCN "
                        + targetScn + " is not readable"));
        if (logs.size() != 1) {
            throw new ConfigurationException(10045,
                    "RedoReplicator supports one active redo thread; found "
                            + logs.size());
        }
        OracleRedoLog log = logs.get(0);
        validateContinuousRedo(catalog, log);

        SchemaCatalog identities = tableCatalogReader.load(
                connection, databaseContext.containerName(), targetScn);
        OracleInitialSchemaLoader schemaLoader = new OracleInitialSchemaLoader(
                new OracleDictionaryReader(),
                new OracleSystemDictionaryReader(),
                new TableSchemaJsonCodec());
        InitialSchemaSnapshot snapshot = schemaLoader.load(
                connection, identities, configuration.tableFilter(), targetScn);
        RedoPosition position = new RedoPosition(
                targetScn, log.thread(), log.sequence(), FileOffset.zero());
        return new RewindSourceValidation(
                position, snapshot.schemaVersions());
    }

    private void validateContinuousRedo(
            OracleRedoCatalog catalog,
            OracleRedoLog startLog) {
        OracleRedoLog current = startLog;
        Scn currentScn = catalog.databaseContext().currentScn();
        while (!current.covers(currentScn)) {
            int thread = current.thread();
            var nextSequence = current.sequence().next();
            current = logPlanner.locateNext(
                            catalog, thread, nextSequence)
                    .orElseThrow(() -> new ConfigurationException(
                            10039, "Redo/archive chain from rewind SCN is not readable; "
                            + "missing thread " + thread
                            + " sequence " + nextSequence));
        }
    }
}
