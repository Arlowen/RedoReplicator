/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.config;

import java.util.List;

public record DatabaseConfiguration(
        String url,
        String username,
        String password,
        List<RedoPathMapping> redoPathMappings) {
    public DatabaseConfiguration {
        if (redoPathMappings != null) {
            redoPathMappings = List.copyOf(redoPathMappings);
        }
    }

    @Override
    public String toString() {
        return "DatabaseConfiguration[url=" + url
                + ", username=" + username
                + ", password=<redacted>, redoPathMappings="
                + redoPathMappings + "]";
    }
}
