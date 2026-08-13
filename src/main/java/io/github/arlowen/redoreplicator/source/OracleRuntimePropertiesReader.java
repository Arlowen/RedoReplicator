/*
 * Java translation derived from OpenLogReplicator database character-set and
 * timezone initialization in src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.charset.Locales;
import io.github.arlowen.redoreplicator.error.ConfigurationException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;

public final class OracleRuntimePropertiesReader {
    private static final String CHARACTER_SET = """
            SELECT PROPERTY_VALUE, NLS_CHARSET_ID(PROPERTY_VALUE)
              FROM DATABASE_PROPERTIES
             WHERE PROPERTY_NAME = ?
            """;
    private static final String DATABASE_TIME_ZONE =
            "SELECT DBTIMEZONE FROM DUAL";

    public OracleRuntimeProperties read(Connection connection)
            throws SQLException {
        OracleCharacterSet database = readCharacterSet(
                connection, "NLS_CHARACTERSET");
        OracleCharacterSet national = readCharacterSet(
                connection, "NLS_NCHAR_CHARACTERSET");
        Locales locales = new Locales();
        validateCharacterSet(locales, database);
        validateCharacterSet(locales, national);
        Charset charset;
        if (database.name().equals("AL32UTF8")) {
            charset = StandardCharsets.UTF_8;
        } else if (database.name().equals("ZHS16GBK")) {
            charset = Charset.forName("GBK");
        } else if (database.name().equals("WE8MSWIN1252")) {
            charset = Charset.forName("windows-1252");
        } else {
            throw new ConfigurationException(10002,
                    "Oracle database character set is not translated: "
                            + database.name());
        }
        return new OracleRuntimeProperties(
                database.id(), national.id(), charset, locales,
                readDatabaseTimeZone(connection));
    }

    private static void validateCharacterSet(
            Locales locales, OracleCharacterSet characterSet) {
        try {
            locales.require(characterSet.id(), characterSet.name());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(10002, e.getMessage());
        }
    }

    private static OracleCharacterSet readCharacterSet(
            Connection connection, String property) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                CHARACTER_SET)) {
            statement.setString(1, property);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "Oracle database property is missing: " + property);
                }
                OracleCharacterSet result = new OracleCharacterSet(
                        resultSet.getString(1), resultSet.getLong(2));
                if (resultSet.next()) {
                    throw new SQLException(
                            "Oracle returned duplicate database property: "
                                    + property);
                }
                return result;
            }
        }
    }

    private static ZoneId readDatabaseTimeZone(Connection connection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                DATABASE_TIME_ZONE);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException(
                        "Oracle did not return the database timezone");
            }
            return ZoneId.of(resultSet.getString(1));
        }
    }
}
