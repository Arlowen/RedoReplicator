/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;

import java.util.Objects;

public record TableSchemaVersion(String container, String owner, String table,
                                 long objectId, long dataObjectId, Scn effectiveScn,
                                 String schemaJson, String ddlType, String ddlText,
                                 SchemaSource source, boolean dropTombstone) {
    public TableSchemaVersion {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(effectiveScn, "effectiveScn");
        Objects.requireNonNull(schemaJson, "schemaJson");
        Objects.requireNonNull(ddlType, "ddlType");
        Objects.requireNonNull(ddlText, "ddlText");
        Objects.requireNonNull(source, "source");
    }

    public static TableSchemaVersion initial(TableSchema schema, Scn effectiveScn,
                                             TableSchemaJsonCodec jsonCodec)
            throws JsonProcessingException {
        return new TableSchemaVersion(
                schema.container(),
                schema.owner(),
                schema.name(),
                schema.objectId(),
                schema.dataObjectId(),
                effectiveScn,
                jsonCodec.write(schema),
                "INITIAL",
                "",
                SchemaSource.INITIAL,
                false);
    }

    public TableSchema decode(TableSchemaJsonCodec jsonCodec)
            throws JsonProcessingException {
        return jsonCodec.read(schemaJson);
    }
}
