/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedoConstantsTest {
    @Test
    void decodesAndFormatsUndoBlockAddress() {
        long undoBlockAddress = 0x00AB_CDEF_1234_5678L;

        assertEquals(0x1234_5678L, RedoConstants.undoBlockAddressBlock(undoBlockAddress));
        assertEquals(0xCDEF, RedoConstants.undoBlockAddressSequence(undoBlockAddress));
        assertEquals(0xAB, RedoConstants.undoBlockAddressRecord(undoBlockAddress));
        assertEquals("0x12345678.cdef.ab", RedoConstants.formatUndoBlockAddress(undoBlockAddress));
    }
}
