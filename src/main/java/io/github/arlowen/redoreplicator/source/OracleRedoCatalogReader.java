/*
 * Java translation derived from OpenLogReplicator online archive discovery in
 * src/replicator/ReplicatorOnline.cpp and ReplicatorOnline.h.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.config.RedoPathMapper;
import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class OracleRedoCatalogReader {
    private static final String ARCHIVED_LOG_SQL = """
            SELECT A.THREAD#, A.SEQUENCE#, A.FIRST_CHANGE#, A.NEXT_CHANGE#,
                   A.STATUS, A.NAME
              FROM SYS.V_$ARCHIVED_LOG A
              JOIN SYS.V_$DATABASE_INCARNATION I
                ON I.STATUS = 'CURRENT'
               AND I.RESETLOGS_ID = A.RESETLOGS_ID
             WHERE A.NAME IS NOT NULL
               AND A.DELETED = 'NO'
               AND A.STATUS = 'A'
             ORDER BY A.THREAD#, A.SEQUENCE#, A.DEST_ID,
                      A.IS_RECOVERY_DEST_FILE DESC
            """;
    private static final String ONLINE_LOG_SQL = """
            SELECT L.THREAD#, L.SEQUENCE#, L.FIRST_CHANGE#, L.NEXT_CHANGE#,
                   L.STATUS, LF.MEMBER
              FROM SYS.V_$LOG L
              JOIN SYS.V_$LOGFILE LF ON LF.GROUP# = L.GROUP#
             WHERE LF.TYPE = 'ONLINE'
             ORDER BY L.THREAD#, L.SEQUENCE#, L.GROUP#,
                      LF.IS_RECOVERY_DEST_FILE DESC, LF.MEMBER
            """;

    private final OracleDatabaseInspector databaseInspector;

    public OracleRedoCatalogReader() {
        databaseInspector = new OracleDatabaseInspector();
    }

    public OracleRedoCatalog read(
            Connection connection, RedoPathMapper pathMapper)
            throws SQLException {
        OracleDatabaseContext context = databaseInspector.inspect(connection);
        return read(connection, pathMapper, context);
    }

    OracleRedoCatalog read(
            Connection connection,
            RedoPathMapper pathMapper,
            OracleDatabaseContext context) throws SQLException {
        List<OracleRedoLog> archived = readLogs(
                connection, pathMapper, ARCHIVED_LOG_SQL,
                OracleRedoLogKind.ARCHIVED);
        List<OracleRedoLog> online = readLogs(
                connection, pathMapper, ONLINE_LOG_SQL,
                OracleRedoLogKind.ONLINE);
        return new OracleRedoCatalog(context, archived, online);
    }

    private static List<OracleRedoLog> readLogs(
            Connection connection,
            RedoPathMapper pathMapper,
            String sql,
            OracleRedoLogKind kind) throws SQLException {
        List<OracleRedoLog> logs = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String oraclePath = resultSet.getString(6);
                Path localPath = pathMapper.map(oraclePath)
                        .orElseThrow(() -> new ConfigurationException(
                                10007,
                                "No redoPathMappings entry matches Oracle redo file: "
                                        + oraclePath));
                logs.add(new OracleRedoLog(
                        kind,
                        resultSet.getInt(1),
                        Seq.of(resultSet.getLong(2)),
                        readScn(resultSet, 3),
                        readScn(resultSet, 4),
                        resultSet.getString(5),
                        oraclePath,
                        localPath));
            }
        }
        return List.copyOf(logs);
    }

    private static Scn readScn(ResultSet resultSet, int column)
            throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return Scn.parseDecimal(value.toBigIntegerExact().toString());
    }
}
