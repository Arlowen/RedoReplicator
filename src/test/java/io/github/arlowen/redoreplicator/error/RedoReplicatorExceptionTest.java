/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedoReplicatorExceptionTest {
    @Test
    void retainsUpstreamCodesAndMessages() {
        BootException boot = new BootException(10001, "boot failed");
        ConfigurationException configuration = new ConfigurationException(10002, "configuration failed");
        RedoLogException redo = new RedoLogException(10003, "redo failed");
        RedoRuntimeException runtime = new RedoRuntimeException(10004, "runtime failed", 55);

        assertEquals(10001, boot.getErrorCode());
        assertEquals("boot failed", boot.getMessage());
        assertEquals(10002, configuration.getErrorCode());
        assertEquals(10003, redo.getErrorCode());
        assertEquals(10004, runtime.getErrorCode());
        assertEquals(55, runtime.getSupplementalCode());
    }
}
