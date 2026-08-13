/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedoTimeTest {
    @Test
    void decodesPackedRedoTime() {
        long value = (((((2018 - 1988) * 12L + (10 - 1)) * 31 + (15 - 1)) * 24 + 22) * 60 + 25) * 60 + 36;
        RedoTime time = RedoTime.of(value);

        assertEquals("10/15/2018 22:25:36", time.toString());
        assertEquals(1_539_613_536L, time.toEpochSeconds(8 * 60 * 60));
        assertEquals(value, time.value());
    }

    @Test
    void validatesUnsigned32BitRange() {
        assertThrows(IllegalArgumentException.class, () -> RedoTime.of(-1));
        assertThrows(IllegalArgumentException.class, () -> RedoTime.of(0x1_0000_0000L));
    }
}
