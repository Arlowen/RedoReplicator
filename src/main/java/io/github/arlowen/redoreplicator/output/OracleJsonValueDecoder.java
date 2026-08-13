/*
 * Java translation derived from OpenLogReplicator Builder::processValue in
 * src/builder/Builder.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.arlowen.redoreplicator.charset.CharacterSet;
import io.github.arlowen.redoreplicator.charset.CharacterSetJdk;
import io.github.arlowen.redoreplicator.charset.Locales;
import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.transaction.RedoColumnValue;
import io.github.arlowen.redoreplicator.redo.value.OracleNumberDecoder;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;

public final class OracleJsonValueDecoder {
    private static final BigInteger NANOS_PER_SECOND =
            BigInteger.valueOf(1_000_000_000L);
    private static final TextNode UNKNOWN = TextNode.valueOf("?");

    private final CharacterSet defaultCharacterSet;
    private final Locales locales;
    private final ZoneId databaseTimeZone;
    private final OracleNumberDecoder numberDecoder;
    private final OracleLobLocatorDecoder lobLocatorDecoder;

    public OracleJsonValueDecoder(
            Charset databaseCharacterSet, ZoneId databaseTimeZone) {
        Objects.requireNonNull(databaseCharacterSet, "databaseCharacterSet");
        defaultCharacterSet = new CharacterSetJdk(
                0, databaseCharacterSet.name(), databaseCharacterSet);
        locales = new Locales();
        this.databaseTimeZone = Objects.requireNonNull(
                databaseTimeZone, "databaseTimeZone");
        numberDecoder = new OracleNumberDecoder();
        lobLocatorDecoder = new OracleLobLocatorDecoder();
    }

    public OracleJsonValueDecoder(
            Locales locales, long databaseCharacterSetId,
            ZoneId databaseTimeZone) {
        this.locales = Objects.requireNonNull(locales, "locales");
        defaultCharacterSet = locales.require(databaseCharacterSetId);
        this.databaseTimeZone = Objects.requireNonNull(
                databaseTimeZone, "databaseTimeZone");
        numberDecoder = new OracleNumberDecoder();
        lobLocatorDecoder = new OracleLobLocatorDecoder();
    }

    public JsonNode decode(RedoColumnValue value) {
        Objects.requireNonNull(value, "value");
        if (value.nullValue()) {
            return NullNode.getInstance();
        }
        byte[] data = value.data();
        return switch (value.type()) {
            case VARCHAR, CHAR -> TextNode.valueOf(text(value, data));
            case NUMBER -> DecimalNode.valueOf(numberDecoder.decode(data));
            case DATE, TIMESTAMP -> timestamp(data, ZoneOffset.UTC);
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE -> timestamp(
                    data, databaseTimeZone);
            case TIMESTAMP_WITH_TIME_ZONE -> timestampWithTimeZone(data);
            case RAW -> TextNode.valueOf(
                    HexFormat.of().withUpperCase().formatHex(data));
            case BINARY_FLOAT -> floating(decodeFloat(data));
            case BINARY_DOUBLE -> floating(decodeDouble(data));
            case INTERVAL_YEAR_TO_MONTH -> intervalYearToMonth(data);
            case INTERVAL_DAY_TO_SECOND -> intervalDayToSecond(data);
            case UROWID -> rowId(data);
            case BOOLEAN -> oracleBoolean(data);
            case CLOB -> TextNode.valueOf(text(
                    value, lobLocatorDecoder.decodeInline(data)));
            case BLOB -> TextNode.valueOf(HexFormat.of().withUpperCase()
                    .formatHex(lobLocatorDecoder.decodeInline(data)));
            case XMLTYPE -> throw new RedoLogException(
                    50075, "LOB value requires transaction LOB reconstruction");
            case NONE, LONG, LONG_RAW, JSON -> UNKNOWN;
        };
    }

    private String text(RedoColumnValue value, byte[] data) {
        CharacterSet characterSet = defaultCharacterSet;
        if (value.charsetId() != 0) {
            characterSet = locales.require(value.charsetId());
        }
        return characterSet.decode(data);
    }

    private JsonNode timestamp(byte[] data, ZoneId zoneId) {
        if (data.length != 7 && data.length != 11) {
            return UNKNOWN;
        }
        int fraction = 0;
        if (data.length == 11) {
            fraction = readIntBigEndian(data, 7);
        }
        try {
            LocalDateTime dateTime = oracleDateTime(data, fraction);
            long epochSecond = dateTime.atZone(zoneId).toEpochSecond();
            return DecimalNode.valueOf(new BigDecimal(
                    BigInteger.valueOf(epochSecond)
                            .multiply(NANOS_PER_SECOND)
                            .add(BigInteger.valueOf(fraction))));
        } catch (DateTimeException e) {
            return UNKNOWN;
        }
    }

    private JsonNode timestampWithTimeZone(byte[] data) {
        if (data.length != 9 && data.length != 13) {
            return UNKNOWN;
        }
        int fraction = 0;
        if (data.length == 13) {
            fraction = readIntBigEndian(data, 7);
        }
        try {
            LocalDateTime dateTime = oracleDateTime(data, fraction);
            long epochSecond = dateTime.toEpochSecond(ZoneOffset.UTC);
            String zone = timeZone(data[data.length - 2] & 0xFF,
                    data[data.length - 1] & 0xFF);
            BigInteger nanos = BigInteger.valueOf(epochSecond)
                    .multiply(NANOS_PER_SECOND)
                    .add(BigInteger.valueOf(fraction));
            return TextNode.valueOf(nanos + "," + zone);
        } catch (DateTimeException e) {
            return UNKNOWN;
        }
    }

    private static LocalDateTime oracleDateTime(
            byte[] data, int fraction) {
        int century = data[0] & 0xFF;
        int yearInCentury = data[1] & 0xFF;
        int year;
        if (century >= 100 && yearInCentury >= 100) {
            year = (century - 100) * 100 + yearInCentury - 100;
        } else {
            year = -((100 - century) * 100 + 100 - yearInCentury);
        }
        int month = data[2] & 0xFF;
        int day = data[3] & 0xFF;
        int hour = (data[4] & 0xFF) - 1;
        int minute = (data[5] & 0xFF) - 1;
        int second = (data[6] & 0xFF) - 1;
        if (fraction < 0 || fraction > 999_999_999) {
            throw new DateTimeException("Invalid Oracle timestamp fraction");
        }
        return LocalDateTime.of(
                year, month, day, hour, minute, second, fraction);
    }

    private static String timeZone(int hourByte, int minuteByte) {
        if (hourByte >= 5 && hourByte <= 36) {
            int hour = hourByte - 20;
            int minute = minuteByte - 60;
            int totalMinutes = hour * 60 + minute;
            ZoneOffset offset = ZoneOffset.ofTotalSeconds(totalMinutes * 60);
            if (offset.equals(ZoneOffset.UTC)) {
                return "+00:00";
            }
            return offset.toString();
        }
        return "TZ?";
    }

    private static JsonNode intervalYearToMonth(byte[] data) {
        if (data.length != 5) {
            return UNKNOWN;
        }
        long encodedYear = Integer.toUnsignedLong(readIntBigEndian(data, 0));
        boolean negative = encodedYear < 0x8000_0000L
                || (data[4] & 0xFF) < 60;
        long years;
        if (encodedYear >= 0x8000_0000L) {
            years = encodedYear - 0x8000_0000L;
        } else {
            years = 0x8000_0000L - encodedYear;
        }
        int monthByte = data[4] & 0xFF;
        int months = Math.abs(monthByte - 60);
        if (years > 999_999_999L || months > 11) {
            return UNKNOWN;
        }
        long total = years * 12 + months;
        if (negative) {
            total = -total;
        }
        return DecimalNode.valueOf(BigDecimal.valueOf(total));
    }

    private static JsonNode intervalDayToSecond(byte[] data) {
        if (data.length != 11) {
            return UNKNOWN;
        }
        long encodedDay = Integer.toUnsignedLong(readIntBigEndian(data, 0));
        long encodedNanos = Integer.toUnsignedLong(
                readIntBigEndian(data, 7));
        int hourByte = data[4] & 0xFF;
        int minuteByte = data[5] & 0xFF;
        int secondByte = data[6] & 0xFF;
        boolean negative = encodedDay < 0x8000_0000L
                || encodedNanos < 0x8000_0000L
                || hourByte < 60 || minuteByte < 60
                || secondByte < 60;
        long days = distanceFromBias(encodedDay);
        long nanos = distanceFromBias(encodedNanos);
        int hours = Math.abs(hourByte - 60);
        int minutes = Math.abs(minuteByte - 60);
        int seconds = Math.abs(secondByte - 60);
        if (days > 999_999_999L || nanos > 999_999_999L
                || hours > 23 || minutes > 59 || seconds > 59) {
            return UNKNOWN;
        }
        BigInteger value = BigInteger.valueOf(days)
                .multiply(BigInteger.valueOf(24))
                .add(BigInteger.valueOf(hours))
                .multiply(BigInteger.valueOf(60))
                .add(BigInteger.valueOf(minutes))
                .multiply(BigInteger.valueOf(60))
                .add(BigInteger.valueOf(seconds))
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(nanos));
        if (negative) {
            value = value.negate();
        }
        return DecimalNode.valueOf(new BigDecimal(value));
    }

    private static long distanceFromBias(long value) {
        if (value >= 0x8000_0000L) {
            return value - 0x8000_0000L;
        }
        return 0x8000_0000L - value;
    }

    private static JsonNode rowId(byte[] data) {
        if (data.length != 13 || data[0] != 0x01) {
            return UNKNOWN;
        }
        byte[] binary = new byte[12];
        System.arraycopy(data, 1, binary, 0, binary.length);
        return TextNode.valueOf(RowId.fromBinary(binary).toHexString());
    }

    private static JsonNode oracleBoolean(byte[] data) {
        if (data.length != 1 || data[0] < 0 || data[0] > 1) {
            return UNKNOWN;
        }
        return IntNode.valueOf(data[0]);
    }

    private static JsonNode floating(double value) {
        if (!Double.isFinite(value)) {
            return UNKNOWN;
        }
        return DoubleNode.valueOf(value);
    }

    private static double decodeFloat(byte[] data) {
        if (data.length != 4) {
            return Double.NaN;
        }
        int sign = data[0] & 0x80;
        int exponent = ((data[0] & 0x7F) << 1)
                | ((data[1] & 0xFF) >>> 7);
        int significand = ((data[1] & 0x7F) << 16)
                | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        if (sign != 0) {
            if (significand == 0 && exponent == 0) {
                return 0.0;
            }
            if (significand == 0 && exponent == 0xFF) {
                return Double.POSITIVE_INFINITY;
            }
            if (significand == 0x400000 && exponent == 0xFF) {
                return Double.NaN;
            }
            if (exponent > 0) {
                significand += 0x800000;
            }
            return Math.scalb(significand / (double) 0x800000,
                    exponent - 0x7F);
        }
        if (exponent == 0 && significand == 0x7FFFFF) {
            return Double.NEGATIVE_INFINITY;
        }
        significand = 0x7FFFFF - significand;
        if (exponent < 0xFF) {
            significand += 0x800000;
        }
        return -Math.scalb(significand / (double) 0x800000,
                0x80 - exponent);
    }

    private static double decodeDouble(byte[] data) {
        if (data.length != 8) {
            return Double.NaN;
        }
        long bits = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).getLong();
        if ((bits & Long.MIN_VALUE) != 0) {
            bits ^= Long.MIN_VALUE;
        } else {
            bits = ~bits;
        }
        return Double.longBitsToDouble(bits);
    }

    private static int readIntBigEndian(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4)
                .order(ByteOrder.BIG_ENDIAN).getInt();
    }
}
