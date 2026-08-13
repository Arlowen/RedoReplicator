/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemTransactionRegistryTest {

    @Test
    void isolatesManagersByOracleContainerId() {
        SystemTransactionManager manager = new SystemTransactionManager(
                SystemDictionaryState.empty(), "FREEPDB1",
                873, 2000, StandardCharsets.UTF_8,
                new TableSchemaJsonCodec());
        SystemTransactionRegistry registry = new SystemTransactionRegistry(
                Map.of(3, manager));

        assertSame(manager, registry.require(3));
        DataException error = assertThrows(
                DataException.class, () -> registry.require(4));
        assertEquals(50071, error.getErrorCode());
    }
}
