/*
 * Java translation derived from OpenLogReplicator initial SYS dictionary loading.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OracleSystemDictionaryReader
        implements SystemDictionaryStateLoader {
    @Override
    public SystemDictionaryState loadReferenceData(
            Connection connection, Scn targetScn) throws SQLException {
        Map<SystemDictionaryKey, SystemDictionaryRow> rows =
                new LinkedHashMap<>();
        addRows(rows, readUsers(connection, targetScn));
        addRows(rows, readAllTablespaces(connection, targetScn));
        return SystemDictionaryState.of(rows.values());
    }

    @Override
    public Optional<SystemDictionaryState> loadTable(
            Connection connection, String owner, String table, Scn targetScn)
            throws SQLException {
        Optional<SystemTableIdentity> identity = readIdentity(
                connection, owner, table, targetScn);
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        long userId = identity.get().userId();
        long objectId = identity.get().objectId();

        List<SysLob> lobs = readLobs(connection, objectId, targetScn);
        List<SysLobCompPart> lobCompositePartitions = readLobCompositePartitions(
                connection, lobs, targetScn);
        List<SysLobFrag> lobFragments = readLobFragments(
                connection, lobs, lobCompositePartitions, targetScn);
        List<SysTabComPart> tableCompositePartitions = readTableCompositePartitions(
                connection, objectId, targetScn);
        List<SysTabSubPart> tableSubpartitions = readTableSubpartitions(
                connection, tableCompositePartitions, targetScn);

        Set<Long> relatedObjectIds = new LinkedHashSet<>();
        relatedObjectIds.add(objectId);
        lobs.stream().map(SysLob::lobObjectId).forEach(relatedObjectIds::add);
        lobFragments.stream().map(SysLobFrag::fragmentObjectId)
                .forEach(relatedObjectIds::add);

        Map<SystemDictionaryKey, SystemDictionaryRow> rows = new LinkedHashMap<>();
        addRow(rows, readUser(connection, userId, targetScn));
        for (Long relatedObjectId : relatedObjectIds) {
            addRows(rows, readObjects(connection, relatedObjectId, targetScn));
        }
        for (SysLob lob : lobs) {
            addRows(rows, readObjectsByName(
                    connection,
                    userId,
                    lobIndexName(objectId, lob.internalColumn()),
                    targetScn));
        }
        addRows(rows, readTables(connection, objectId, targetScn));
        addRows(rows, readColumns(connection, objectId, targetScn));
        addRows(rows, readDeferredStorage(connection, objectId, targetScn));
        addRows(rows, readExtendedColumns(connection, objectId, targetScn));
        addRows(rows, lobs);
        addRows(rows, lobCompositePartitions);
        addRows(rows, lobFragments);
        addRows(rows, readConstraints(connection, objectId, targetScn));
        addRows(rows, readConstraintColumns(connection, objectId, targetScn));
        addRows(rows, tableCompositePartitions);
        addRows(rows, readTablePartitions(connection, objectId, targetScn));
        addRows(rows, tableSubpartitions);

        Set<Long> tablespaceIds = new LinkedHashSet<>();
        lobs.stream().map(SysLob::tablespaceId).forEach(tablespaceIds::add);
        lobFragments.stream().map(SysLobFrag::tablespaceId)
                .forEach(tablespaceIds::add);
        for (Long tablespaceId : tablespaceIds) {
            addRows(rows, readTablespaces(connection, tablespaceId, targetScn));
        }
        return Optional.of(SystemDictionaryState.of(rows.values()));
    }

    private static Optional<SystemTableIdentity> readIdentity(
            Connection connection, String owner, String table, Scn targetScn)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_TABLE_IDENTITY)) {
            bindScn(statement, 1, targetScn);
            bindScn(statement, 2, targetScn);
            statement.setString(3, owner);
            statement.setString(4, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                SystemTableIdentity identity = new SystemTableIdentity(
                        resultSet.getLong(1), resultSet.getLong(2));
                if (resultSet.next()) {
                    throw new SQLException(
                            "Oracle returned duplicate system table identity for "
                                    + owner + "." + table);
                }
                return Optional.of(identity);
            }
        }
    }

    private static SysUser readUser(Connection connection, long userId,
                                    Scn targetScn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_USER_ROW)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("SYS.USER$ row is missing for USER# " + userId);
                }
                SysUser user = new SysUser(
                        RowId.parse(resultSet.getString(1)),
                        resultSet.getLong(2),
                        resultSet.getString(3),
                        intX(resultSet.getBigDecimal(4)));
                if (resultSet.next()) {
                    throw new SQLException("Duplicate SYS.USER$ row for USER# " + userId);
                }
                return user;
            }
        }
    }

    private static List<SysUser> readUsers(
            Connection connection, Scn targetScn) throws SQLException {
        List<SysUser> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_USER_ROWS)) {
            bindScn(statement, 1, targetScn);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysUser(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getString(3),
                            intX(resultSet.getBigDecimal(4))));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysObj> readObjects(Connection connection, long objectId,
                                            Scn targetScn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_OBJECT_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            return readObjects(statement);
        }
    }

    private static List<SysObj> readObjectsByName(
            Connection connection, long userId, String name, Scn targetScn)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_OBJECT_ROWS_BY_NAME)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, userId);
            statement.setString(3, name);
            return readObjects(statement);
        }
    }

    private static List<SysObj> readObjects(PreparedStatement statement)
            throws SQLException {
        List<SysObj> rows = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rows.add(new SysObj(
                        RowId.parse(resultSet.getString(1)),
                        resultSet.getLong(2),
                        resultSet.getLong(3),
                        resultSet.getLong(4),
                        resultSet.getInt(5),
                        resultSet.getString(6),
                        intX(resultSet.getBigDecimal(7))));
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysTab> readTables(Connection connection, long objectId,
                                           Scn targetScn) throws SQLException {
        List<SysTab> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_TABLE_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysTab(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getLong(3),
                            resultSet.getLong(4),
                            resultSet.getInt(5),
                            intX(resultSet.getBigDecimal(6)),
                            intX(resultSet.getBigDecimal(7))));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysCol> readColumns(Connection connection, long objectId,
                                            Scn targetScn) throws SQLException {
        List<SysCol> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_COLUMN_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysCol(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getInt(3),
                            resultSet.getInt(4),
                            resultSet.getInt(5),
                            resultSet.getString(6),
                            resultSet.getInt(7),
                            resultSet.getInt(8),
                            resultSet.getInt(9),
                            resultSet.getInt(10),
                            resultSet.getInt(11),
                            resultSet.getLong(12),
                            resultSet.getInt(13),
                            intX(resultSet.getBigDecimal(14))));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysDeferredStg> readDeferredStorage(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        List<SysDeferredStg> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_DEFERRED_STORAGE_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysDeferredStg(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            intX(resultSet.getBigDecimal(3))));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysECol> readExtendedColumns(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        List<SysECol> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_EXTENDED_COLUMN_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysECol(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getInt(3),
                            resultSet.getInt(4)));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysLob> readLobs(Connection connection, long objectId,
                                         Scn targetScn) throws SQLException {
        List<SysLob> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_LOB_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysLob(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getInt(3),
                            resultSet.getInt(4),
                            resultSet.getLong(5),
                            resultSet.getLong(6)));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysLobCompPart> readLobCompositePartitions(
            Connection connection, List<SysLob> lobs, Scn targetScn)
            throws SQLException {
        List<SysLobCompPart> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_LOB_COMPOSITE_PARTITION_ROWS)) {
            for (SysLob lob : lobs) {
                bindScn(statement, 1, targetScn);
                statement.setLong(2, lob.lobObjectId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        rows.add(new SysLobCompPart(
                                RowId.parse(resultSet.getString(1)),
                                resultSet.getLong(2),
                                resultSet.getLong(3)));
                    }
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysLobFrag> readLobFragments(
            Connection connection, List<SysLob> lobs,
            List<SysLobCompPart> compositePartitions, Scn targetScn)
            throws SQLException {
        Set<Long> parents = new LinkedHashSet<>();
        lobs.stream().map(SysLob::lobObjectId).forEach(parents::add);
        compositePartitions.stream().map(SysLobCompPart::partitionObjectId)
                .forEach(parents::add);
        List<SysLobFrag> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_LOB_FRAGMENT_ROWS)) {
            for (Long parent : parents) {
                bindScn(statement, 1, targetScn);
                statement.setLong(2, parent);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        rows.add(new SysLobFrag(
                                RowId.parse(resultSet.getString(1)),
                                resultSet.getLong(2),
                                resultSet.getLong(3),
                                resultSet.getLong(4)));
                    }
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysCDef> readConstraints(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        List<SysCDef> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_CONSTRAINT_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysCDef(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getLong(3),
                            resultSet.getInt(4)));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysCCol> readConstraintColumns(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        List<SysCCol> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_CONSTRAINT_COLUMN_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysCCol(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getInt(3),
                            resultSet.getLong(4),
                            intX(resultSet.getBigDecimal(5))));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysTabComPart> readTableCompositePartitions(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        List<SysTabComPart> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_TABLE_COMPOSITE_PARTITION_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysTabComPart(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getLong(3),
                            resultSet.getLong(4)));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysTabPart> readTablePartitions(
            Connection connection, long objectId, Scn targetScn) throws SQLException {
        List<SysTabPart> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_TABLE_PARTITION_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysTabPart(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getLong(3),
                            resultSet.getLong(4)));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysTabSubPart> readTableSubpartitions(
            Connection connection, List<SysTabComPart> compositePartitions,
            Scn targetScn) throws SQLException {
        List<SysTabSubPart> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_TABLE_SUBPARTITION_ROWS)) {
            for (SysTabComPart partition : compositePartitions) {
                bindScn(statement, 1, targetScn);
                statement.setLong(2, partition.objectId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        rows.add(new SysTabSubPart(
                                RowId.parse(resultSet.getString(1)),
                                resultSet.getLong(2),
                                resultSet.getLong(3),
                                resultSet.getLong(4)));
                    }
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysTs> readTablespaces(
            Connection connection, long tablespaceId, Scn targetScn) throws SQLException {
        List<SysTs> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_TABLESPACE_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, tablespaceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysTs(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getString(3),
                            resultSet.getInt(4)));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<SysTs> readAllTablespaces(
            Connection connection, Scn targetScn) throws SQLException {
        List<SysTs> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_ALL_TABLESPACE_ROWS)) {
            bindScn(statement, 1, targetScn);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SysTs(
                            RowId.parse(resultSet.getString(1)),
                            resultSet.getLong(2),
                            resultSet.getString(3),
                            resultSet.getInt(4)));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static void addRows(
            Map<SystemDictionaryKey, SystemDictionaryRow> rows,
            Collection<? extends SystemDictionaryRow> additions) throws SQLException {
        for (SystemDictionaryRow row : additions) {
            addRow(rows, row);
        }
    }

    private static void addRow(
            Map<SystemDictionaryKey, SystemDictionaryRow> rows,
            SystemDictionaryRow row) throws SQLException {
        SystemDictionaryKey key = new SystemDictionaryKey(
                row.dictionaryTable(), row.rowId());
        SystemDictionaryRow previous = rows.putIfAbsent(key, row);
        if (previous != null && !previous.equals(row)) {
            throw new SQLException("Oracle returned conflicting dictionary row " + key);
        }
    }

    private static String lobIndexName(long objectId, int internalColumn) {
        return String.format("SYS_IL%010dC%05d$$", objectId, internalColumn);
    }

    private static void bindScn(PreparedStatement statement, int index, Scn scn)
            throws SQLException {
        statement.setBigDecimal(index, new BigDecimal(scn.toDecimalString()));
    }

    private static IntX intX(BigDecimal value) throws SQLException {
        try {
            return IntX.parseDecimal(value.toBigIntegerExact().toString());
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw new SQLException("Oracle dictionary integer is invalid: " + value, e);
        }
    }
}
