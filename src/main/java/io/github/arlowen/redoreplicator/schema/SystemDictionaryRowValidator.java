/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;

import java.util.Map;

final class SystemDictionaryRowValidator {
    void validateInsert(SystemDictionaryRow row,
                        Map<String, SystemDictionaryValue> values) {
        boolean complete = switch (row.dictionaryTable()) {
            case USER -> present(values, "USER#", "NAME");
            case OBJECT -> present(values, "OWNER#", "OBJ#", "TYPE#", "NAME");
            case TABLE -> present(values, "OBJ#");
            case COLUMN -> present(values, "OBJ#", "INTCOL#", "NAME", "TYPE#");
            case CONSTRAINT -> present(values, "CON#", "OBJ#", "TYPE#");
            case CONSTRAINT_COLUMN -> present(values, "CON#", "INTCOL#", "OBJ#");
        };
        if (!complete) {
            throw new DataException(50020,
                    "Incomplete inserted row for "
                            + row.dictionaryTable().qualifiedName()
                            + " at ROWID " + row.rowId());
        }
    }

    private static boolean present(Map<String, SystemDictionaryValue> values,
                                   String... columns) {
        for (String column : columns) {
            SystemDictionaryValue value = values.get(column);
            if (value == null || value.nullValue()) {
                return false;
            }
        }
        return true;
    }
}
