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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final OracleDatabaseInspector databaseInspector;
    private final OracleRedoCatalogReader catalogReader;
    private final OracleContainerCatalogReader containerCatalogReader;
    private final OracleContainerSession containerSession;

    public OracleSourceValidator() {
        databaseInspector = new OracleDatabaseInspector();
        catalogReader = new OracleRedoCatalogReader();
        containerCatalogReader = new OracleContainerCatalogReader();
        containerSession = new OracleContainerSession();
    }

    public OracleSourceValidation validate(
            Connection connection, ResolvedConfiguration configuration)
            throws SQLException {
        OracleDatabaseContext context = databaseInspector.inspect(connection);
        context.validateSupportedSource();
        validateDictionaryAccess(connection);
        OracleContainerRegistry containerRegistry =
                containerCatalogReader.read(connection, context);
        validatePdbDictionaryAccess(
                connection, context, containerRegistry);
        OracleRedoCatalog catalog = catalogReader.read(
                connection, configuration.redoPathMapper(), context);
        if (catalog.onlineLogs().isEmpty()) {
            throw new ConfigurationException(10006,
                    "Oracle did not return any online redo files");
        }

        Set<Path> localFiles = new LinkedHashSet<>();
        for (OracleRedoLog archived : catalog.archivedLogs()) {
            verifyReadable(archived.oraclePath(), archived.localPath());
            localFiles.add(archived.localPath());
        }
        for (OracleRedoLog online : catalog.onlineLogs()) {
            verifyReadable(online.oraclePath(), online.localPath());
            localFiles.add(online.localPath());
        }
        return new OracleSourceValidation(
                context, containerRegistry, List.copyOf(localFiles));
    }

    private void validatePdbDictionaryAccess(
            Connection connection,
            OracleDatabaseContext context,
            OracleContainerRegistry containerRegistry) throws SQLException {
        if (!context.containerDatabase()
                || !OracleContainer.ROOT_NAME.equals(
                        context.containerName())) {
            return;
        }
        try {
            for (OracleContainer container : containerRegistry.containers()) {
                if (container.name().equals(context.containerName())) {
                    continue;
                }
                containerSession.switchTo(connection, container.name());
                validateLocalDictionaryAccess(connection);
            }
        } finally {
            containerSession.switchTo(connection, context.containerName());
        }
    }

    private static void validateDictionaryAccess(Connection connection)
            throws SQLException {
        validateLocalDictionaryAccess(connection);
        for (String view : REQUIRED_DYNAMIC_VIEWS) {
            validateQuery(connection,
                    "SELECT 1 FROM SYS." + view + " WHERE ROWNUM = 0");
        }
    }

    private static void validateLocalDictionaryAccess(Connection connection)
            throws SQLException {
        validateQuery(connection, CONTAINER_NAME_SQL);
        validateQuery(connection, CHARACTER_SET_SQL);
        for (String table : REQUIRED_DICTIONARY_TABLES) {
            validateQuery(connection,
                    "SELECT 1 FROM SYS." + table + " WHERE ROWNUM = 0");
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

}
