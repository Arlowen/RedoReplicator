/*
 * Java translation derived from OpenLogReplicator parser/Transaction.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.state.RedoPosition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class RedoTransaction {
    private final Xid xid;
    private final int containerId;
    private final int thread;
    private final RedoPosition beginPosition;
    private final RedoTime beginTimestamp;
    private final Map<Attribute, String> attributes;
    private final List<RedoTransactionEntry> entries;

    RedoTransaction(RedoLogRecord begin, RedoPosition beginPosition) {
        xid = begin.xid;
        containerId = begin.conId;
        thread = begin.thread;
        this.beginPosition = beginPosition;
        beginTimestamp = begin.timestamp;
        attributes = new EnumMap<>(Attribute.class);
        entries = new ArrayList<>();
    }

    Xid xid() {
        return xid;
    }

    RedoPosition beginPosition() {
        return beginPosition;
    }

    Map<Attribute, String> attributes() {
        return attributes;
    }

    void add(RedoTransactionEntry entry) {
        entries.add(entry);
    }

    void rollbackPair(RedoLogRecord inverse) {
        while (!entries.isEmpty()) {
            RedoTransactionEntry last = entries.get(entries.size() - 1);
            int lastRedoOpCode = 0;
            if (last.second().isPresent()) {
                lastRedoOpCode = last.second().orElseThrow().opCode;
            }
            if (isIndexOperation(lastRedoOpCode)) {
                entries.remove(entries.size() - 1);
                continue;
            }
            boolean matches = inverseMatches(lastRedoOpCode, inverse.opCode)
                    && last.first().obj == inverse.obj;
            if (!matches) {
                throw new RedoLogException(50044,
                        "Partial rollback does not match buffered operation for "
                                + xid);
            }
            entries.remove(entries.size() - 1);
            return;
        }
        throw new RedoLogException(50044,
                "Partial rollback reached an empty transaction: " + xid);
    }

    void rollbackSingle(RedoLogRecord inverse) {
        while (!entries.isEmpty()) {
            RedoTransactionEntry last = entries.get(entries.size() - 1);
            int lastRedoOpCode = 0;
            if (last.second().isPresent()) {
                lastRedoOpCode = last.second().orElseThrow().opCode;
            }
            if (isIndexOperation(lastRedoOpCode)) {
                entries.remove(entries.size() - 1);
                continue;
            }
            boolean rollbackCandidate = lastRedoOpCode == 0
                    || lastRedoOpCode == 0x0B10
                    || lastRedoOpCode == 0x0513
                    || lastRedoOpCode == 0x0514;
            if (!rollbackCandidate || last.first().obj != inverse.obj) {
                throw new RedoLogException(50044,
                        "Single partial rollback does not match buffered operation for "
                                + xid);
            }
            entries.remove(entries.size() - 1);
            return;
        }
        throw new RedoLogException(50044,
                "Single partial rollback reached an empty transaction: " + xid);
    }

    CommittedRedoTransaction commit(RedoLogRecord commit) {
        RedoPosition commitPosition = new RedoPosition(
                commit.scn, commit.thread, commit.sequence, commit.fileOffset);
        return new CommittedRedoTransaction(
                xid,
                containerId,
                thread,
                beginPosition,
                beginTimestamp,
                commitPosition,
                commit.timestamp,
                attributes,
                entries);
    }

    int entryCount() {
        return entries.size();
    }

    private static boolean isIndexOperation(int opCode) {
        return opCode == 0x0A02 || opCode == 0x0A08
                || opCode == 0x0A12 || opCode == 0x1A02;
    }

    private static boolean inverseMatches(
            int bufferedOpCode, int inverseOpCode) {
        if (bufferedOpCode == 0x0B02) {
            return inverseOpCode == 0x0B03;
        }
        if (bufferedOpCode == 0x0B03) {
            return inverseOpCode == 0x0B02;
        }
        if (bufferedOpCode == 0x0B0B) {
            return inverseOpCode == 0x0B0C;
        }
        if (bufferedOpCode == 0x0B0C) {
            return inverseOpCode == 0x0B0B;
        }
        return bufferedOpCode == inverseOpCode
                && (bufferedOpCode == 0x0B05
                || bufferedOpCode == 0x0B06
                || bufferedOpCode == 0x0B08
                || bufferedOpCode == 0x0B16);
    }
}
