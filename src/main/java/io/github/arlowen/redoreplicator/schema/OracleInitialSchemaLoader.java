/*
 * Java translation derived from OpenLogReplicator selected-table dictionary
 * bootstrap in src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.config.TableFilter;
import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OracleInitialSchemaLoader {
    private final TableSchemaLoader tableSchemaLoader;
    private final SystemDictionaryStateLoader dictionaryStateLoader;
    private final TableSchemaJsonCodec jsonCodec;

    public OracleInitialSchemaLoader(
            TableSchemaLoader tableSchemaLoader,
            SystemDictionaryStateLoader dictionaryStateLoader,
            TableSchemaJsonCodec jsonCodec) {
        this.tableSchemaLoader = Objects.requireNonNull(
                tableSchemaLoader, "tableSchemaLoader");
        this.dictionaryStateLoader = Objects.requireNonNull(
                dictionaryStateLoader, "dictionaryStateLoader");
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec");
    }

    public InitialSchemaSnapshot load(
            Connection connection,
            SchemaCatalog identityCatalog,
            TableFilter tableFilter,
            Scn targetScn) throws IOException, SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(identityCatalog, "identityCatalog");
        Objects.requireNonNull(tableFilter, "tableFilter");
        Objects.requireNonNull(targetScn, "targetScn");
        SchemaCatalog catalog = identityCatalog.copy();
        Map<SystemDictionaryKey, SystemDictionaryRow> dictionaryRows =
                new LinkedHashMap<>();
        for (SystemDictionaryRow row : dictionaryStateLoader
                .loadReferenceData(connection, targetScn).rows()) {
            dictionaryRows.put(new SystemDictionaryKey(
                    row.dictionaryTable(), row.rowId()), row);
        }
        List<TableSchemaVersion> versions = new ArrayList<>();
        for (TableSchema identity : identityCatalog.tables()) {
            if (!tableFilter.matches(identity.qualifiedName())) {
                continue;
            }
            TableSchema schema = tableSchemaLoader.loadTable(
                    connection, identity.owner(), identity.name(), targetScn)
                    .orElseThrow(() -> new DataException(50071,
                            "Selected table schema is missing at SCN "
                                    + targetScn + ": "
                                    + identity.qualifiedName()));
            catalog.replace(schema);
            versions.add(TableSchemaVersion.initial(
                    schema, targetScn, jsonCodec));
            SystemDictionaryState state = dictionaryStateLoader.loadTable(
                    connection, identity.owner(), identity.name(), targetScn)
                    .orElseThrow(() -> new DataException(50071,
                            "Selected table dictionary is missing at SCN "
                                    + targetScn + ": "
                                    + identity.qualifiedName()));
            for (SystemDictionaryRow row : state.rows()) {
                SystemDictionaryKey key = new SystemDictionaryKey(
                        row.dictionaryTable(), row.rowId());
                SystemDictionaryRow previous = dictionaryRows.putIfAbsent(
                        key, row);
                if (previous != null && !previous.equals(row)) {
                    throw new DataException(50071,
                            "Conflicting initial dictionary row " + key);
                }
            }
        }
        return new InitialSchemaSnapshot(
                catalog,
                SystemDictionaryState.of(dictionaryRows.values()),
                versions);
    }
}
