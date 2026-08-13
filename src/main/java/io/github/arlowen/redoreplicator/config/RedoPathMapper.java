/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class RedoPathMapper {
    private final List<RedoPathMapping> mappings;

    public RedoPathMapper(List<RedoPathMapping> mappings) {
        this.mappings = List.copyOf(mappings);
    }

    public Optional<Path> map(String oraclePath) {
        String source = Path.of(oraclePath).normalize().toString();
        RedoPathMapping selected = null;
        int selectedLength = -1;
        for (RedoPathMapping mapping : mappings) {
            String prefix = Path.of(mapping.oracle()).normalize().toString();
            boolean matches = source.equals(prefix)
                    || source.startsWith(prefix + "/");
            if (matches && prefix.length() > selectedLength) {
                selected = mapping;
                selectedLength = prefix.length();
            }
        }
        if (selected == null) {
            return Optional.empty();
        }

        String suffix = source.substring(selectedLength);
        if (suffix.startsWith("/")) {
            suffix = suffix.substring(1);
        }
        Path local = Path.of(selected.local()).normalize();
        if (!suffix.isEmpty()) {
            local = local.resolve(suffix).normalize();
        }
        return Optional.of(local);
    }
}
