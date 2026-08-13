/*
 * Java translation derived from OpenLogReplicator
 * src/replicator/DatabaseEnvironment.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.config.DatabaseConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class OracleConnectionFactory {
    private final DatabaseConfiguration configuration;

    public OracleConnectionFactory(DatabaseConfiguration configuration) {
        this.configuration = Objects.requireNonNull(
                configuration, "configuration");
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(
                configuration.url(),
                configuration.username(),
                configuration.password());
    }
}
