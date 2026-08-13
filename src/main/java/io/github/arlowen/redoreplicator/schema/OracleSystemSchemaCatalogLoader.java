/*
 * Java translation derived from OpenLogReplicator initial SYS dictionary
 * table metadata loading in src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.Scn;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class OracleSystemSchemaCatalogLoader {
    private final TableSchemaLoader tableSchemaLoader;

    public OracleSystemSchemaCatalogLoader(
            TableSchemaLoader tableSchemaLoader) {
        this.tableSchemaLoader = Objects.requireNonNull(
                tableSchemaLoader, "tableSchemaLoader");
    }

    public SchemaCatalog load(Connection connection, Scn targetScn)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(targetScn, "targetScn");
        SchemaCatalog catalog = new SchemaCatalog();
        for (SystemDictionaryTable table : SystemDictionaryTable.values()) {
            TableSchema schema = tableSchemaLoader.loadTable(
                    connection, "SYS", table.tableName(), targetScn)
                    .orElseThrow(() -> new DataException(50071,
                            "Oracle dictionary schema is missing at SCN "
                                    + targetScn + ": "
                                    + table.qualifiedName()));
            catalog.add(schema);
        }
        return catalog;
    }
}
