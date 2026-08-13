/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import java.util.Objects;

public record OracleContainer(int id, String name) {
    public static final String ROOT_NAME = "CDB$ROOT";

    public OracleContainer {
        if (id < 0) {
            throw new IllegalArgumentException(
                    "Oracle container id must not be negative");
        }
        Objects.requireNonNull(name, "name");
    }
}
