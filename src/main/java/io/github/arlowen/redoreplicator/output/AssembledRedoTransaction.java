/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.util.List;

public record AssembledRedoTransaction(
        List<RedoJsonChange> jsonChanges,
        List<TableSchemaVersion> schemaVersions) {
    public AssembledRedoTransaction {
        jsonChanges = List.copyOf(jsonChanges);
        schemaVersions = List.copyOf(schemaVersions);
    }
}
