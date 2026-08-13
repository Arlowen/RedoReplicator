/*
 * Java translation derived from OpenLogReplicator
 * src/common/types/Data.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

public final class Data {
    private static final long UNIX_AD1970_01_01 = 62_167_132_800L;
    private static final long UNIX_BC1970_01_01 =
            UNIX_AD1970_01_01 - 365L * 24 * 60 * 60;
    private static final long UNIX_BC4712_01_01 = -210_831_897_600L;
    private static final long UNIX_AD9999_12_31 = 253_402_300_799L;
    private static final String MAP64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final long[] CUMULATIVE_DAYS = {
            0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
    private static final long[] CUMULATIVE_LEAP_DAYS = {
            0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335};

    private Data() {
    }

    public static char map10(int value) {
        return (char) ('0' + value);
    }

    public static char map16(int value) {
        if (value < 10) {
            return (char) ('0' + value);
        }
        return (char) ('a' + value - 10);
    }

    public static char map16Upper(int value) {
        if (value < 10) {
            return (char) ('0' + value);
        }
        return (char) ('A' + value - 10);
    }

    public static char map64(int value) {
        return MAP64.charAt(value);
    }

    public static int map64Reverse(int value) {
        int mapped = MAP64.indexOf(value);
        if (mapped < 0) {
            return 0;
        }
        return mapped;
    }

    public static OptionalLong parseTimezone(String value) {
        String normalized = normalizeTimezone(value);
        long result;
        if (normalized.length() == 5) {
            if (!digit(normalized, 1)
                    || normalized.charAt(2) != ':'
                    || !digit(normalized, 3)
                    || !digit(normalized, 4)) {
                return OptionalLong.empty();
            }
            result = -(normalized.charAt(1) - '0') * 3_600L
                    + (normalized.charAt(3) - '0') * 60L
                    + normalized.charAt(4) - '0';
        } else if (normalized.length() == 6) {
            if (!digit(normalized, 1)
                    || !digit(normalized, 2)
                    || normalized.charAt(3) != ':'
                    || !digit(normalized, 4)
                    || !digit(normalized, 5)) {
                return OptionalLong.empty();
            }
            result = -(normalized.charAt(1) - '0') * 36_000L
                    + (normalized.charAt(2) - '0') * 3_600L
                    + (normalized.charAt(4) - '0') * 60L
                    + normalized.charAt(5) - '0';
        } else {
            return OptionalLong.empty();
        }

        if (normalized.charAt(0) == '-') {
            result = -result;
        } else if (normalized.charAt(0) != '+') {
            return OptionalLong.empty();
        }
        return OptionalLong.of(result);
    }

    public static String timezoneToString(long timezoneSeconds) {
        long value = timezoneSeconds;
        char sign = '+';
        if (value < 0) {
            sign = '-';
            value = -value;
        }
        value /= 60;
        long minutes = value % 60;
        long hours = value / 60;
        return String.format("%c%02d:%02d", sign, hours, minutes);
    }

    public static long valuesToEpoch(
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second,
            int timezoneSeconds) {
        long result;
        if (year > 0) {
            result = yearToDays(year, month)
                    + CUMULATIVE_DAYS[month % 12] + day;
            result = timeOfDay(result, hour, minute, second);
            return result - UNIX_AD1970_01_01 - timezoneSeconds;
        }
        result = -yearToDaysBc(-year, month)
                + CUMULATIVE_DAYS[month % 12] + day;
        result = timeOfDay(result, hour, minute, second);
        return result - UNIX_BC1970_01_01 - timezoneSeconds;
    }

    public static String epochToIso8601(
            long epochSeconds, boolean addT, boolean addZ) {
        if (epochSeconds < UNIX_BC4712_01_01
                || epochSeconds > UNIX_AD9999_12_31) {
            throw new RedoRuntimeException(
                    10069, "invalid timestamp value: " + epochSeconds);
        }
        long timestamp = epochSeconds + UNIX_AD1970_01_01;
        if (timestamp >= 365L * 24 * 60 * 60) {
            return formatAd(timestamp, addT, addZ);
        }
        return formatBc(timestamp, addT, addZ);
    }

    public static byte[] escapeValue(byte[] value) {
        ByteArrayOutputStream escaped = new ByteArrayOutputStream(
                value.length);
        for (byte current : value) {
            int unsigned = current & 0xFF;
            switch (unsigned) {
                case '\t' -> escaped.writeBytes(new byte[]{'\\', 't'});
                case '\r' -> escaped.writeBytes(new byte[]{'\\', 'r'});
                case '\n' -> escaped.writeBytes(new byte[]{'\\', 'n'});
                case '\b' -> escaped.writeBytes(new byte[]{'\\', 'b'});
                case '\f' -> escaped.writeBytes(new byte[]{'\\', 'f'});
                case '"', '\\' -> {
                    escaped.write('\\');
                    escaped.write(unsigned);
                }
                default -> {
                    if (unsigned < 32) {
                        escaped.writeBytes(new byte[]{
                                '\\', 'u', '0', '0',
                                (byte) map16(unsigned >> 4),
                                (byte) map16(unsigned & 0x0F)});
                    } else {
                        escaped.write(unsigned);
                    }
                }
            }
        }
        return escaped.toByteArray();
    }

    public static void checkName(String name) {
        checkName(name.getBytes(StandardCharsets.UTF_8));
    }

    public static void checkName(byte[] name) {
        if (name.length >= 1_024) {
            throw new DataException(
                    20004, "identifier is too long");
        }
    }

    private static String normalizeTimezone(String timezone) {
        return switch (timezone) {
            case "Etc/GMT-14" -> "-14:00";
            case "Etc/GMT-13" -> "-13:00";
            case "Etc/GMT-12" -> "-12:00";
            case "Etc/GMT-11" -> "-11:00";
            case "HST", "Etc/GMT-10" -> "-10:00";
            case "Etc/GMT-9" -> "-09:00";
            case "PST", "PST8PDT", "Etc/GMT-8" -> "-08:00";
            case "MST", "MST7MDT", "Etc/GMT-7" -> "-07:00";
            case "CST", "CST6CDT", "Etc/GMT-6" -> "-06:00";
            case "EST", "EST5EDT", "Etc/GMT-5" -> "-05:00";
            case "Etc/GMT-4" -> "-04:00";
            case "Etc/GMT-3" -> "-03:00";
            case "Etc/GMT-2" -> "-02:00";
            case "Etc/GMT-1" -> "-01:00";
            case "GMT", "Etc/GMT", "Greenwich", "Etc/Greenwich",
                 "GMT0", "Etc/GMT0", "GMT+0", "Etc/GMT-0",
                 "Etc/GMT+0", "UTC", "Etc/UTC", "UCT", "Etc/UCT",
                 "Universal", "Etc/Universal", "WET" -> "+00:00";
            case "MET", "CET", "Etc/GMT+1" -> "+01:00";
            case "EET", "Etc/GMT+2" -> "+02:00";
            case "Etc/GMT+3" -> "+03:00";
            case "Etc/GMT+4" -> "+04:00";
            case "Etc/GMT+5" -> "+05:00";
            case "Etc/GMT+6" -> "+06:00";
            case "Etc/GMT+7" -> "+07:00";
            case "PRC", "ROC", "Etc/GMT+8" -> "+08:00";
            case "Etc/GMT+9" -> "+09:00";
            case "Etc/GMT+10" -> "+10:00";
            case "Etc/GMT+11" -> "+11:00";
            case "Etc/GMT+12" -> "+12:00";
            default -> timezone;
        };
    }

    private static String formatAd(
            long timestamp, boolean addT, boolean addZ) {
        long second = timestamp % 60;
        timestamp /= 60;
        long minute = timestamp % 60;
        timestamp /= 60;
        long hour = timestamp % 24;
        timestamp /= 24;

        long year = timestamp / 365 + 1;
        long firstDay = yearToDays(year, 0);
        while (firstDay > timestamp) {
            year--;
            firstDay = yearToDays(year, 0);
        }
        long day = timestamp - firstDay;
        int month = (int) Math.min(day / 27, 11);
        long[] cumulative = leapYear(year)
                ? CUMULATIVE_LEAP_DAYS : CUMULATIVE_DAYS;
        while (cumulative[month] > day) {
            month--;
        }
        day -= cumulative[month];
        return formatDate(
                false, year, month + 1, day + 1,
                hour, minute, second, addT, addZ);
    }

    private static String formatBc(
            long timestamp, boolean addT, boolean addZ) {
        timestamp = 365L * 24 * 60 * 60 - timestamp;
        long second = timestamp % 60;
        timestamp /= 60;
        long minute = timestamp % 60;
        timestamp /= 60;
        long hour = timestamp % 24;
        timestamp /= 24;

        long year = Math.max(timestamp / 366 - 1, 0);
        long firstDay = yearToDaysBc(year, 0);
        while (firstDay < timestamp) {
            year++;
            firstDay = yearToDaysBc(year, 0);
        }
        long day = firstDay - timestamp;
        int month = (int) Math.min(day / 27, 11);
        long[] cumulative = leapYear(year)
                ? CUMULATIVE_LEAP_DAYS : CUMULATIVE_DAYS;
        while (cumulative[month] > day) {
            month--;
        }
        day -= cumulative[month];
        return formatDate(
                true, year, month + 1, day + 1,
                hour, minute, second, addT, addZ);
    }

    private static String formatDate(
            boolean bc,
            long year,
            long month,
            long day,
            long hour,
            long minute,
            long second,
            boolean addT,
            boolean addZ) {
        String separator = addT ? "T" : " ";
        String suffix = addZ ? "Z" : "";
        String sign = bc ? "-" : "";
        return String.format(
                "%s%04d-%02d-%02d%s%02d:%02d:%02d%s",
                sign, year, month, day, separator,
                hour, minute, second, suffix);
    }

    private static long yearToDays(long year, long month) {
        long result = year * 365 + year / 4 - year / 100 + year / 400;
        if (leapYear(year) && month < 2) {
            result--;
        }
        return result;
    }

    private static long yearToDaysBc(long year, long month) {
        long result = year * 365 + year / 4 - year / 100 + year / 400;
        if (leapYear(year) && month >= 2) {
            result--;
        }
        return result;
    }

    private static long timeOfDay(
            long days, long hour, long minute, long second) {
        return ((days * 24 + hour) * 60 + minute) * 60 + second;
    }

    private static boolean leapYear(long year) {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    private static boolean digit(String value, int index) {
        char current = value.charAt(index);
        return current >= '0' && current <= '9';
    }
}
