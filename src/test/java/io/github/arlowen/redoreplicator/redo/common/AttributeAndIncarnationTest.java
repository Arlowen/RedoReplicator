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

class AttributeAndIncarnationTest {
    @Test
    void mapsEveryTransactionAttributeInUpstreamOrder() {
        assertEquals(32, Attribute.values().length);
        assertEquals("version", Attribute.VERSION.toString());
        assertEquals("seq$ update transaction", Attribute.SEQ_UPDATE_TRANSACTION.externalName());
        assertEquals(Attribute.CLIENT_ID, Attribute.fromString().get("client id"));
        assertThrows(UnsupportedOperationException.class,
                () -> Attribute.fromString().put("unknown", Attribute.VERSION));
    }

    @Test
    void preservesUnsignedIncarnationIdentity() {
        DbIncarnation incarnation = new DbIncarnation(
                0xFFFF_FFFFL, Scn.of(100), Scn.of(50), "CURRENT", 200, 0xFFFF_FFFEL);

        assertTrue(incarnation.isCurrent());
        assertEquals("(4294967295, 100, 50, CURRENT, 200, 4294967294)", incarnation.toString());

        DbIncarnation old = new DbIncarnation(1, Scn.of(10), Scn.zero(), "PARENT", 2, 0);
        assertFalse(old.isCurrent());
        assertThrows(IllegalArgumentException.class,
                () -> new DbIncarnation(-1, Scn.zero(), Scn.zero(), "CURRENT", 0, 0));
    }
}
