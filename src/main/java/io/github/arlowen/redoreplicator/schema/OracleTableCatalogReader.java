/*
 * Java translation derived from OpenLogReplicator initial object and
 * partition lookup in src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.Scn;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OracleTableCatalogReader {

    public SchemaCatalog load(Connection connection, String container,
                              Scn targetScn) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(targetScn, "targetScn");
        Map<Long, List<TablePartition>> partitions = readPartitions(
                connection, targetScn);
        SchemaCatalog catalog = new SchemaCatalog();
        BigDecimal scn = new BigDecimal(targetScn.toDecimalString());
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.TABLE_IDENTITIES)) {
            statement.setBigDecimal(1, scn);
            statement.setBigDecimal(2, scn);
            statement.setBigDecimal(3, scn);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long objectId = resultSet.getLong(4);
                    catalog.add(new TableSchema(
                            container,
                            resultSet.getString(1),
                            resultSet.getString(2),
                            objectId,
                            resultSet.getLong(5),
                            resultSet.getLong(3),
                            resultSet.getInt(6),
                            0,
                            List.of(),
                            List.of(),
                            partitions.getOrDefault(objectId, List.of())));
                }
            }
        }
        return catalog;
    }

    private static Map<Long, List<TablePartition>> readPartitions(
            Connection connection, Scn targetScn) throws SQLException {
        Map<Long, List<TablePartition>> partitions = new LinkedHashMap<>();
        BigDecimal scn = new BigDecimal(targetScn.toDecimalString());
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.TABLE_PARTITION_IDENTITIES)) {
            statement.setBigDecimal(1, scn);
            statement.setBigDecimal(2, scn);
            statement.setBigDecimal(3, scn);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    partitions.computeIfAbsent(
                            resultSet.getLong(1), ignored -> new ArrayList<>())
                            .add(new TablePartition(
                                    resultSet.getLong(2),
                                    resultSet.getLong(3)));
                }
            }
        }
        Map<Long, List<TablePartition>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<TablePartition>> entry
                : partitions.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }
}
