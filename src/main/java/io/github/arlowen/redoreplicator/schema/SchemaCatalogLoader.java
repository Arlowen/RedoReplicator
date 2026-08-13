/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.state.StateStore;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.sql.SQLException;
import java.util.Objects;

public final class SchemaCatalogLoader {
    private final StateStore stateStore;
    private final TableSchemaJsonCodec jsonCodec;

    public SchemaCatalogLoader(
            StateStore stateStore, TableSchemaJsonCodec jsonCodec) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec");
    }

    public SchemaCatalog loadAt(Scn targetScn)
            throws SQLException, JsonProcessingException {
        Objects.requireNonNull(targetScn, "targetScn");
        SchemaCatalog catalog = new SchemaCatalog();
        for (TableSchemaVersion version
                : stateStore.findSchemasAt(targetScn)) {
            if (!version.dropTombstone()) {
                catalog.add(version.decode(jsonCodec));
            }
        }
        return catalog;
    }
}
