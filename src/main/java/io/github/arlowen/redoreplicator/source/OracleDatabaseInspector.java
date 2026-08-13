/*
 * Java translation derived from OpenLogReplicator
 * src/replicator/ReplicatorOnline.cpp database metadata loading.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class OracleDatabaseInspector {
    private static final String DATABASE_CONTEXT_SQL = """
            SELECT D.DBID, D.CURRENT_SCN, D.LOG_MODE, D.FORCE_LOGGING,
                   D.SUPPLEMENTAL_LOG_DATA_MIN, D.CDB,
                   I.INCARNATION#, I.RESETLOGS_ID,
                   SYS_CONTEXT('USERENV', 'DB_NAME'),
                   NVL(SYS_CONTEXT('USERENV', 'CON_NAME'),
                       SYS_CONTEXT('USERENV', 'DB_NAME'))
              FROM SYS.V_$DATABASE D
              JOIN SYS.V_$DATABASE_INCARNATION I ON I.STATUS = 'CURRENT'
            """;
    private static final String VERSION_SQL = """
            SELECT VERSION_FULL
              FROM SYS.V_$INSTANCE
            """;

    public OracleDatabaseContext inspect(Connection connection) throws SQLException {
        String version = readVersion(connection);
        try (PreparedStatement statement = connection.prepareStatement(
                DATABASE_CONTEXT_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Oracle did not return database identity metadata");
            }
            OracleDatabaseContext context = new OracleDatabaseContext(
                    new DatabaseIdentity(
                            resultSet.getLong(1),
                            resultSet.getLong(7),
                            resultSet.getLong(8)),
                    Scn.of(resultSet.getLong(2)),
                    resultSet.getString(9),
                    resultSet.getString(10),
                    version,
                    "YES".equals(resultSet.getString(6)),
                    resultSet.getString(3),
                    "YES".equals(resultSet.getString(4)),
                    "YES".equals(resultSet.getString(5)));
            if (resultSet.next()) {
                throw new SQLException("Oracle returned multiple current incarnations");
            }
            return context;
        }
    }

    private static String readVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(VERSION_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Oracle did not return the instance version");
            }
            return resultSet.getString(1);
        }
    }
}
