/*
 * Java translation derived from OpenLogReplicator: src/common/types/Scn.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import java.io.Serial;
import java.io.Serializable;

public final class Scn implements Comparable<Scn>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final long NONE_VALUE = 0xFFFF_FFFF_FFFF_FFFFL;
    private static final Scn NONE = new Scn(NONE_VALUE);
    private static final Scn ZERO = new Scn(0);

    private final long value;

    private Scn(long value) {
        this.value = value;
    }

    public static Scn of(long rawValue) {
        if (rawValue == NONE_VALUE) {
            return NONE;
        }
        if (rawValue == 0) {
            return ZERO;
        }
        return new Scn(rawValue);
    }

    public static Scn none() {
        return NONE;
    }

    public static Scn zero() {
        return ZERO;
    }

    public static Scn fromLittleEndian(int... bytes) {
        if (bytes.length != 6 && bytes.length != 8) {
            throw new IllegalArgumentException("SCN requires 6 or 8 bytes");
        }

        long rawValue = 0;
        for (int index = 0; index < bytes.length; index++) {
            rawValue |= ((long) bytes[index] & 0xFFL) << (index * 8);
        }
        return of(rawValue);
    }

    public static Scn fromWords(long highWord, long lowWord) {
        return of(((highWord & 0xFFFF_FFFFL) << 32) | (lowWord & 0xFFFF_FFFFL));
    }

    public long rawValue() {
        return value;
    }

    public boolean isNone() {
        return value == NONE_VALUE;
    }

    public String to48() {
        return String.format("0x%04x.%08x", (value >>> 32) & 0xFFFFL, value & 0xFFFF_FFFFL);
    }

    public String to64() {
        return String.format("0x%016x", value);
    }

    public String to64D() {
        return String.format("0x%04x.%04x.%08x",
                (value >>> 48) & 0xFFFFL,
                (value >>> 32) & 0xFFFFL,
                value & 0xFFFF_FFFFL);
    }

    public String toHex12() {
        return String.format("0x%012x", value);
    }

    public String toHex16() {
        return String.format("0x%016x", value & 0xFFFF_7FFF_FFFF_FFFFL);
    }

    public String toDecimalString() {
        return Long.toUnsignedString(value);
    }

    @Override
    public int compareTo(Scn other) {
        return Long.compareUnsigned(value, other.value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Scn scn)) {
            return false;
        }
        return value == scn.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return toDecimalString();
    }
}
