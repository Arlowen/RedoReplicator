/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

public enum SchemaSource {
    INITIAL("initial"),
    FLASHBACK("flashback"),
    REDO("redo");

    private final String databaseValue;

    SchemaSource(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static SchemaSource fromDatabaseValue(String value) {
        for (SchemaSource source : values()) {
            if (source.databaseValue.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown schema source: " + value);
    }
}
