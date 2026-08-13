/*
 * Java translation derived from OpenLogReplicator
 * src/replicator/ReplicatorOnline.cpp dictionary loading.
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
import java.util.Optional;

public final class OracleDictionaryReader {
    private static final String DATABASE_CHARACTER_SET = "NLS_CHARACTERSET";
    private static final String NATIONAL_CHARACTER_SET = "NLS_NCHAR_CHARACTERSET";

    private final OracleSchemaAssembler schemaAssembler;

    public OracleDictionaryReader() {
        schemaAssembler = new OracleSchemaAssembler();
    }

    public Optional<TableSchema> loadTable(Connection connection, String owner,
                                           String tableName, Scn targetScn)
            throws SQLException {
        String container = readContainerName(connection);
        Optional<OracleTableMetadata> metadata = readTableMetadata(
                connection, owner, tableName, targetScn);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }

        OracleTableMetadata table = metadata.get();
        OracleTableSupport.validate(table);
        List<OracleColumnMetadata> columns = readColumns(
                connection, table.objectId(), targetScn);
        Map<Integer, Integer> primaryKeyMembership = readPrimaryKeyMembership(
                connection, table.objectId(), targetScn);
        Map<Integer, Integer> guardSegments = readGuardSegments(
                connection, table.objectId(), targetScn);
        List<TablePartition> partitions = readTablePartitions(
                connection, table.objectId(), targetScn);
        List<LobSchema> lobs = readLobs(connection, table, targetScn);
        long defaultCharacterSetId = readCharacterSetId(
                connection, DATABASE_CHARACTER_SET);
        long defaultNationalCharacterSetId = readCharacterSetId(
                connection, NATIONAL_CHARACTER_SET);

        return Optional.of(schemaAssembler.assemble(
                container,
                table,
                columns,
                primaryKeyMembership,
                guardSegments,
                defaultCharacterSetId,
                defaultNationalCharacterSetId,
                lobs,
                partitions));
    }

    private static String readContainerName(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.CONTAINER_NAME);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Oracle did not return the current container name");
            }
            return resultSet.getString(1);
        }
    }

    private static long readCharacterSetId(Connection connection, String property)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.CHARACTER_SET)) {
            statement.setString(1, property);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Oracle database property is missing: " + property);
                }
                return resultSet.getLong(1);
            }
        }
    }

    private static Optional<OracleTableMetadata> readTableMetadata(
            Connection connection, String owner, String tableName, Scn targetScn)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.TABLE)) {
            bindScn(statement, 1, targetScn);
            bindScn(statement, 2, targetScn);
            bindScn(statement, 3, targetScn);
            bindScn(statement, 4, targetScn);
            statement.setString(5, owner);
            statement.setString(6, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                OracleTableMetadata table = new OracleTableMetadata(
                        owner,
                        tableName,
                        resultSet.getLong(2),
                        resultSet.getLong(3),
                        resultSet.getLong(1),
                        resultSet.getInt(4),
                        0,
                        unsignedLong(resultSet.getBigDecimal(5)),
                        unsignedLong(resultSet.getBigDecimal(6)),
                        unsignedLong(resultSet.getBigDecimal(7)),
                        (unsignedLong(resultSet.getBigDecimal(8)) & 4) != 0);
                if (resultSet.next()) {
                    throw new SQLException("Oracle returned duplicate table metadata for "
                            + owner + "." + tableName);
                }
                return Optional.of(table);
            }
        }
    }

    private static List<OracleColumnMetadata> readColumns(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        List<OracleColumnMetadata> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.COLUMNS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(new OracleColumnMetadata(
                            resultSet.getInt(1),
                            resultSet.getInt(2),
                            resultSet.getInt(3),
                            resultSet.getString(4),
                            resultSet.getInt(5),
                            resultSet.getInt(6),
                            resultSet.getInt(7),
                            resultSet.getInt(8),
                            resultSet.getInt(9),
                            resultSet.getLong(10),
                            resultSet.getInt(11),
                            unsignedLong(resultSet.getBigDecimal(12))));
                }
            }
        }
        return List.copyOf(columns);
    }

    private static Map<Integer, Integer> readPrimaryKeyMembership(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        Map<Integer, Integer> primaryKeyMembership = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.PRIMARY_KEY_MEMBERSHIP)) {
            bindScn(statement, 1, targetScn);
            bindScn(statement, 2, targetScn);
            statement.setLong(3, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    primaryKeyMembership.put(resultSet.getInt(1), resultSet.getInt(2));
                }
            }
        }
        return Map.copyOf(primaryKeyMembership);
    }

    private static Map<Integer, Integer> readGuardSegments(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        Map<Integer, Integer> guardSegments = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.GUARD_SEGMENTS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guardSegments.put(resultSet.getInt(1), resultSet.getInt(2));
                }
            }
        }
        return Map.copyOf(guardSegments);
    }

    private static List<TablePartition> readTablePartitions(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        List<TablePartition> partitions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.TABLE_PARTITIONS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            bindScn(statement, 3, targetScn);
            bindScn(statement, 4, targetScn);
            statement.setLong(5, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    partitions.add(new TablePartition(
                            resultSet.getLong(1), resultSet.getLong(2)));
                }
            }
        }
        return List.copyOf(partitions);
    }

    private static List<LobSchema> readLobs(Connection connection,
                                             OracleTableMetadata table,
                                             Scn targetScn) throws SQLException {
        List<OracleLobMetadata> metadata = readLobMetadata(
                connection, table.objectId(), targetScn);
        List<LobSchema> lobs = new ArrayList<>();
        for (OracleLobMetadata lob : metadata) {
            List<Long> indexes = readObjectDataByName(
                    connection,
                    table.userId(),
                    lobIndexName(table.objectId(), lob.internalColumn()),
                    targetScn);
            Map<Long, LobPartition> partitions = readLobPartitions(
                    connection, lob.lobObjectId(), targetScn);
            if (lob.dataObjectId() != 0) {
                LobPartition base = new LobPartition(
                        lob.dataObjectId(), lobPageSize(lob.blockSize()));
                partitions.putIfAbsent(base.dataObjectId(), base);
            }
            lobs.add(new LobSchema(
                    lob.objectId(),
                    lob.dataObjectId(),
                    lob.lobObjectId(),
                    lob.columnNumber(),
                    lob.internalColumn(),
                    indexes,
                    List.copyOf(partitions.values())));
        }
        return List.copyOf(lobs);
    }

    private static List<OracleLobMetadata> readLobMetadata(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        List<OracleLobMetadata> lobs = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.LOBS)) {
            bindScn(statement, 1, targetScn);
            bindScn(statement, 2, targetScn);
            bindScn(statement, 3, targetScn);
            statement.setLong(4, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lobs.add(new OracleLobMetadata(
                            resultSet.getLong(1),
                            resultSet.getLong(2),
                            resultSet.getLong(3),
                            resultSet.getInt(4),
                            resultSet.getInt(5),
                            resultSet.getInt(6)));
                }
            }
        }
        return List.copyOf(lobs);
    }

    private static List<Long> readObjectDataByName(
            Connection connection, long userId, String objectName, Scn targetScn)
            throws SQLException {
        List<Long> dataObjectIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.OBJECT_DATA_BY_NAME)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, userId);
            statement.setString(3, objectName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long dataObjectId = resultSet.getLong(1);
                    if (dataObjectId != 0) {
                        dataObjectIds.add(dataObjectId);
                    }
                }
            }
        }
        return List.copyOf(dataObjectIds);
    }

    private static Map<Long, LobPartition> readLobPartitions(
            Connection connection, long lobObjectId, Scn targetScn) throws SQLException {
        Map<Long, LobPartition> partitions = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.LOB_PARTITIONS)) {
            bindScn(statement, 1, targetScn);
            bindScn(statement, 2, targetScn);
            bindScn(statement, 3, targetScn);
            statement.setLong(4, lobObjectId);
            bindScn(statement, 5, targetScn);
            bindScn(statement, 6, targetScn);
            bindScn(statement, 7, targetScn);
            bindScn(statement, 8, targetScn);
            statement.setLong(9, lobObjectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long dataObjectId = resultSet.getLong(1);
                    if (dataObjectId != 0) {
                        partitions.putIfAbsent(
                                dataObjectId,
                                new LobPartition(
                                        dataObjectId,
                                        lobPageSize(resultSet.getInt(2))));
                    }
                }
            }
        }
        return partitions;
    }

    private static String lobIndexName(long objectId, int internalColumn) {
        return String.format("SYS_IL%010dC%05d$$", objectId, internalColumn);
    }

    private static int lobPageSize(int blockSize) {
        if (blockSize == 16_384) {
            return 16_264;
        }
        if (blockSize == 32_768) {
            return 32_528;
        }
        return 8_132;
    }

    private static void bindScn(PreparedStatement statement, int index, Scn scn)
            throws SQLException {
        statement.setBigDecimal(index, new BigDecimal(scn.toDecimalString()));
    }

    private static long unsignedLong(BigDecimal value) throws SQLException {
        if (value == null) {
            return 0;
        }
        try {
            return value.toBigIntegerExact().longValue();
        } catch (ArithmeticException e) {
            throw new SQLException("Oracle dictionary integer is not exact: " + value, e);
        }
    }
}
