/*
 * Java translation derived from redo record header handling in
 * OpenLogReplicator src/parser/Parser.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

public final class RedoRecordHeader {
    private final long recordSize;
    private final int validity;
    private final int headerSize;
    private final long containerUid;

    public RedoRecordHeader(long recordSize, int validity, int headerSize, long containerUid) {
        this.recordSize = recordSize;
        this.validity = validity;
        this.headerSize = headerSize;
        this.containerUid = containerUid;
    }

    public long recordSize() {
        return recordSize;
    }

    public int validity() {
        return validity;
    }

    public int headerSize() {
        return headerSize;
    }

    public long containerUid() {
        return containerUid;
    }

    public boolean isValid() {
        return (validity & 0x01) != 0;
    }

    public boolean isExtended() {
        return (validity & 0x04) != 0;
    }
}
