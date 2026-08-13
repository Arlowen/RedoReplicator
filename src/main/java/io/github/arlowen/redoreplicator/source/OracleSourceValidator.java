/*
 * Java translation derived from OpenLogReplicator online source validation in
 * src/replicator/DatabaseConnection.cpp and ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class OracleSourceValidator {
    private static final Logger log = LoggerFactory.getLogger(
            OracleSourceValidator.class);
    private static final String CONTAINER_NAME_SQL = """
            SELECT NVL(SYS_CONTEXT('USERENV', 'CON_NAME'),
                       SYS_CONTEXT('USERENV', 'DB_NAME'))
              FROM DUAL
            """;
    private static final String CHARACTER_SET_SQL = """
            SELECT NLS_CHARSET_ID(PROPERTY_VALUE)
              FROM DATABASE_PROPERTIES
             WHERE PROPERTY_NAME = ?
            """;
    private static final List<String> REQUIRED_DICTIONARY_TABLES = List.of(
            "CCOL$", "CDEF$", "COL$", "DEFERRED_STG$", "ECOL$",
            "LOB$", "LOBCOMPPART$", "LOBFRAG$", "OBJ$", "TAB$",
            "TABCOMPART$", "TABPART$", "TABSUBPART$", "TS$", "USER$");
    private static final List<String> REQUIRED_DYNAMIC_VIEWS = List.of(
            "V_$ARCHIVED_LOG", "V_$LOG", "V_$PARAMETER", "V_$PDBS");
    private static final String REDO_FILES_SQL = """
            SELECT MEMBER, TYPE
              FROM SYS.V_$LOGFILE
             WHERE TYPE IN ('ONLINE', 'STANDBY')
             ORDER BY GROUP#, MEMBER
            """;
    private static final String ARCHIVED_FILES_SQL = """
            SELECT A.NAME, 'ARCHIVED'
              FROM SYS.V_$ARCHIVED_LOG A
              JOIN SYS.V_$DATABASE_INCARNATION I
                ON I.STATUS = 'CURRENT'
               AND I.RESETLOGS_ID = A.RESETLOGS_ID
             WHERE A.NAME IS NOT NULL
               AND A.DELETED = 'NO'
               AND A.STATUS = 'A'
             ORDER BY A.THREAD#, A.SEQUENCE#, A.DEST_ID
            """;

    private final OracleDatabaseInspector databaseInspector;

    public OracleSourceValidator() {
        databaseInspector = new OracleDatabaseInspector();
    }

    public OracleSourceValidation validate(
            Connection connection, ResolvedConfiguration configuration)
            throws SQLException {
        OracleDatabaseContext context = databaseInspector.inspect(connection);
        context.validateSupportedSource();
        validateDictionaryAccess(connection);
        List<OracleRedoFile> databaseFiles = readRedoFiles(connection);
        if (databaseFiles.isEmpty()) {
            throw new ConfigurationException(10006,
                    "Oracle did not return any online redo files");
        }

        List<Path> localFiles = new ArrayList<>(databaseFiles.size());
        for (OracleRedoFile databaseFile : databaseFiles) {
            Path local = configuration.redoPathMapper()
                    .map(databaseFile.path())
                    .orElseThrow(() -> new ConfigurationException(10007,
                            "No redoPathMappings entry matches Oracle redo file: "
                                    + databaseFile.path()));
            verifyReadable(databaseFile.path(), local);
            localFiles.add(local);
        }
        return new OracleSourceValidation(context, localFiles);
    }

    private static void validateDictionaryAccess(Connection connection)
            throws SQLException {
        validateQuery(connection, CONTAINER_NAME_SQL);
        validateQuery(connection, CHARACTER_SET_SQL);
        for (String table : REQUIRED_DICTIONARY_TABLES) {
            validateQuery(connection,
                    "SELECT 1 FROM SYS." + table + " WHERE ROWNUM = 0");
        }
        for (String view : REQUIRED_DYNAMIC_VIEWS) {
            validateQuery(connection,
                    "SELECT 1 FROM SYS." + view + " WHERE ROWNUM = 0");
        }
    }

    private static void validateQuery(Connection connection, String sql)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (sql.equals(CHARACTER_SET_SQL)) {
                statement.setString(1, "NLS_CHARACTERSET");
            }
            try (ResultSet ignored = statement.executeQuery()) {
                // Executing is the privilege check; rows are not required.
            }
        }
    }

    private static List<OracleRedoFile> readRedoFiles(Connection connection)
            throws SQLException {
        List<OracleRedoFile> files = new ArrayList<>();
        readRedoFiles(connection, REDO_FILES_SQL, files);
        readRedoFiles(connection, ARCHIVED_FILES_SQL, files);
        return List.copyOf(files);
    }

    private static void verifyReadable(String oraclePath, Path local) {
        if (!Files.isRegularFile(local)) {
            throw new ConfigurationException(10008,
                    "Mapped redo file is not a regular file: "
                            + oraclePath + " -> " + local);
        }
        try (SeekableByteChannel ignored = Files.newByteChannel(
                local, StandardOpenOption.READ)) {
            // Opening read-only proves the process can read the redo file.
        } catch (IOException e) {
            String msg = "Mapped redo file is not readable: "
                    + oraclePath + " -> " + local;
            log.error(msg, e);
            throw new ConfigurationException(10008, msg);
        }
    }

    private static void readRedoFiles(
            Connection connection,
            String sql,
            List<OracleRedoFile> files) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                files.add(new OracleRedoFile(
                        resultSet.getString(1), resultSet.getString(2)));
            }
        }
    }
}
