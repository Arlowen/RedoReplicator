/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import io.github.arlowen.redoreplicator.error.DataException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XidTest {
    @Test
    void reproducesClassicOpenLogReplicatorXid() {
        Xid xid = Xid.of(2, 0x12, 0x4162);

        assertEquals("0x0002.012.00004162", xid.toString());
        assertEquals(2, xid.unsignedUndoSegment());
        assertEquals(0x12, xid.slot());
        assertEquals(0x4162, xid.sequence());
        assertFalse(xid.isEmpty());
    }

    @Test
    void acceptsEveryUpstreamTextShape() {
        Xid expected = Xid.of(2, 0x12, 0x4162);

        assertEquals(expected, Xid.parse("0002001200004162"));
        assertEquals(expected, Xid.parse("0002.012.00004162"));
        assertEquals(expected, Xid.parse("0002.0012.00004162"));
        assertEquals(expected, Xid.parse("0x0002.012.00004162"));
        assertEquals(expected, Xid.parse("0x0002.0012.00004162"));
    }

    @Test
    void preservesUnsignedFieldsAndRejectsBadText() {
        Xid maximum = Xid.parse("0xffff.ffff.ffffffff");

        assertEquals(-1, maximum.undoSegment());
        assertEquals(0xFFFF, maximum.unsignedUndoSegment());
        assertEquals(0xFFFF, maximum.slot());
        assertEquals(0xFFFF_FFFFL, maximum.sequence());
        assertTrue(maximum.compareTo(Xid.of(Long.MAX_VALUE)) > 0);

        DataException error = assertThrows(DataException.class, () -> Xid.parse("0X0002.012.00004162"));
        assertEquals(20002, error.getErrorCode());
    }
}
