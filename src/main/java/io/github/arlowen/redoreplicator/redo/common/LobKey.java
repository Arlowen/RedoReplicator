/*
 * Java translation derived from OpenLogReplicator
 * src/common/LobKey.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

public record LobKey(LobId lobId, long page)
        implements Comparable<LobKey> {

    @Override
    public int compareTo(LobKey other) {
        int lobIdComparison = lobId.compareTo(other.lobId);
        if (lobIdComparison != 0) {
            return lobIdComparison;
        }
        return Long.compare(page, other.page);
    }
}
