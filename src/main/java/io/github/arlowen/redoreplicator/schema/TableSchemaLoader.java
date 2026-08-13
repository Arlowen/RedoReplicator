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
public interface TableSchemaLoader {
    Optional<TableSchema> loadTable(Connection connection, String owner,
                                    String tableName, Scn targetScn)
            throws SQLException;
}
