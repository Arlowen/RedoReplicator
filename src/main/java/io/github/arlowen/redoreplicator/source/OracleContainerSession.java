/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.regex.Pattern;

public final class OracleContainerSession {
    private static final Pattern CONTAINER_NAME = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_$#]{0,127}");

    public void switchTo(Connection connection, String container)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(container, "container");
        if (!CONTAINER_NAME.matcher(container).matches()) {
            throw new IllegalArgumentException(
                    "Invalid Oracle container name " + container);
        }
        String sql = "ALTER SESSION SET CONTAINER = \"" + container + "\"";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
