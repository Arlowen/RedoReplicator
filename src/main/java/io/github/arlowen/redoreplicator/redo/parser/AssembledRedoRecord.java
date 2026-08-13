/*
 * Java translation derived from redo record buffering in
 * OpenLogReplicator src/parser/Parser.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

public final class AssembledRedoRecord implements Comparable<AssembledRedoRecord> {
    private final LwnMember member;
    private final byte[] data;

    public AssembledRedoRecord(LwnMember member, byte[] data) {
        this.member = member;
        this.data = data;
    }

    public LwnMember member() {
        return member;
    }

    public byte[] data() {
        return data;
    }

    @Override
    public int compareTo(AssembledRedoRecord other) {
        return member.compareTo(other.member);
    }
}
