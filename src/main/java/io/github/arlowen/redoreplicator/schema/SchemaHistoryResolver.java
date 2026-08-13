/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.StateStore;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public final class SchemaHistoryResolver {
    private static final Logger log = LoggerFactory.getLogger(SchemaHistoryResolver.class);
    private static final int ORA_SNAPSHOT_TOO_OLD = 1555;
    private static final int ORA_NO_SNAPSHOT_FOUND = 8180;
    private static final int ORA_INVALID_LOWER_SNAPSHOT = 30052;

    private final StateStore stateStore;
    private final TableSchemaLoader flashbackLoader;
    private final SchemaRedoRebuilder redoRebuilder;
    private final TableSchemaJsonCodec jsonCodec;

    public SchemaHistoryResolver(StateStore stateStore,
                                 TableSchemaLoader flashbackLoader,
                                 SchemaRedoRebuilder redoRebuilder,
                                 TableSchemaJsonCodec jsonCodec) {
        this.stateStore = stateStore;
        this.flashbackLoader = flashbackLoader;
        this.redoRebuilder = redoRebuilder;
        this.jsonCodec = jsonCodec;
    }

    public SchemaResolution resolve(Connection connection, String container,
                                    String owner, String table, Scn targetScn)
            throws IOException, SQLException {
        Optional<TableSchemaVersion> h2Version = stateStore.findSchemaAt(
                container, owner, table, targetScn);
        Optional<RuntimeState> runtimeState = stateStore.loadRuntimeState();
        if (h2Version.isPresent() && covers(runtimeState, targetScn)) {
            return fromH2(h2Version.get());
        }

        try {
            Optional<TableSchema> flashbackSchema = flashbackLoader.loadTable(
                    connection, owner, table, targetScn);
            if (flashbackSchema.isPresent()) {
                TableSchema schema = flashbackSchema.get();
                validateIdentity(schema, container, owner, table, "Oracle Flashback");
                TableSchemaVersion version = TableSchemaVersion.flashback(
                        schema, targetScn, jsonCodec);
                return SchemaResolution.present(version);
            }
            return SchemaResolution.notPresent(
                    Optional.empty(),
                    "Oracle Flashback proves that " + container + "." + owner + "."
                            + table + " did not exist at SCN " + targetScn);
        } catch (SQLException e) {
            if (!isUnavailableFlashback(e)) {
                String msg = "Failed to load Oracle Flashback schema at SCN " + targetScn;
                log.error(msg, e);
                throw e;
            }
            String msg = "Oracle Flashback schema is unavailable at SCN "
                    + targetScn + "; rebuilding from redo";
            log.error(msg, e);
        }

        SchemaResolution rebuilt = redoRebuilder.rebuild(
                container, owner, table, targetScn, h2Version);
        return rebuilt.requireProven();
    }

    private SchemaResolution fromH2(TableSchemaVersion version) throws IOException {
        if (version.dropTombstone()) {
            return SchemaResolution.notPresent(
                    Optional.of(version),
                    "H2 contains a committed drop tombstone at SCN "
                            + version.effectiveScn());
        }
        TableSchema schema = version.decode(jsonCodec);
        validateIdentity(
                schema, version.container(), version.owner(), version.table(), "H2");
        return SchemaResolution.present(version);
    }

    private static void validateIdentity(TableSchema schema, String container,
                                         String owner, String table, String source) {
        if (!container.equals(schema.container())
                || !owner.equals(schema.owner())
                || !table.equals(schema.name())) {
            throw new DataException(
                    50071,
                    source + " schema identity does not match "
                            + container + "." + owner + "." + table);
        }
    }

    private static boolean covers(Optional<RuntimeState> runtimeState, Scn targetScn) {
        if (runtimeState.isEmpty()) {
            return false;
        }
        return runtimeState.get().durablePosition().scn().compareTo(targetScn) >= 0;
    }

    private static boolean isUnavailableFlashback(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            int errorCode = current.getErrorCode();
            if (errorCode == ORA_SNAPSHOT_TOO_OLD
                    || errorCode == ORA_NO_SNAPSHOT_FOUND
                    || errorCode == ORA_INVALID_LOWER_SNAPSHOT) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }
}
