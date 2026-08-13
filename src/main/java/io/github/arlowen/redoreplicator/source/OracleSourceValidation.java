/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record OracleSourceValidation(
        OracleDatabaseContext databaseContext,
        OracleContainerRegistry containerRegistry,
        List<Path> redoFiles) {
    public OracleSourceValidation {
        Objects.requireNonNull(databaseContext, "databaseContext");
        Objects.requireNonNull(containerRegistry, "containerRegistry");
        redoFiles = List.copyOf(redoFiles);
    }
}
