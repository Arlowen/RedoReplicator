/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import java.util.List;
import java.util.Objects;

public record RewindSourceValidation(
        RedoPosition position,
        List<TableSchemaVersion> schemaVersions) {
    public RewindSourceValidation {
        Objects.requireNonNull(position, "position");
        schemaVersions = List.copyOf(schemaVersions);
    }
}
