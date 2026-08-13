/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import io.github.arlowen.redoreplicator.schema.DdlSchemaChange;

import java.util.Objects;

public record RedoJsonDdlChange(DdlSchemaChange change)
        implements RedoJsonChange {
    public RedoJsonDdlChange {
        Objects.requireNonNull(change, "change");
    }
}
