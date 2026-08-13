/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SystemTransactionRegistry {
    private final Map<Integer, SystemTransactionManager> managers;

    public SystemTransactionRegistry(
            Map<Integer, SystemTransactionManager> managers) {
        this.managers = Map.copyOf(new LinkedHashMap<>(managers));
    }

    public SystemTransactionManager require(int containerId) {
        SystemTransactionManager manager = managers.get(containerId);
        if (manager == null) {
            throw new DataException(50071,
                    "No system dictionary state for Oracle container id "
                            + containerId);
        }
        return manager;
    }
}
