/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntXTest {
    @Test
    void parsesAndWrapsUnsigned128BitValues() {
        IntX maximum = IntX.parseDecimal("340282366920938463463374607431768211455");

        assertEquals("[18446744073709551615,18446744073709551615]", maximum.toString());
        assertEquals(new BigInteger("340282366920938463463374607431768211455"),
                maximum.toUnsignedBigInteger());
        assertEquals(IntX.zero(), maximum.plus(IntX.of(1)));
        assertTrue(maximum.isSet64(1));
        assertFalse(IntX.zero().isSet64(1));
    }

    @Test
    void matchesUpstreamDecimalInputErrors() {
        IllegalArgumentException badDigit = assertThrows(IllegalArgumentException.class,
                () -> IntX.parseDecimal("12x4"));
        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class,
                () -> IntX.parseDecimal("1234567890123456789012345678901234567890"));

        assertEquals("incorrect conversion of string: x4", badDigit.getMessage());
        assertEquals("incorrect conversion of string: 1234567890123456789012345678901234567890",
                tooLong.getMessage());
    }
}
