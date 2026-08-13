/*
 * Java translation derived from OpenLogReplicator: src/parser/Parser.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.common.Scn;

public final class LwnMember implements Comparable<LwnMember> {
    private final int pageOffset;
    private final Scn scn;
    private final long size;
    private final long block;
    private final int subScn;

    public LwnMember(int pageOffset, Scn scn, long size, long block, int subScn) {
        this.pageOffset = pageOffset;
        this.scn = scn;
        this.size = size;
        this.block = block;
        this.subScn = subScn;
    }

    public int pageOffset() {
        return pageOffset;
    }

    public Scn scn() {
        return scn;
    }

    public long size() {
        return size;
    }

    public long block() {
        return block;
    }

    public int subScn() {
        return subScn;
    }

    @Override
    public int compareTo(LwnMember other) {
        int comparison = scn.compareTo(other.scn);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compareUnsigned(subScn, other.subScn);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Long.compareUnsigned(block, other.block);
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compareUnsigned(pageOffset, other.pageOffset);
    }
}
