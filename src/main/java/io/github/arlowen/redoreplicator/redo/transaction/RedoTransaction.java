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
    private TransactionSpillManager spillManager;
    private TransactionSpillFile spillFile;
    private int entryCount;

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
        if (spillFile == null) {
            entries.add(entry);
        } else {
            spillFile.append(entry);
        }
        entryCount++;
    }

    void spill(TransactionSpillManager manager) {
        if (spillFile != null || entries.isEmpty()) {
            return;
        }
        spillManager = manager;
        spillFile = manager.open(containerId, xid);
        for (RedoTransactionEntry entry : entries) {
            spillFile.append(entry);
        }
        entries.clear();
    }

    boolean spilled() {
        return spillFile != null;
    }

    long memoryBytes() {
        long bytes = 0;
        for (RedoTransactionEntry entry : entries) {
            bytes += entry.estimatedMemoryBytes();
        }
        return bytes;
    }

    void rollbackPair(RedoLogRecord inverse) {
        while (entryCount > 0) {
            RedoTransactionEntry last = lastEntry();
            int lastRedoOpCode = 0;
            if (last.second().isPresent()) {
                lastRedoOpCode = last.second().orElseThrow().opCode;
            }
            if (isIndexOperation(lastRedoOpCode)) {
                removeLast();
                continue;
            }
            boolean matches = inverseMatches(lastRedoOpCode, inverse.opCode)
                    && last.first().obj == inverse.obj;
            if (!matches) {
                throw new RedoLogException(50044,
                        "Partial rollback does not match buffered operation for "
                                + xid);
            }
            removeLast();
            return;
        }
        throw new RedoLogException(50044,
                "Partial rollback reached an empty transaction: " + xid);
    }

    void rollbackSingle(RedoLogRecord inverse) {
        while (entryCount > 0) {
            RedoTransactionEntry last = lastEntry();
            int lastRedoOpCode = 0;
            if (last.second().isPresent()) {
                lastRedoOpCode = last.second().orElseThrow().opCode;
            }
            if (isIndexOperation(lastRedoOpCode)) {
                removeLast();
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
            removeLast();
            return;
        }
        throw new RedoLogException(50044,
                "Single partial rollback reached an empty transaction: " + xid);
    }

    CommittedRedoTransaction commit(RedoLogRecord commit) {
        RedoPosition commitPosition = new RedoPosition(
                commit.scn, commit.thread, commit.sequence, commit.fileOffset);
        List<RedoTransactionEntry> committedEntries = new ArrayList<>(
                entryCount);
        if (spillFile != null) {
            committedEntries.addAll(spillFile.readAll());
        }
        committedEntries.addAll(entries);
        CommittedRedoTransaction committed = new CommittedRedoTransaction(
                xid,
                containerId,
                thread,
                beginPosition,
                beginTimestamp,
                commitPosition,
                commit.timestamp,
                attributes,
                committedEntries);
        discard();
        return committed;
    }

    int entryCount() {
        return entryCount;
    }

    void discard() {
        entries.clear();
        entryCount = 0;
        if (spillFile != null) {
            spillManager.release(spillFile);
            spillFile = null;
            spillManager = null;
        }
    }

    private RedoTransactionEntry lastEntry() {
        if (!entries.isEmpty()) {
            return entries.get(entries.size() - 1);
        }
        return spillFile.readLast();
    }

    private void removeLast() {
        if (!entries.isEmpty()) {
            entries.remove(entries.size() - 1);
        } else {
            spillFile.removeLast();
        }
        entryCount--;
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
