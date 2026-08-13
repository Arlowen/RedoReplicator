/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class TableSchemaJsonCodec {
    private final ObjectMapper objectMapper;

    public TableSchemaJsonCodec() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public String write(TableSchema schema) throws JsonProcessingException {
        return objectMapper.writeValueAsString(schema);
    }

    public TableSchema read(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, TableSchema.class);
    }
}
