/*
 * Java translation derived from OpenLogReplicator: src/common/types/Seq.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

public final class Seq implements Comparable<Seq> {
    private static final int NONE_VALUE = 0xFFFF_FFFF;
    private static final Seq NONE = new Seq(NONE_VALUE);
    private static final Seq ZERO = new Seq(0);

    private final int value;

    private Seq(int value) {
        this.value = value;
    }

    public static Seq of(long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Redo sequence must be an unsigned 32-bit value");
        }
        if (value == 0) {
            return ZERO;
        }
        if (value == 0xFFFF_FFFFL) {
            return NONE;
        }
        return new Seq((int) value);
    }

    public static Seq none() {
        return NONE;
    }

    public static Seq zero() {
        return ZERO;
    }

    public long value() {
        return Integer.toUnsignedLong(value);
    }

    public Seq next() {
        return of(Integer.toUnsignedLong(value + 1));
    }

    public String toHex(int width) {
        return "0x" + String.format("%0" + width + "x", value());
    }

    @Override
    public int compareTo(Seq other) {
        return Integer.compareUnsigned(value, other.value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Seq seq)) {
            return false;
        }
        return value == seq.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return Integer.toUnsignedString(value);
    }
}
