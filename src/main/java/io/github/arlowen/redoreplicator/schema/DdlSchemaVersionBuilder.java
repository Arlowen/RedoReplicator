/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.state.SchemaSource;
import io.github.arlowen.redoreplicator.state.StateStore;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DdlSchemaVersionBuilder {
    private final StateStore stateStore;
    private final TableSchemaLoader flashbackLoader;
    private final TableSchemaJsonCodec jsonCodec;

    public DdlSchemaVersionBuilder(StateStore stateStore,
                                   TableSchemaLoader flashbackLoader,
                                   TableSchemaJsonCodec jsonCodec) {
        this.stateStore = stateStore;
        this.flashbackLoader = flashbackLoader;
        this.jsonCodec = jsonCodec;
    }

    public List<TableSchemaVersion> build(Connection connection,
                                          DdlSchemaChange change)
            throws IOException, SQLException {
        Optional<TableSchema> committed = flashbackLoader.loadTable(
                connection, change.owner(), change.table(), change.commitScn());
        if (committed.isPresent()) {
            TableSchema schema = committed.get();
            validateIdentity(schema, change);
            return List.of(TableSchemaVersion.ddl(
                    schema,
                    change.commitScn(),
                    change.operation().name(),
                    change.ddlText(),
                    SchemaSource.FLASHBACK,
                    jsonCodec));
        }

        DdlOperation operation = change.operation();
        if (operation != DdlOperation.DROP && operation != DdlOperation.PURGE) {
            throw new DataException(50071,
                    "DDL committed at SCN " + change.commitScn()
                            + " but the resulting schema cannot be proved for "
                            + change.qualifiedName());
        }

        return buildDropVersions(connection, change, operation);
    }

    private List<TableSchemaVersion> buildDropVersions(
            Connection connection, DdlSchemaChange change, DdlOperation operation)
            throws IOException, SQLException {
        Optional<TableSchemaVersion> previous = stateStore.findSchemaAt(
                change.container(), change.owner(), change.table(), change.commitScn());
        if (previous.isPresent() && previous.get().dropTombstone()) {
            return List.of();
        }

        List<TableSchemaVersion> versions = new ArrayList<>();
        TableSchemaVersion base;
        if (previous.isPresent()) {
            base = previous.get();
            validateIdentity(base.decode(jsonCodec), change);
        } else {
            Scn beforeCommit = immediatelyBefore(change.commitScn(), change.qualifiedName());
            Optional<TableSchema> beforeDrop = flashbackLoader.loadTable(
                    connection, change.owner(), change.table(), beforeCommit);
            if (beforeDrop.isEmpty()) {
                throw new DataException(50071,
                        "No complete schema exists before " + operation
                                + " of " + change.qualifiedName()
                                + " at SCN " + change.commitScn());
            }
            TableSchema schema = beforeDrop.get();
            validateIdentity(schema, change);
            base = TableSchemaVersion.flashback(schema, beforeCommit, jsonCodec);
            versions.add(base);
        }

        versions.add(TableSchemaVersion.drop(
                base,
                change.commitScn(),
                operation.name(),
                change.ddlText(),
                SchemaSource.FLASHBACK));
        return List.copyOf(versions);
    }

    private static void validateIdentity(TableSchema schema, DdlSchemaChange change) {
        if (!change.container().equals(schema.container())
                || !change.owner().equals(schema.owner())
                || !change.table().equals(schema.name())) {
            throw new DataException(50071,
                    "DDL schema identity does not match " + change.qualifiedName());
        }
    }

    private static Scn immediatelyBefore(Scn commitScn, String qualifiedName) {
        if (commitScn.equals(Scn.zero())) {
            throw new DataException(50071,
                    "Cannot resolve the schema before SCN 0 for " + qualifiedName);
        }
        return Scn.of(commitScn.rawValue() - 1);
    }
}
