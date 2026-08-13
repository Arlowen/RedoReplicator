/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.arlowen.redoreplicator.charset.Locales;
import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.transaction.RedoColumnValue;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleJsonValueDecoderTest {
    private final OracleJsonValueDecoder decoder =
            new OracleJsonValueDecoder(
                    StandardCharsets.UTF_8, ZoneId.of("Asia/Shanghai"));

    @Test
    void decodesNullTextNumberRawAndBoolean() {
        assertTrue(decoder.decode(
                RedoColumnValue.nullValue(OracleColumnType.VARCHAR))
                .isNull());
        assertEquals("中文", decoder.decode(value(
                OracleColumnType.VARCHAR,
                "中文".getBytes(StandardCharsets.UTF_8))).textValue());
        assertEquals("12", decoder.decode(value(
                OracleColumnType.NUMBER,
                new byte[]{(byte) 0xC1, 13})).decimalValue()
                .toPlainString());
        assertEquals("00ABFF", decoder.decode(value(
                OracleColumnType.RAW,
                new byte[]{0, (byte) 0xAB, (byte) 0xFF})).textValue());
        assertEquals(1, decoder.decode(value(
                OracleColumnType.BOOLEAN, new byte[]{1})).intValue());
        assertEquals("?", decoder.decode(value(
                OracleColumnType.BOOLEAN, new byte[]{2})).textValue());
    }

    @Test
    void decodesTextWithTheColumnCharacterSet() {
        OracleJsonValueDecoder localeDecoder = new OracleJsonValueDecoder(
                new Locales(), 873, ZoneId.of("Asia/Shanghai"));

        assertEquals("中文", localeDecoder.decode(RedoColumnValue.of(
                OracleColumnType.VARCHAR, 2000,
                new byte[]{0x4E, 0x2D, 0x65, (byte) 0x87})).textValue());
        assertEquals("😀", localeDecoder.decode(RedoColumnValue.of(
                OracleColumnType.CHAR, 871,
                new byte[]{(byte) 0xED, (byte) 0xA0, (byte) 0xBD,
                        (byte) 0xED, (byte) 0xB8, (byte) 0x80}))
                .textValue());
    }

    @Test
    void decodesInlineBlobAndClobLikeTheFixedBaseline() throws Exception {
        Properties expected = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/lob-locator/"
                        + "openlogreplicator-6bc92bc1.properties")) {
            assertNotNull(input);
            expected.load(input);
        }

        OracleLobLocatorDecoder locatorDecoder =
                new OracleLobLocatorDecoder();
        Properties actual = new Properties();
        actual.setProperty("fixed.ok", "true");
        actual.setProperty("fixed.hex", HexFormat.of().formatHex(
                locatorDecoder.decodeInline(fixedLobLocator(
                        new byte[]{1, 2, 3}))));
        byte[] textLocator = variableLobLocator(
                "ABCD".getBytes(StandardCharsets.UTF_8));
        actual.setProperty("variable.ok", "true");
        actual.setProperty("variable.hex", HexFormat.of().formatHex(
                locatorDecoder.decodeInline(textLocator)));
        actual.setProperty("empty.ok", "true");
        actual.setProperty("empty.hex", HexFormat.of().formatHex(
                locatorDecoder.decodeInline(variableLobLocator(
                        new byte[0]))));
        assertEquals(expected.getProperty("fixed.ok"),
                actual.getProperty("fixed.ok"));
        assertEquals(expected.getProperty("fixed.hex"),
                actual.getProperty("fixed.hex"));
        assertEquals(expected.getProperty("variable.ok"),
                actual.getProperty("variable.ok"));
        assertEquals(expected.getProperty("variable.hex"),
                actual.getProperty("variable.hex"));
        assertEquals(expected.getProperty("empty.ok"),
                actual.getProperty("empty.ok"));
        assertEquals(expected.getProperty("empty.hex"),
                actual.getProperty("empty.hex"));

        assertEquals("010203", decoder.decode(value(
                OracleColumnType.BLOB,
                fixedLobLocator(new byte[]{1, 2, 3}))).textValue());
        assertEquals("ABCD", decoder.decode(value(
                OracleColumnType.CLOB, textLocator)).textValue());
        assertEquals("", decoder.decode(value(
                OracleColumnType.CLOB,
                variableLobLocator(new byte[0]))).textValue());
    }

    @Test
    void stopsForExternalOrInvalidLobLocator() {
        byte[] external = new byte[20];
        RedoLogException externalError = assertThrows(
                RedoLogException.class,
                () -> decoder.decode(value(
                        OracleColumnType.BLOB, external)));
        assertEquals(50075, externalError.getErrorCode());

        byte[] invalid = fixedLobLocator(new byte[]{1, 2, 3});
        invalid[21]++;
        RedoLogException invalidError = assertThrows(
                RedoLogException.class,
                () -> decoder.decode(value(
                        OracleColumnType.CLOB, invalid)));
        assertEquals(50075, invalidError.getErrorCode());
    }

    @Test
    void decodesDateTimestampAndLocalTimeZoneToEpochNanos() {
        byte[] date = oracleDateTime(2024, 4, 5, 19, 34, 38, 0);
        long utcSeconds = LocalDateTime.of(
                2024, 4, 5, 19, 34, 38)
                .toEpochSecond(ZoneOffset.UTC);
        assertEquals(Long.toString(utcSeconds * 1_000_000_000L),
                decoder.decode(value(OracleColumnType.DATE, date))
                        .decimalValue().toPlainString());

        byte[] timestamp = oracleDateTime(
                2024, 4, 5, 19, 34, 38, 123_456_789);
        assertEquals(Long.toString(
                        utcSeconds * 1_000_000_000L + 123_456_789),
                decoder.decode(value(
                        OracleColumnType.TIMESTAMP, timestamp))
                        .decimalValue().toPlainString());

        long localSeconds = LocalDateTime.of(
                2024, 4, 5, 19, 34, 38)
                .atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond();
        assertEquals(Long.toString(
                        localSeconds * 1_000_000_000L + 123_456_789),
                decoder.decode(value(
                        OracleColumnType.TIMESTAMP_WITH_LOCAL_TIME_ZONE,
                        timestamp)).decimalValue().toPlainString());
    }

    @Test
    void decodesTimestampWithTimeZoneLabel() {
        byte[] timestamp = new byte[13];
        byte[] base = oracleDateTime(
                2024, 4, 5, 19, 34, 38, 123_456_789);
        System.arraycopy(base, 0, timestamp, 0, 11);
        timestamp[11] = 28;
        timestamp[12] = 60;

        JsonNode value = decoder.decode(value(
                OracleColumnType.TIMESTAMP_WITH_TIME_ZONE, timestamp));

        long seconds = LocalDateTime.of(
                2024, 4, 5, 19, 34, 38)
                .toEpochSecond(ZoneOffset.UTC);
        assertEquals(seconds + "123456789,+08:00", value.textValue());

        byte[] utcTimestamp = new byte[9];
        byte[] utcBase = oracleDateTime(
                2024, 4, 5, 19, 34, 38, 0);
        System.arraycopy(utcBase, 0, utcTimestamp, 0, 7);
        utcTimestamp[7] = 20;
        utcTimestamp[8] = 60;
        assertEquals(seconds + "000000000,+00:00", decoder.decode(value(
                OracleColumnType.TIMESTAMP_WITH_TIME_ZONE,
                utcTimestamp)).textValue());
    }

    @Test
    void decodesBinaryFloatAndDouble() {
        assertEquals(1.5, decoder.decode(value(
                OracleColumnType.BINARY_FLOAT,
                new byte[]{(byte) 0xBF, (byte) 0xC0, 0, 0}))
                .doubleValue());
        assertEquals(-1.5, decoder.decode(value(
                OracleColumnType.BINARY_FLOAT,
                new byte[]{0x40, 0x3F, (byte) 0xFF, (byte) 0xFF}))
                .doubleValue());
        long oracleDouble = Double.doubleToRawLongBits(1.5)
                ^ Long.MIN_VALUE;
        byte[] bytes = ByteBuffer.allocate(8)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(oracleDouble).array();
        assertEquals(1.5, decoder.decode(value(
                OracleColumnType.BINARY_DOUBLE, bytes)).doubleValue());
    }

    @Test
    void decodesIntervalsAndUniversalRowId() {
        byte[] yearMonth = ByteBuffer.allocate(5)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(0x8000_0002).put((byte) 63).array();
        assertEquals("27", decoder.decode(value(
                OracleColumnType.INTERVAL_YEAR_TO_MONTH,
                yearMonth)).decimalValue().toPlainString());
        byte[] negativeYearMonth = ByteBuffer.allocate(5)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(0x7FFF_FFFE).put((byte) 57).array();
        assertEquals("-27", decoder.decode(value(
                OracleColumnType.INTERVAL_YEAR_TO_MONTH,
                negativeYearMonth)).decimalValue().toPlainString());

        byte[] daySecond = ByteBuffer.allocate(11)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(0x8000_0001)
                .put((byte) 62).put((byte) 63).put((byte) 64)
                .putInt(0x8000_0005).array();
        assertEquals("93784000000005", decoder.decode(value(
                OracleColumnType.INTERVAL_DAY_TO_SECOND,
                daySecond)).decimalValue().toPlainString());
        byte[] negativeDaySecond = ByteBuffer.allocate(11)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(0x7FFF_FFFF)
                .put((byte) 58).put((byte) 57).put((byte) 56)
                .putInt(0x7FFF_FFFB).array();
        assertEquals("-93784000000005", decoder.decode(value(
                OracleColumnType.INTERVAL_DAY_TO_SECOND,
                negativeDaySecond)).decimalValue().toPlainString());

        byte[] rowId = ByteBuffer.allocate(13)
                .order(ByteOrder.BIG_ENDIAN)
                .put((byte) 1)
                .putInt(100)
                .putShort((short) 5)
                .putShort((short) 3)
                .putInt(200).array();
        assertEquals("00c000c8.0064.0005", decoder.decode(value(
                OracleColumnType.UROWID, rowId)).textValue());
    }

    @Test
    void returnsQuestionMarkForMalformedAndStopsForUnresolvedLob() {
        assertEquals("?", decoder.decode(value(
                OracleColumnType.DATE, new byte[]{1, 2})).textValue());
        assertEquals("?", decoder.decode(value(
                OracleColumnType.LONG, new byte[]{1})).textValue());
        RedoLogException error = assertThrows(
                RedoLogException.class,
                () -> decoder.decode(value(
                        OracleColumnType.CLOB, new byte[]{1})));
        assertEquals(50075, error.getErrorCode());
    }

    private static RedoColumnValue value(
            OracleColumnType type, byte[] bytes) {
        return RedoColumnValue.of(type, bytes);
    }

    private static byte[] fixedLobLocator(byte[] value) {
        byte[] locator = new byte[36 + value.length];
        locator[5] = 0x04;
        putUnsignedShort(locator, 20, value.length + 16);
        putUnsignedShort(locator, 22, 0x0100);
        putUnsignedShort(locator, 28, value.length);
        System.arraycopy(value, 0, locator, 36, value.length);
        return locator;
    }

    private static byte[] variableLobLocator(byte[] value) {
        byte[] locator = new byte[30 + value.length];
        locator[5] = 0x04;
        putUnsignedShort(locator, 20, value.length + 10);
        putUnsignedShort(locator, 22, 0x0800);
        locator[28] = (byte) value.length;
        System.arraycopy(value, 0, locator, 30, value.length);
        return locator;
    }

    private static void putUnsignedShort(
            byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static byte[] oracleDateTime(
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second,
            int nanos) {
        int size = 7;
        if (nanos != 0) {
            size = 11;
        }
        byte[] data = new byte[size];
        data[0] = (byte) (year / 100 + 100);
        data[1] = (byte) (year % 100 + 100);
        data[2] = (byte) month;
        data[3] = (byte) day;
        data[4] = (byte) (hour + 1);
        data[5] = (byte) (minute + 1);
        data[6] = (byte) (second + 1);
        if (size == 11) {
            ByteBuffer.wrap(data, 7, 4)
                    .order(ByteOrder.BIG_ENDIAN).putInt(nanos);
        }
        return data;
    }
}
