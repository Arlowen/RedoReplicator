/*
 * Java translation derived from OpenLogReplicator container discovery in
 * src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OracleContainerCatalogReader {
    private static final String CURRENT_CONTAINER_SQL = """
            SELECT TO_NUMBER(SYS_CONTEXT('USERENV', 'CON_ID')),
                   NVL(SYS_CONTEXT('USERENV', 'CON_NAME'),
                       SYS_CONTEXT('USERENV', 'DB_NAME'))
              FROM DUAL
            """;
    private static final String OPEN_PDBS_SQL = """
            SELECT CON_ID, NAME
              FROM SYS.V_$PDBS
             WHERE OPEN_MODE = 'READ WRITE'
             ORDER BY CON_ID
            """;

    public OracleContainerRegistry read(
            Connection connection, OracleDatabaseContext databaseContext)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(databaseContext, "databaseContext");
        List<OracleContainer> containers = new ArrayList<>();
        containers.add(readCurrent(connection));
        if (databaseContext.containerDatabase()
                && OracleContainer.ROOT_NAME.equals(
                        databaseContext.containerName())) {
            containers.addAll(readOpenPdbs(connection));
        }
        return new OracleContainerRegistry(containers);
    }

    private static OracleContainer readCurrent(Connection connection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                CURRENT_CONTAINER_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException(
                        "Oracle did not return the current container");
            }
            OracleContainer container = new OracleContainer(
                    resultSet.getInt(1), resultSet.getString(2));
            if (resultSet.next()) {
                throw new SQLException(
                        "Oracle returned multiple current containers");
            }
            return container;
        }
    }

    private static List<OracleContainer> readOpenPdbs(Connection connection)
            throws SQLException {
        List<OracleContainer> containers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                OPEN_PDBS_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                containers.add(new OracleContainer(
                        resultSet.getInt(1), resultSet.getString(2)));
            }
        }
        return List.copyOf(containers);
    }
}
