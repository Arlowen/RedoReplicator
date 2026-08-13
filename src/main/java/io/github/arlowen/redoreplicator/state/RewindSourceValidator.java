/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.source.OracleDatabaseContext;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface RewindSourceValidator {
    RewindSourceValidation validate(
            Connection connection,
            ResolvedConfiguration configuration,
            OracleDatabaseContext databaseContext,
            Scn targetScn) throws IOException, SQLException;
}
