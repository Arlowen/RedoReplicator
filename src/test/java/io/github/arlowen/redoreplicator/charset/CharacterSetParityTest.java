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
}
