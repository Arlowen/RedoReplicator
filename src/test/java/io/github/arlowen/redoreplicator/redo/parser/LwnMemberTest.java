/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.common.Scn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LwnMemberTest {
    @Test
    void ordersByScnSubScnBlockAndPageOffset() {
        LwnMember first = new LwnMember(16, Scn.of(100), 80, 2, 1);
        LwnMember laterSubScn = new LwnMember(16, Scn.of(100), 80, 2, 2);
        LwnMember laterBlock = new LwnMember(16, Scn.of(100), 80, 3, 2);
        LwnMember laterOffset = new LwnMember(20, Scn.of(100), 80, 3, 2);

        assertTrue(first.compareTo(laterSubScn) < 0);
        assertTrue(laterSubScn.compareTo(laterBlock) < 0);
        assertTrue(laterBlock.compareTo(laterOffset) < 0);
    }
}
