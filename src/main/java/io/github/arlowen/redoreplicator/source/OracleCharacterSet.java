/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import java.util.Objects;

public record OracleCharacterSet(String name, long id) {
    public OracleCharacterSet {
        Objects.requireNonNull(name, "name");
    }
}
