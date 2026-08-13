/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataParityTest {
    private static final String FIXTURE =
            "/fixtures/value-types/openlogreplicator-6bc92bc1.properties";

    private Properties baseline;

    private static final String[] TIMEZONE_ALIASES = {
            "Etc/GMT-14", "Etc/GMT-13", "Etc/GMT-12", "Etc/GMT-11",
            "HST", "Etc/GMT-10", "Etc/GMT-9", "PST", "PST8PDT",
            "Etc/GMT-8", "MST", "MST7MDT", "Etc/GMT-7", "CST",
            "CST6CDT", "Etc/GMT-6", "EST", "EST5EDT", "Etc/GMT-5",
            "Etc/GMT-4", "Etc/GMT-3", "Etc/GMT-2", "Etc/GMT-1",
            "GMT", "Etc/GMT", "Greenwich", "Etc/Greenwich", "GMT0",
            "Etc/GMT0", "GMT+0", "Etc/GMT-0", "Etc/GMT+0", "UTC",
            "Etc/UTC", "UCT", "Etc/UCT", "Universal", "Etc/Universal",
            "WET", "MET", "CET", "Etc/GMT+1", "EET", "Etc/GMT+2",
            "Etc/GMT+3", "Etc/GMT+4", "Etc/GMT+5", "Etc/GMT+6",
            "Etc/GMT+7", "PRC", "ROC", "Etc/GMT+8", "Etc/GMT+9",
            "Etc/GMT+10", "Etc/GMT+11", "Etc/GMT+12"
    };

    @BeforeEach
    void loadBaseline() throws Exception {
        baseline = new Properties();
        try (InputStream input = getClass().getResourceAsStream(FIXTURE)) {
            assertNotNull(input);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedTimezoneParsingAndFormatting() {
        assertEquals(baseline.getProperty("data.timezone.prc"),
                parsed("PRC"));
        assertEquals(baseline.getProperty("data.timezone.pst"),
                parsed("PST8PDT"));
        assertEquals(baseline.getProperty("data.timezone.negative"),
                parsed("-05:30"));
        assertFalse(Data.parseTimezone("Asia/Shanghai").isPresent());
        StringBuilder aliases = new StringBuilder();
        for (String alias : TIMEZONE_ALIASES) {
            aliases.append(Data.parseTimezone(alias).orElseThrow())
                    .append(',');
        }
        assertEquals(baseline.getProperty("data.timezone.aliases"),
                aliases.toString());
        assertEquals(baseline.getProperty("data.timezone.formatPositive"),
                Data.timezoneToString(19_800));
        assertEquals(baseline.getProperty("data.timezone.formatNegative"),
                Data.timezoneToString(-45_000));
    }

    @Test
    void matchesPinnedAdBcAndBoundaryDates() {
        long ad = Data.valuesToEpoch(2024, 1, 29, 12, 34, 56, 28_800);
        long leap = Data.valuesToEpoch(2000, 1, 29, 0, 0, 0, 0);
        long bc = Data.valuesToEpoch(0, 0, 1, 0, 0, 0, 0);
        assertEquals(baseline.getProperty("data.epoch.ad"),
                Long.toString(ad));
        assertEquals(baseline.getProperty("data.epoch.leap"),
                Long.toString(leap));
        assertEquals(baseline.getProperty("data.epoch.bc"),
                Long.toString(bc));
        assertEquals(baseline.getProperty("data.iso.ad"),
                Data.epochToIso8601(ad, true, true));
        assertEquals(baseline.getProperty("data.iso.leap"),
                Data.epochToIso8601(leap, false, false));
        assertEquals(baseline.getProperty("data.iso.bc"),
                Data.epochToIso8601(bc, true, true));
        assertEquals(baseline.getProperty("data.iso.minimum"),
                Data.epochToIso8601(-210_831_897_600L, true, true));
        assertEquals(baseline.getProperty("data.iso.maximum"),
                Data.epochToIso8601(253_402_300_799L, true, true));
        RedoRuntimeException error = assertThrows(
                RedoRuntimeException.class,
                () -> Data.epochToIso8601(
                        253_402_300_800L, true, true));
        assertEquals(Integer.parseInt(
                        baseline.getProperty("data.iso.invalidCode")),
                error.getErrorCode());
    }

    @Test
    void matchesPinnedByteEscapingAndIdentifierLimit() {
        byte[] value = {
                'A', '\t', '\n', '\b', '\f', '\r', '"', '\\', 1, 'Z', 0};
        assertEquals(baseline.getProperty("data.escape.hex"),
                HexFormat.of().formatHex(Data.escapeValue(value)));
        assertEquals(baseline.getProperty("data.map16"),
                "" + Data.map16(10) + Data.map16Upper(15));
        assertEquals(baseline.getProperty("data.map64"),
                "" + Data.map64(62) + Data.map64(63));
        assertEquals(62, Data.map64Reverse('+'));
        assertEquals(0, Data.map64Reverse(','));
        for (int index = 0; index < 64; index++) {
            assertEquals(index, Data.map64Reverse(Data.map64(index)));
        }

        Data.checkName("A".repeat(1_023));
        DataException error = assertThrows(
                DataException.class,
                () -> Data.checkName("A".repeat(1_024)
                        .getBytes(StandardCharsets.UTF_8)));
        assertEquals(Integer.parseInt(
                        baseline.getProperty("data.name.invalidCode")),
                error.getErrorCode());
    }

    private String parsed(String value) {
        return "1:" + Data.parseTimezone(value).orElseThrow();
    }
}
