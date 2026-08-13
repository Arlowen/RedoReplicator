/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import java.util.Arrays;
import java.util.Objects;

public final class SystemDictionaryValue {
    private final OracleColumnType type;
    private final byte[] data;
    private final boolean nullValue;

    private SystemDictionaryValue(OracleColumnType type, byte[] data,
                                  boolean nullValue) {
        this.type = Objects.requireNonNull(type, "type");
        this.data = data.clone();
        this.nullValue = nullValue;
    }

    public static SystemDictionaryValue of(OracleColumnType type, byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) {
            throw new IllegalArgumentException("A non-null dictionary value requires data");
        }
        return new SystemDictionaryValue(type, data, false);
    }

    public static SystemDictionaryValue nullValue(OracleColumnType type) {
        return new SystemDictionaryValue(type, new byte[0], true);
    }

    public OracleColumnType type() {
        return type;
    }

    public byte[] data() {
        return data.clone();
    }

    public boolean nullValue() {
        return nullValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SystemDictionaryValue value)) {
            return false;
        }
        return nullValue == value.nullValue
                && type == value.type
                && Arrays.equals(data, value.data);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Arrays.hashCode(data);
        return 31 * result + Boolean.hashCode(nullValue);
    }
}
