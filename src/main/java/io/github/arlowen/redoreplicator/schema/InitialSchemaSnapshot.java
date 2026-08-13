/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.util.List;
import java.util.Objects;

public record InitialSchemaSnapshot(
        SchemaCatalog tableCatalog,
        SystemDictionaryState dictionaryState,
        List<TableSchemaVersion> schemaVersions) {
    public InitialSchemaSnapshot {
        tableCatalog = Objects.requireNonNull(
                tableCatalog, "tableCatalog").copy();
        Objects.requireNonNull(dictionaryState, "dictionaryState");
        schemaVersions = List.copyOf(schemaVersions);
    }
}
