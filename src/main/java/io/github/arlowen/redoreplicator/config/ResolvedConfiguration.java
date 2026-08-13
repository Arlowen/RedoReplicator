/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.config;

import java.nio.file.Path;
import java.util.List;

public record ResolvedConfiguration(
        RedoReplicatorConfiguration configuration,
        Path installationDirectory,
        Path configurationFile,
        Path outputDirectory,
        Path stateDirectory,
        Path transactionSpillDirectory,
        long transactionMemoryBytes,
        TableFilter tableFilter,
        RedoPathMapper redoPathMapper,
        List<String> warnings) {
    public ResolvedConfiguration {
        warnings = List.copyOf(warnings);
    }
}
