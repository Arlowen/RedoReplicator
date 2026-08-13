/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.error.DataException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OracleContainerRegistry {
    private final Map<Integer, OracleContainer> containersById;

    public OracleContainerRegistry(List<OracleContainer> containers) {
        Map<Integer, OracleContainer> indexed = new LinkedHashMap<>();
        for (OracleContainer container : containers) {
            OracleContainer previous = indexed.putIfAbsent(
                    container.id(), container);
            if (previous != null && !previous.equals(container)) {
                throw new IllegalArgumentException(
                        "Duplicate Oracle container id " + container.id());
            }
        }
        containersById = Collections.unmodifiableMap(
                new LinkedHashMap<>(indexed));
    }

    public String requireName(int containerId) {
        OracleContainer container = containersById.get(containerId);
        if (container == null) {
            throw new DataException(50071,
                    "Redo references unknown Oracle container id "
                            + containerId);
        }
        return container.name();
    }

    public List<OracleContainer> containers() {
        return List.copyOf(containersById.values());
    }
}
