/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.error.DataException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OracleContainerRegistryTest {

    @Test
    void resolvesKnownContainersAndStopsOnUnknownRedoContainer() {
        OracleContainerRegistry registry = new OracleContainerRegistry(
                List.of(
                        new OracleContainer(1, "CDB$ROOT"),
                        new OracleContainer(3, "FREEPDB1")));

        assertEquals("FREEPDB1", registry.requireName(3));
        DataException error = assertThrows(
                DataException.class, () -> registry.requireName(4));
        assertEquals(50071, error.getErrorCode());
    }
}
