/*
 * Java translation derived from OpenLogReplicator src/common/LobData.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.lob;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.LobId;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

final class RedoLobData {
    private final LobId lobId;
    private final Map<Long, NavigableMap<Integer, byte[]>> pages;
    private final NavigableMap<Long, Long> index;
    private int pageSize;
    private long sizePages;
    private int sizeRest;
    private boolean sizeSet;

    RedoLobData(LobId lobId) {
        this.lobId = lobId;
        pages = new TreeMap<>();
        index = new TreeMap<>();
    }

    void addPage(long page, int pageOffset, byte[] data, int newPageSize) {
        if (newPageSize > 0) {
            if (pageSize == 0) {
                pageSize = newPageSize;
            } else if (pageSize != newPageSize) {
                throw invalid(50003, "inconsistent page size "
                        + newPageSize + ", expected " + pageSize);
            }
        }
        pages.computeIfAbsent(page, ignored -> new TreeMap<>())
                .put(pageOffset, data.clone());
    }

    void setPage(long pageNumber, long page) {
        Long previous = index.putIfAbsent(pageNumber, page);
        if (previous != null && previous != page) {
            throw invalid(50004, "page number " + pageNumber
                    + " maps to both " + previous + " and " + page);
        }
    }

    void setSize(long newSizePages, int newSizeRest) {
        sizePages = newSizePages;
        sizeRest = newSizeRest;
        sizeSet = true;
    }

    long sizePages() {
        requireSize();
        return sizePages;
    }

    int sizeRest() {
        requireSize();
        return sizeRest;
    }

    long indexedPage(long pageNumber) {
        Long page = index.get(pageNumber);
        if (page == null) {
            throw invalid(50075,
                    "missing indexed page number " + pageNumber);
        }
        return page;
    }

    byte[] readPage(long page, int size) {
        NavigableMap<Integer, byte[]> fragments = pages.get(page);
        if (fragments == null) {
            throw invalid(50075, "missing LOB page " + page);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        int expectedOffset = 0;
        for (Map.Entry<Integer, byte[]> entry : fragments.entrySet()) {
            if (entry.getKey() != expectedOffset) {
                throw invalid(50075, "LOB page " + page
                        + " has a gap at offset " + expectedOffset);
            }
            byte[] fragment = entry.getValue();
            int remaining = size - output.size();
            if (remaining == 0) {
                break;
            }
            int copySize = Math.min(fragment.length, remaining);
            output.write(fragment, 0, copySize);
            expectedOffset += fragment.length;
        }
        if (output.size() != size) {
            throw invalid(50075, "LOB page " + page + " contains "
                    + output.size() + " bytes, expected " + size);
        }
        return output.toByteArray();
    }

    int pageSize() {
        if (pageSize == 0) {
            throw invalid(50075, "LOB page size is unknown");
        }
        return pageSize;
    }

    private void requireSize() {
        if (!sizeSet) {
            throw invalid(50075, "LOB length is unknown");
        }
    }

    private RedoLogException invalid(int code, String reason) {
        return new RedoLogException(code,
                "Invalid LOB " + lobId.upper() + ": " + reason);
    }
}
