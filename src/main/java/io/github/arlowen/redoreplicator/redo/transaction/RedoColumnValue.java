/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.schema.OracleColumnType;

import java.util.Arrays;
import java.util.Objects;

public final class RedoColumnValue {
    private final OracleColumnType type;
    private final long charsetId;
    private final byte[] data;
    private final boolean nullValue;

    private RedoColumnValue(
            OracleColumnType type, long charsetId, byte[] data,
            boolean nullValue) {
        this.type = Objects.requireNonNull(type, "type");
        this.charsetId = charsetId;
        this.data = data.clone();
        this.nullValue = nullValue;
    }

    public static RedoColumnValue of(
            OracleColumnType type, byte[] data) {
        return of(type, 0, data);
    }

    public static RedoColumnValue of(
            OracleColumnType type, long charsetId, byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) {
            throw new IllegalArgumentException(
                    "A non-null redo column value requires data");
        }
        return new RedoColumnValue(type, charsetId, data, false);
    }

    public static RedoColumnValue nullValue(OracleColumnType type) {
        return nullValue(type, 0);
    }

    public static RedoColumnValue nullValue(
            OracleColumnType type, long charsetId) {
        return new RedoColumnValue(type, charsetId, new byte[0], true);
    }

    public OracleColumnType type() {
        return type;
    }

    public long charsetId() {
        return charsetId;
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
        if (!(other instanceof RedoColumnValue value)) {
            return false;
        }
        return nullValue == value.nullValue
                && charsetId == value.charsetId
                && type == value.type
                && Arrays.equals(data, value.data);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Long.hashCode(charsetId);
        result = 31 * result + Arrays.hashCode(data);
        return 31 * result + Boolean.hashCode(nullValue);
    }
}
