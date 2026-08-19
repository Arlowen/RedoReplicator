/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LobKeyParityTest {
    private static final String FIXTURE =
            "/fixtures/value-types/openlogreplicator-6bc92bc1.properties";

    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws Exception {
        baseline = new Properties();
        try (InputStream input = getClass().getResourceAsStream(FIXTURE)) {
            assertNotNull(input);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedEqualityOrderingAndHashSemantics() {
        LobId firstId = LobId.of(
                new byte[]{0, 0, 0, 1, 2, 3, 4, 5, 6, 7});
        LobId secondId = LobId.of(
                new byte[]{0, 0, 0, 1, 2, 3, 4, 5, 6, 8});
        LobKey first = new LobKey(firstId, 16);
        LobKey same = new LobKey(firstId, 16);
        LobKey nextPage = new LobKey(firstId, 17);
        LobKey nextId = new LobKey(secondId, 1);

        assertEquals(baseline.getProperty("lobkey.equal"),
                Boolean.toString(first.equals(same)));
        assertEquals(baseline.getProperty("lobkey.pageLess"),
                Boolean.toString(first.compareTo(nextPage) < 0));
        assertEquals(baseline.getProperty("lobkey.idLess"),
                Boolean.toString(nextPage.compareTo(nextId) < 0));
        assertEquals(baseline.getProperty("lobkey.different"),
                Boolean.toString(!first.equals(nextPage)));
        assertEquals(3, new TreeSet<>(
                java.util.List.of(nextPage, nextId, first, same)).size());
        assertEquals(1, new HashSet<>(
                java.util.List.of(first, same)).size());
    }
}
