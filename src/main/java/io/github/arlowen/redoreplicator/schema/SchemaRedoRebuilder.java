/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

@FunctionalInterface
public interface SchemaRedoRebuilder {
    SchemaResolution rebuild(String container, String owner, String table,
                             Scn targetScn, Optional<TableSchemaVersion> baseVersion)
            throws IOException, SQLException;
}
