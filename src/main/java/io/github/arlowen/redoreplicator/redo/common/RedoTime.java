/*
 * Java translation derived from OpenLogReplicator: src/common/types/Time.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

public final class RedoTime {
    private static final RedoTime ZERO = new RedoTime(0);

    private final int value;

    private RedoTime(int value) {
        this.value = value;
    }

    public static RedoTime of(long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Redo time must be an unsigned 32-bit value");
        }
        if (value == 0) {
            return ZERO;
        }
        return new RedoTime((int) value);
    }

    public static RedoTime zero() {
        return ZERO;
    }

    public long value() {
        return Integer.toUnsignedLong(value);
    }

    public long toEpochSeconds(long hostTimezoneSeconds) {
        long rest = value();
        long second = rest % 60;
        rest /= 60;
        long minute = rest % 60;
        rest /= 60;
        long hour = rest % 24;
        rest /= 24;
        long day = rest % 31 + 1;
        rest /= 31;
        long month = rest % 12 + 1;
        rest /= 12;
        long year = rest + 1988;

        if (month <= 2) {
            month += 10;
            year -= 1;
        } else {
            month -= 2;
        }

        long days = year / 4 - year / 100 + year / 400 + 367 * month / 12 + day;
        days += year * 365 - 719_499;
        return ((((days * 24) + hour) * 60) + minute) * 60 + second - hostTimezoneSeconds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RedoTime redoTime)) {
            return false;
        }
        return value == redoTime.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        long rest = value();
        long second = rest % 60;
        rest /= 60;
        long minute = rest % 60;
        rest /= 60;
        long hour = rest % 24;
        rest /= 24;
        long day = rest % 31 + 1;
        rest /= 31;
        long month = rest % 12 + 1;
        rest /= 12;
        long year = rest + 1988;
        return String.format("%02d/%02d/%d %02d:%02d:%02d", month, day, year, hour, minute, second);
    }
}
