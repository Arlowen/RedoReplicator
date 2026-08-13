/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import java.nio.file.Path;
import java.util.List;

public record OracleSourceValidation(
        OracleDatabaseContext databaseContext,
        List<Path> redoFiles) {
    public OracleSourceValidation {
        redoFiles = List.copyOf(redoFiles);
    }
}
