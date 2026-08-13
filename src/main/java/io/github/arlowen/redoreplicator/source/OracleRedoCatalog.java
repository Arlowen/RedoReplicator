/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import java.util.List;
import java.util.Objects;

public record OracleRedoCatalog(
        OracleDatabaseContext databaseContext,
        List<OracleRedoLog> archivedLogs,
        List<OracleRedoLog> onlineLogs) {
    public OracleRedoCatalog {
        Objects.requireNonNull(databaseContext, "databaseContext");
        archivedLogs = List.copyOf(archivedLogs);
        onlineLogs = List.copyOf(onlineLogs);
    }
}
