/*
 * Java translation derived from LWN buffering and ordering in
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

import java.util.List;

public final class AssembledLwn {
    private final long startBlock;
    private final long endBlock;
    private final Scn scn;
    private final RedoTime timestamp;
    private final List<RedoLwnHeader> partHeaders;
    private final List<AssembledRedoRecord> records;

    public AssembledLwn(long startBlock, long endBlock, Scn scn, RedoTime timestamp,
                        List<RedoLwnHeader> partHeaders, List<AssembledRedoRecord> records) {
        this.startBlock = startBlock;
        this.endBlock = endBlock;
        this.scn = scn;
        this.timestamp = timestamp;
        this.partHeaders = List.copyOf(partHeaders);
        this.records = List.copyOf(records);
    }

    public long startBlock() {
        return startBlock;
    }

    public long endBlock() {
        return endBlock;
    }

    public long consumedBlocks() {
        return endBlock - startBlock;
    }

    public Scn scn() {
        return scn;
    }

    public RedoTime timestamp() {
        return timestamp;
    }

    public List<RedoLwnHeader> partHeaders() {
        return partHeaders;
    }

    public List<AssembledRedoRecord> records() {
        return records;
    }
}
