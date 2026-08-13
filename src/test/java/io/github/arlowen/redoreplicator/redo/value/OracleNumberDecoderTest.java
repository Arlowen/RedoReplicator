/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.value;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OracleNumberDecoderTest {
    private final OracleNumberDecoder decoder = new OracleNumberDecoder();

    @Test
    void decodesZeroAndPositiveIntegers() {
        assertNumber("0", 0x80);
        assertNumber("1", 0xC1, 0x02);
        assertNumber("99", 0xC1, 0x64);
        assertNumber("100", 0xC2, 0x02);
        assertNumber("12345", 0xC3, 0x02, 0x18, 0x2E);
    }

    @Test
    void decodesPositiveFractions() {
        assertNumber("0.01", 0xC0, 0x02);
        assertNumber("1.23", 0xC1, 0x02, 0x18);
        assertNumber("123.45", 0xC2, 0x02, 0x18, 0x2E);
    }

    @Test
    void decodesNegativeValuesWithAndWithoutTerminator() {
        assertNumber("-1", 0x3E, 0x64, 0x66);
        assertNumber("-99", 0x3E, 0x02, 0x66);
        assertNumber("-123.45", 0x3D, 0x64, 0x4E, 0x38, 0x66);
        assertNumber("-0.01", 0x3F, 0x64);
    }

    @Test
    void rejectsMalformedValues() {
        assertThrows(RedoLogException.class, () -> decoder.decode(new byte[0]));
        assertThrows(RedoLogException.class,
                () -> decoder.decode(bytes(0x80, 0x01)));
        assertThrows(RedoLogException.class,
                () -> decoder.decode(bytes(0xC1, 0x00)));
        assertThrows(RedoLogException.class,
                () -> decoder.decode(bytes(0x3E, 0x00, 0x66)));
    }

    private void assertNumber(String expected, int... encoded) {
        BigDecimal actual = decoder.decode(bytes(encoded));
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
