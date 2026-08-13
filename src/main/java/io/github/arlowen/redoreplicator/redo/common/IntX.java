/*
 * Java translation derived from OpenLogReplicator: src/common/types/IntX.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import java.math.BigInteger;

public final class IntX {
    public static final int LENGTH = 2;
    public static final int DIGITS = 39;

    private static final BigInteger MASK_128 = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    private static final BigInteger MASK_64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final IntX ZERO = new IntX(0, 0);

    private final long low;
    private final long high;

    private IntX(long low, long high) {
        this.low = low;
        this.high = high;
    }

    public static IntX zero() {
        return ZERO;
    }

    public static IntX of(long low) {
        return of(low, 0);
    }

    public static IntX of(long low, long high) {
        if (low == 0 && high == 0) {
            return ZERO;
        }
        return new IntX(low, high);
    }

    public static IntX parseDecimal(String text) {
        if (text.length() > DIGITS) {
            throw new IllegalArgumentException("incorrect conversion of string: " + text);
        }
        for (int index = 0; index < text.length(); index++) {
            char digit = text.charAt(index);
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException("incorrect conversion of string: " + text.substring(index));
            }
        }

        if (text.isEmpty()) {
            return ZERO;
        }
        BigInteger value = new BigInteger(text).and(MASK_128);
        long low = value.and(MASK_64).longValue();
        long high = value.shiftRight(64).longValue();
        return of(low, high);
    }

    public IntX plus(IntX other) {
        long resultLow = low + other.low;
        long carry = 0;
        if (Long.compareUnsigned(resultLow, low) < 0) {
            carry = 1;
        }
        return of(resultLow, high + other.high + carry);
    }

    public long low() {
        return low;
    }

    public long high() {
        return high;
    }

    public boolean isSet64(long mask) {
        return (low & mask) != 0;
    }

    public boolean isZero() {
        return low == 0 && high == 0;
    }

    public BigInteger toUnsignedBigInteger() {
        BigInteger upper = new BigInteger(Long.toUnsignedString(high)).shiftLeft(64);
        return upper.add(new BigInteger(Long.toUnsignedString(low)));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IntX intX)) {
            return false;
        }
        return low == intX.low && high == intX.high;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(low);
        return 31 * result + Long.hashCode(high);
    }

    @Override
    public String toString() {
        return "[" + Long.toUnsignedString(low) + "," + Long.toUnsignedString(high) + "]";
    }
}
