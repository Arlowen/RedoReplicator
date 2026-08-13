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
import java.util.List;
import java.util.Optional;

public final class OracleSystemDictionaryReader {
    public Optional<SystemDictionaryState> loadCoreTable(
            Connection connection, String owner, String table, Scn targetScn)
            throws SQLException {
        Optional<SystemTableIdentity> identity = readIdentity(
                connection, owner, table, targetScn);
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        long userId = identity.get().userId();
        long objectId = identity.get().objectId();
        List<SystemDictionaryRow> rows = new ArrayList<>();
        rows.add(readUser(connection, userId, targetScn));
        rows.addAll(readObjects(connection, objectId, targetScn));
        rows.addAll(readTables(connection, objectId, targetScn));
        rows.addAll(readColumns(connection, objectId, targetScn));
        rows.addAll(readConstraints(connection, objectId, targetScn));
        rows.addAll(readConstraintColumns(connection, objectId, targetScn));
        return Optional.of(SystemDictionaryState.of(rows));
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

    private static List<SysObj> readObjects(Connection connection, long objectId,
                                            Scn targetScn) throws SQLException {
        List<SysObj> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OracleDictionarySql.SYSTEM_OBJECT_ROWS)) {
            bindScn(statement, 1, targetScn);
            statement.setLong(2, objectId);
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
