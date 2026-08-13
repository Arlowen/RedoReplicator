/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HexFormat;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CharacterSetParityTest {
    private final Locales locales = new Locales();

    @Test
    void matchesTheFixedOpenLogReplicatorProbe() throws Exception {
        Properties expected = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/charset/"
                        + "openlogreplicator-6bc92bc1.properties")) {
            assertNotNull(input);
            expected.load(input);
        }

        Properties actual = new Properties();
        add(actual, "al32.ascii", 873, "41");
        add(actual, "al32.bmp", 873, "e4b8ad");
        add(actual, "al32.supplementary", 873, "f09f9880");
        add(actual, "al32.sequence", 873, "41e4b8ad");
        add(actual, "utf8.ascii", 871, "41");
        add(actual, "utf8.bmp", 871, "e4b8ad");
        add(actual, "utf8.cesu8", 871, "eda0bdedb880");
        add(actual, "utf8.sequence", 871, "41e4b8ad");
        add(actual, "al16.bmp", 2000, "4e2d");
        add(actual, "al16.supplementary", 2000, "d83dde00");
        add(actual, "al16.sequence", 2000, "00414e2d");
        add(actual, "zhs16gbk.ascii", 852, "41");
        add(actual, "zhs16gbk.euro", 852, "80");
        add(actual, "zhs16gbk.chinese", 852, "d6d0cec4");
        actual.setProperty("zhs16gbk.map_fnv1a64",
                zhs16gbkMapDigest());
        add(actual, "gb18030.ascii", 854, "41");
        add(actual, "gb18030.two_byte", 854, "d6d0");
        add(actual, "gb18030.four_group1", 854, "81308130");
        add(actual, "gb18030.four_group2", 854, "90308130");
        add(actual, "gb18030.invalid_then_ascii", 854,
                "8130814041");
        add(actual, "gb18030.truncated", 854, "813081");
        actual.setProperty("gb18030.single_fnv1a64",
                singleByteMapDigest(854));
        actual.setProperty("gb18030.pair_fnv1a64",
                bytePairMapDigest(854));
        actual.setProperty("gb18030.four_group1_fnv1a64",
                gb18030FourByteMapDigest(0x81, 0x84));
        actual.setProperty("gb18030.four_group2_fnv1a64",
                gb18030FourByteMapDigest(0x90, 0xE3));
        add(actual, "we8mswin1252.ascii", 178, "41");
        add(actual, "we8mswin1252.euro", 178, "80");
        add(actual, "we8mswin1252.controls", 178, "818d9d");
        actual.setProperty("we8mswin1252.map_fnv1a64",
                singleByteMapDigest(178));
        long[] sevenBitIds = {
                1, 11, 13, 14, 15, 16, 17, 21,
                202, 203, 204, 206, 205, 207
        };
        for (long id : sevenBitIds) {
            actual.setProperty("seven." + id + ".map_fnv1a64",
                    singleByteMapDigest(id));
        }
        String prefix = "eight.";
        String digestSuffix = ".map_fnv1a64";
        String nameSuffix = ".name";
        for (String key : expected.stringPropertyNames()) {
            if (key.startsWith(prefix) && key.endsWith(digestSuffix)) {
                int end = key.length() - digestSuffix.length();
                long id = Long.parseLong(
                        key.substring(prefix.length(), end));
                actual.setProperty(key, singleByteMapDigest(id));
            }
            if (key.startsWith(prefix) && key.endsWith(nameSuffix)) {
                int end = key.length() - nameSuffix.length();
                long id = Long.parseLong(
                        key.substring(prefix.length(), end));
                actual.setProperty(key, locales.require(id).name());
            }
        }
        String sixteenPrefix = "sixteen.";
        for (String key : expected.stringPropertyNames()) {
            if (!key.startsWith(sixteenPrefix)) {
                continue;
            }
            int propertySeparator = key.indexOf('.', sixteenPrefix.length());
            long id = Long.parseLong(key.substring(
                    sixteenPrefix.length(), propertySeparator));
            if (key.endsWith(".name")) {
                actual.setProperty(key, locales.require(id).name());
            }
            if (key.endsWith(".single_fnv1a64")) {
                actual.setProperty(key, singleByteMapDigest(id));
            }
            if (key.endsWith(".pair_fnv1a64")) {
                actual.setProperty(key, bytePairMapDigest(id));
            }
        }
        String eastAsianPrefix = "east.";
        for (String key : expected.stringPropertyNames()) {
            if (!key.startsWith(eastAsianPrefix)) {
                continue;
            }
            int propertySeparator = key.indexOf(
                    '.', eastAsianPrefix.length());
            long id = Long.parseLong(key.substring(
                    eastAsianPrefix.length(), propertySeparator));
            if (key.endsWith(".name")) {
                actual.setProperty(key, locales.require(id).name());
            }
            if (key.endsWith(".single_fnv1a64")) {
                actual.setProperty(key, singleByteMapDigest(id));
            }
            if (key.endsWith(".pair_fnv1a64")) {
                actual.setProperty(key, bytePairMapDigest(id));
            }
            if (key.endsWith(".triple_fnv1a64")) {
                actual.setProperty(key, japaneseEucTripleMapDigest(id));
            }
        }

        assertEquals(expected, actual);
    }

    @Test
    void preservesUpstreamMalformedSequenceConsumption() {
        assertEquals("\uFFFD", locales.require(873).decode(
                HexFormat.of().parseHex("c241")));
        assertEquals("\uFFFD", locales.require(873).decode(
                HexFormat.of().parseHex("f09f98")));
        assertEquals("\u0000", locales.require(873).decode(
                HexFormat.of().parseHex("c080")));

        assertEquals("\u07D8\uFFFD", locales.require(871).decode(
                HexFormat.of().parseHex("f09f9880")));
        assertEquals("\uFFFD", locales.require(871).decode(
                HexFormat.of().parseHex("eda0bd")));

        assertEquals("\uFFFD", locales.require(2000).decode(
                HexFormat.of().parseHex("dc00")));
        assertEquals("\uFFFD", locales.require(2000).decode(
                HexFormat.of().parseHex("d83d")));
        assertEquals("\uFFFD", locales.require(2000).decode(
                HexFormat.of().parseHex("41")));

        assertEquals("\uFFFD", locales.require(852).decode(
                HexFormat.of().parseHex("81")));
        assertEquals("\uFFFD", locales.require(852).decode(
                HexFormat.of().parseHex("8130")));
        assertEquals("\uE76C", locales.require(852).decode(
                HexFormat.of().parseHex("a2e3")));
        assertEquals("\u2295", locales.require(852).decode(
                HexFormat.of().parseHex("a892")));
        assertEquals("\u0081\u008D\u008F\u0090\u009D",
                locales.require(178).decode(
                        HexFormat.of().parseHex("818d8f909d")));
    }

    @Test
    void rejectsUnknownOrMismatchedCharacterSetIdentity() {
        assertEquals("AL16UTF16", locales.require(
                2000, "al16utf16").name());
        IllegalArgumentException unknown = org.junit.jupiter.api.Assertions
                .assertThrows(IllegalArgumentException.class,
                        () -> locales.require(9999));
        assertEquals("Oracle character set id is not translated: 9999",
                unknown.getMessage());
        IllegalArgumentException mismatch = org.junit.jupiter.api.Assertions
                .assertThrows(IllegalArgumentException.class,
                        () -> locales.require(873, "UTF8"));
        assertEquals("Oracle character set identity mismatch: 873 is UTF8, "
                        + "expected AL32UTF8", mismatch.getMessage());
    }

    private void add(
            Properties actual, String key, long id, String encoded) {
        String decoded = locales.require(id).decode(
                HexFormat.of().parseHex(encoded));
        StringBuilder codePoints = new StringBuilder();
        decoded.codePoints().forEach(codePoint -> {
            if (!codePoints.isEmpty()) {
                codePoints.append('.');
            }
            codePoints.append(Integer.toHexString(codePoint));
        });
        actual.setProperty(key, codePoints.toString());
    }

    private String zhs16gbkMapDigest() {
        long hash = 0xCBF29CE484222325L;
        CharacterSet characterSet = locales.require(852);
        byte[] encoded = new byte[2];
        for (int byte1 = 0x81; byte1 <= 0xFE; byte1++) {
            encoded[0] = (byte) byte1;
            for (int byte2 = 0x40; byte2 <= 0xFE; byte2++) {
                encoded[1] = (byte) byte2;
                int codePoint = characterSet.decode(encoded).codePointAt(0);
                for (int shift = 24; shift >= 0; shift -= 8) {
                    hash ^= (codePoint >> shift) & 0xFF;
                    hash *= 0x100000001B3L;
                }
            }
        }
        return String.format("%016x", hash);
    }

    private String singleByteMapDigest(long id) {
        long hash = 0xCBF29CE484222325L;
        CharacterSet characterSet = locales.require(id);
        for (int value = 0; value <= 0xFF; value++) {
            int codePoint = characterSet.decode(
                    new byte[]{(byte) value}).codePointAt(0);
            for (int shift = 24; shift >= 0; shift -= 8) {
                hash ^= (codePoint >> shift) & 0xFF;
                hash *= 0x100000001B3L;
            }
        }
        return String.format("%016x", hash);
    }

    private String bytePairMapDigest(long id) {
        long hash = 0xCBF29CE484222325L;
        CharacterSet characterSet = locales.require(id);
        byte[] encoded = new byte[2];
        for (int byte1 = 0; byte1 <= 0xFF; byte1++) {
            encoded[0] = (byte) byte1;
            for (int byte2 = 0; byte2 <= 0xFF; byte2++) {
                encoded[1] = (byte) byte2;
                int[] codePoints = characterSet.decode(encoded)
                        .codePoints().toArray();
                hash ^= codePoints.length;
                hash *= 0x100000001B3L;
                for (int codePoint : codePoints) {
                    for (int shift = 24; shift >= 0; shift -= 8) {
                        hash ^= (codePoint >> shift) & 0xFF;
                        hash *= 0x100000001B3L;
                    }
                }
            }
        }
        return String.format("%016x", hash);
    }

    private String japaneseEucTripleMapDigest(long id) {
        long hash = 0xCBF29CE484222325L;
        CharacterSet characterSet = locales.require(id);
        byte[] encoded = new byte[3];
        encoded[0] = (byte) 0x8F;
        for (int byte2 = 0xA1; byte2 <= 0xFE; byte2++) {
            encoded[1] = (byte) byte2;
            for (int byte3 = 0xA1; byte3 <= 0xFE; byte3++) {
                encoded[2] = (byte) byte3;
                int codePoint = characterSet.decode(encoded).codePointAt(0);
                for (int shift = 24; shift >= 0; shift -= 8) {
                    hash ^= (codePoint >> shift) & 0xFF;
                    hash *= 0x100000001B3L;
                }
            }
        }
        return String.format("%016x", hash);
    }

    private String gb18030FourByteMapDigest(
            int byte1Min, int byte1Max) {
        long hash = 0xCBF29CE484222325L;
        CharacterSet characterSet = locales.require(854);
        byte[] encoded = new byte[4];
        for (int byte1 = byte1Min; byte1 <= byte1Max; byte1++) {
            encoded[0] = (byte) byte1;
            for (int byte2 = 0x30; byte2 <= 0x39; byte2++) {
                encoded[1] = (byte) byte2;
                for (int byte3 = 0x81; byte3 <= 0xFE; byte3++) {
                    encoded[2] = (byte) byte3;
                    for (int byte4 = 0x30; byte4 <= 0x39; byte4++) {
                        encoded[3] = (byte) byte4;
                        int codePoint = characterSet.decode(encoded)
                                .codePointAt(0);
                        for (int shift = 24; shift >= 0; shift -= 8) {
                            hash ^= (codePoint >> shift) & 0xFF;
                            hash *= 0x100000001B3L;
                        }
                    }
                }
            }
        }
        return String.format("%016x", hash);
    }
}
