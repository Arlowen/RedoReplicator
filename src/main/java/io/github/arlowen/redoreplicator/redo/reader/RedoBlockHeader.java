/*
 * Java translation derived from OpenLogReplicator block header handling in
 * src/reader/Reader.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.redo.common.Seq;

public final class RedoBlockHeader {
    private final boolean empty;
    private final int type;
    private final long blockNumber;
    private final Seq sequence;
    private final int checksum;

    public RedoBlockHeader(boolean empty, int type, long blockNumber, Seq sequence, int checksum) {
        this.empty = empty;
        this.type = type;
        this.blockNumber = blockNumber;
        this.sequence = sequence;
        this.checksum = checksum;
    }

    public boolean isEmpty() {
        return empty;
    }

    public int type() {
        return type;
    }

    public long blockNumber() {
        return blockNumber;
    }

    public Seq sequence() {
        return sequence;
    }

    public int checksum() {
        return checksum;
    }
}
