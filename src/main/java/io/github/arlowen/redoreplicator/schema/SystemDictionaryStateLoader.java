/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.Scn;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

@FunctionalInterface
public interface SystemDictionaryStateLoader {
    default SystemDictionaryState loadReferenceData(
            Connection connection, Scn targetScn) throws SQLException {
        return SystemDictionaryState.empty();
    }

    Optional<SystemDictionaryState> loadTable(
            Connection connection, String owner, String table, Scn targetScn)
            throws SQLException;
}
