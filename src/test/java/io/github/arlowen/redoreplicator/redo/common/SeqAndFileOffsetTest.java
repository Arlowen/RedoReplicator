/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeqAndFileOffsetTest {
    @Test
    void treatsRedoSequenceAsUnsigned32BitValue() {
        assertEquals("4294967295", Seq.none().toString());
        assertEquals("0xffffffff", Seq.none().toHex(8));
        assertEquals(Seq.zero(), Seq.none().next());
        assertTrue(Seq.none().compareTo(Seq.of(2_147_483_647L)) > 0);
        assertThrows(IllegalArgumentException.class, () -> Seq.of(0x1_0000_0000L));
    }

    @Test
    void calculatesAndChecksPhysicalFileOffsets() {
        FileOffset offset = FileOffset.fromBlock(31, 512).plus(128);

        assertEquals(16_000, offset.value());
        assertEquals(31, offset.block(512));
        assertEquals(128, offset.withinBlockOffset(512));
        assertFalse(offset.isBlockAligned(512));
        assertTrue(FileOffset.fromBlock(32, 512).isBlockAligned(512));
        assertEquals(FileOffset.of(128), offset.minus(FileOffset.fromBlock(31, 512)));
        assertEquals("00003e80", offset.toHex(8));
    }

    @Test
    void preservesUnsigned64BitFileOffsetBoundaries() {
        FileOffset maximum = FileOffset.of(-1);

        assertEquals("18446744073709551615", maximum.toString());
        assertEquals("ffffffffffffffff", maximum.toHex(16));
        assertTrue(maximum.compareTo(FileOffset.of(Long.MAX_VALUE)) > 0);
        assertEquals(FileOffset.zero(), maximum.plus(1));
        assertThrows(IllegalArgumentException.class, () -> FileOffset.fromBlock(1, 1000));
    }
}
