/*
 * Java translation derived from OpenLogReplicator LobCtx list-map entries.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.lob;

import java.util.ArrayList;
import java.util.List;

final class RedoLobListPage {
    private final List<RedoLobExtent> extents = new ArrayList<>();
    private long nextPage;

    void setNextPage(long newNextPage) {
        nextPage = newNextPage;
    }

    long nextPage() {
        return nextPage;
    }

    void replace(List<RedoLobExtent> newExtents) {
        extents.clear();
        extents.addAll(newExtents);
    }

    void append(int startIndex, List<RedoLobExtent> newExtents) {
        while (extents.size() < startIndex) {
            extents.add(new RedoLobExtent(0, 0));
        }
        int newSize = startIndex + newExtents.size();
        while (extents.size() > newSize) {
            extents.remove(extents.size() - 1);
        }
        for (int index = 0; index < newExtents.size(); index++) {
            int target = startIndex + index;
            if (target < extents.size()) {
                extents.set(target, newExtents.get(index));
            } else {
                extents.add(newExtents.get(index));
            }
        }
    }

    List<RedoLobExtent> extents() {
        return extents;
    }
}
