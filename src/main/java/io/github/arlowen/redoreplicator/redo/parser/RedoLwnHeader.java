/*
 * Java translation derived from LWN header handling in
 * OpenLogReplicator src/parser/Parser.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;

public final class RedoLwnHeader {
    private final RedoRecordHeader recordHeader;
    private final int number;
    private final int maximum;
    private final long blockCount;
    private final long length;
    private final Scn scn;
    private final RedoTime timestamp;

    public RedoLwnHeader(RedoRecordHeader recordHeader, int number, int maximum,
                         long blockCount, long length, Scn scn, RedoTime timestamp) {
        this.recordHeader = recordHeader;
        this.number = number;
        this.maximum = maximum;
        this.blockCount = blockCount;
        this.length = length;
        this.scn = scn;
        this.timestamp = timestamp;
    }

    public RedoRecordHeader recordHeader() {
        return recordHeader;
    }

    public int number() {
        return number;
    }

    public int maximum() {
        return maximum;
    }

    public long blockCount() {
        return blockCount;
    }

    public long length() {
        return length;
    }

    public Scn scn() {
        return scn;
    }

    public RedoTime timestamp() {
        return timestamp;
    }
}
