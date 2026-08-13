/*
 * Java translation derived from OpenLogReplicator: src/common/types/Xid.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import io.github.arlowen.redoreplicator.error.DataException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Xid implements Comparable<Xid> {
    private static final Pattern COMPACT = Pattern.compile("([0-9A-Fa-f]{4})([0-9A-Fa-f]{4})([0-9A-Fa-f]{8})");
    private static final Pattern DOTTED = Pattern.compile("([0-9A-Fa-f]{4})\\.([0-9A-Fa-f]{3,4})\\.([0-9A-Fa-f]{8})");
    private static final Pattern PREFIXED = Pattern.compile("0x([0-9A-Fa-f]{4})\\.([0-9A-Fa-f]{3,4})\\.([0-9A-Fa-f]{8})");
    private static final Xid ZERO = new Xid(0);

    private final long value;

    private Xid(long value) {
        this.value = value;
    }

    public static Xid of(long rawValue) {
        if (rawValue == 0) {
            return ZERO;
        }
        return new Xid(rawValue);
    }

    public static Xid of(int undoSegment, int slot, long sequence) {
        if (undoSegment < Short.MIN_VALUE || undoSegment > 0xFFFF) {
            throw new IllegalArgumentException("Undo segment must fit 16 bits");
        }
        if (slot < 0 || slot > 0xFFFF) {
            throw new IllegalArgumentException("Transaction slot must be an unsigned 16-bit value");
        }
        if (sequence < 0 || sequence > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Transaction sequence must be an unsigned 32-bit value");
        }

        long rawValue = ((long) undoSegment & 0xFFFFL) << 48;
        rawValue |= ((long) slot & 0xFFFFL) << 32;
        rawValue |= sequence;
        return of(rawValue);
    }

    public static Xid parse(String text) {
        Matcher matcher = COMPACT.matcher(text);
        if (!matcher.matches()) {
            matcher = DOTTED.matcher(text);
        }
        if (!matcher.matches()) {
            matcher = PREFIXED.matcher(text);
        }
        if (!matcher.matches()) {
            throw new DataException(20002, "bad XID value: " + text);
        }

        int undoSegment = Integer.parseUnsignedInt(matcher.group(1), 16);
        int slot = Integer.parseUnsignedInt(matcher.group(2), 16);
        long sequence = Long.parseUnsignedLong(matcher.group(3), 16);
        return of(undoSegment, slot, sequence);
    }

    public static Xid zero() {
        return ZERO;
    }

    public long rawValue() {
        return value;
    }

    public boolean isEmpty() {
        return value == 0;
    }

    public short undoSegment() {
        return (short) (value >>> 48);
    }

    public int unsignedUndoSegment() {
        return (int) ((value >>> 48) & 0xFFFFL);
    }

    public int slot() {
        return (int) ((value >>> 32) & 0xFFFFL);
    }

    public long sequence() {
        return value & 0xFFFF_FFFFL;
    }

    @Override
    public int compareTo(Xid other) {
        return Long.compareUnsigned(value, other.value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Xid xid)) {
            return false;
        }
        return value == xid.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return String.format("0x%04x.%03x.%08x", unsignedUndoSegment(), slot(), sequence());
    }
}
