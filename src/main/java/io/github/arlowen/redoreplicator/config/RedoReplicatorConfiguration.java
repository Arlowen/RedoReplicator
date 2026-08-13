/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.config;

public record RedoReplicatorConfiguration(
        DatabaseConfiguration database,
        CaptureConfiguration capture,
        OutputConfiguration output,
        StateConfiguration state,
        ArchiveConfiguration archive,
        LoggingConfiguration logging) {
}
