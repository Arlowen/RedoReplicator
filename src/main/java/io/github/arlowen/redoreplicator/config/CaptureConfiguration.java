/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.config;

import java.util.List;

public record CaptureConfiguration(
        Long startScn,
        List<String> includeTables,
        List<String> excludeTables) {
    public CaptureConfiguration {
        if (includeTables != null) {
            includeTables = List.copyOf(includeTables);
        }
        if (excludeTables != null) {
            excludeTables = List.copyOf(excludeTables);
        }
    }
}
