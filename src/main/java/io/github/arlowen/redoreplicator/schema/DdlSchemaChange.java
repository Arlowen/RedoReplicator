/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.Scn;

import java.util.Objects;

public record DdlSchemaChange(String container, String owner, String table,
                              int oracleType, String ddlText, Scn commitScn) {
    public DdlSchemaChange {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(ddlText, "ddlText");
        Objects.requireNonNull(commitScn, "commitScn");
    }

    public DdlOperation operation() {
        return DdlOperation.fromOracleCode(oracleType);
    }

    public String qualifiedName() {
        return container + "." + owner + "." + table;
    }
}
