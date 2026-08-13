/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScnTest {
    @Test
    void reproducesOpenLogReplicatorFormats() {
        Scn scn = Scn.of(0x1234_5678_9ABC_DEF0L);

        assertEquals("0x5678.9abcdef0", scn.to48());
        assertEquals("0x123456789abcdef0", scn.to64());
        assertEquals("0x1234.5678.9abcdef0", scn.to64D());
        assertEquals("0x123456789abcdef0", scn.toHex12());
        assertEquals("0x123456789abcdef0", scn.toHex16());
        assertEquals("1311768467463790320", scn.toDecimalString());
    }

    @Test
    void preservesUnsignedSentinelAndOrdering() {
        assertSame(Scn.none(), Scn.of(-1L));
        assertEquals("18446744073709551615", Scn.none().toDecimalString());
        assertEquals("0xffff7fffffffffff", Scn.none().toHex16());
        assertTrue(Scn.none().compareTo(Scn.of(Long.MAX_VALUE)) > 0);
        assertTrue(Scn.of(Long.MIN_VALUE).compareTo(Scn.of(Long.MAX_VALUE)) > 0);
    }

    @Test
    void parsesOracleUnsignedDecimalScns() {
        Scn scn = Scn.parseDecimal("9295429630892703743");

        assertEquals("9295429630892703743", scn.toDecimalString());
        assertThrows(IllegalArgumentException.class,
                () -> Scn.parseDecimal("18446744073709551616"));
        assertThrows(IllegalArgumentException.class,
                () -> Scn.parseDecimal("-1"));
    }

    @Test
    void buildsScnFromWordsAndLittleEndianBytes() {
        Scn fromWords = Scn.fromWords(0x1234_5678L, 0x9ABC_DEF0L);
        Scn fromBytes = Scn.fromLittleEndian(0xF0, 0xDE, 0xBC, 0x9A, 0x78, 0x56, 0x34, 0x12);
        Scn sixBytes = Scn.fromLittleEndian(0xF0, 0xDE, 0xBC, 0x9A, 0x78, 0x56);

        assertEquals(Scn.of(0x1234_5678_9ABC_DEF0L), fromWords);
        assertEquals(fromWords, fromBytes);
        assertEquals(Scn.of(0x0000_5678_9ABC_DEF0L), sixBytes);
    }
}
