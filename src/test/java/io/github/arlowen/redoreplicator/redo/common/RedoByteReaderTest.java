/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RedoByteReaderTest {
    private final RedoByteReader littleEndian = new RedoByteReader(ByteOrder.LITTLE_ENDIAN);
    private final RedoByteReader bigEndian = new RedoByteReader(ByteOrder.BIG_ENDIAN);

    @Test
    void readsUnsignedIntegersInBothByteOrders() {
        byte[] bytes = {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF};

        assertEquals(0x2301, littleEndian.readUnsignedShort(bytes, 0));
        assertEquals(0x0123, bigEndian.readUnsignedShort(bytes, 0));
        assertEquals(0x6745_2301L, littleEndian.readUnsignedInt(bytes, 0));
        assertEquals(0x0123_4567L, bigEndian.readUnsignedInt(bytes, 0));
        assertEquals(0xEFCD_AB89_6745_2301L, littleEndian.readLong(bytes, 0));
        assertEquals(0x0123_4567_89AB_CDEFL, bigEndian.readLong(bytes, 0));
    }

    @Test
    void reproducesUpstream56BitLayouts() {
        byte[] bytes = {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD};

        assertEquals(0x00CD_AB89_6745_2301L, littleEndian.read56(bytes, 0));
        assertEquals(0x00CD_89AB_0123_4567L, bigEndian.read56(bytes, 0));
    }

    @Test
    void readsNormalExtendedAndReverseScnLayouts() {
        byte[] normalLittle = {(byte) 0xF0, (byte) 0xDE, (byte) 0xBC, (byte) 0x9A, 0x78, 0x56, 0, 0};
        byte[] normalBig = {(byte) 0x9A, (byte) 0xBC, (byte) 0xDE, (byte) 0xF0, 0x56, 0x78, 0, 0};
        byte[] extendedLittle = {
                (byte) 0xF0, (byte) 0xDE, (byte) 0xBC, (byte) 0x9A,
                0x34, (byte) 0x92, 0x78, 0x56
        };
        byte[] reverseLittle = {0x78, 0x56, (byte) 0xF0, (byte) 0xDE, (byte) 0xBC, (byte) 0x9A};

        assertEquals(Scn.of(0x0000_5678_9ABC_DEF0L), littleEndian.readScn(normalLittle, 0));
        assertEquals(Scn.of(0x0000_5678_9ABC_DEF0L), bigEndian.readScn(normalBig, 0));
        assertEquals(Scn.of(0x1234_5678_9ABC_DEF0L), littleEndian.readScn(extendedLittle, 0));
        assertEquals(Scn.of(0x0000_5678_9ABC_DEF0L), littleEndian.readReverseScn(reverseLittle, 0));
    }

    @Test
    void mapsSixFfBytesToNone() {
        byte[] bytes = {
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, 0, 0
        };

        assertSame(Scn.none(), littleEndian.readScn(bytes, 0));
        assertSame(Scn.none(), bigEndian.readReverseScn(bytes, 0));
    }
}
