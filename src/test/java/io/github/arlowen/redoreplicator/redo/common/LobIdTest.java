/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobIdTest {
    @Test
    void reproducesUpstreamHexForms() {
        byte[] bytes = {0x01, 0x0A, 0x00, (byte) 0xFF, 0x10, 0x20, 0x03, 0x40, 0x05, 0x60};
        LobId lobId = LobId.of(bytes);

        assertEquals("010a00ff102003400560", lobId.lower());
        assertEquals("010A00FF102003400560", lobId.upper());
        assertEquals("1A0FF1020340560", lobId.narrow());
        assertEquals(lobId.upper(), lobId.toString());

        bytes[0] = 0x02;
        assertEquals("010A00FF102003400560", lobId.upper());
    }

    @Test
    void comparesBytesAsUnsignedAndValidatesLength() {
        LobId lower = LobId.of(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0x7F});
        LobId higher = LobId.of(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0x80});

        assertTrue(lower.compareTo(higher) < 0);
        assertNotEquals(lower, higher);
        assertThrows(IllegalArgumentException.class, () -> LobId.of(new byte[9]));
    }
}
