/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.config;

import java.util.List;
import java.util.regex.Pattern;

public final class TableFilter {
    private final List<Pattern> includes;
    private final List<Pattern> excludes;

    public TableFilter(List<Pattern> includes, List<Pattern> excludes) {
        this.includes = List.copyOf(includes);
        this.excludes = List.copyOf(excludes);
    }

    public boolean matches(String qualifiedTableName) {
        for (Pattern exclude : excludes) {
            if (exclude.matcher(qualifiedTableName).matches()) {
                return false;
            }
        }
        for (Pattern include : includes) {
            if (include.matcher(qualifiedTableName).matches()) {
                return true;
            }
        }
        return false;
    }
}
