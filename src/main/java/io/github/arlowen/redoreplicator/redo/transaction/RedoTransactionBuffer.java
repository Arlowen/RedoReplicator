/*
 * Java translation derived from OpenLogReplicator parser/Parser.cpp,
 * Transaction.cpp and TransactionBuffer.cpp.
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
import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.state.RedoPosition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RedoTransactionBuffer implements AutoCloseable {
    public static final int FLG_MULTIBLOCK_UNDO_HEAD = 0x0001;
    public static final int FLG_MULTIBLOCK_UNDO_TAIL = 0x0002;
    public static final int FLG_LAST_BUFFER_SPLIT = 0x0004;
    public static final int FLG_ROLLBACK_COMMIT = 0x0004;
    public static final int FLG_USER_UNDO_DONE = 0x0010;
    public static final int FLG_MULTIBLOCK_UNDO_MIDDLE = 0x0100;

    private final Map<TransactionSlotKey, RedoTransaction> transactions;
    private final TransactionSpillManager spillManager;
    private final long memoryLimitBytes;
    private final Map<LobId, Map<Long, RedoLogRecord>> orphanedLobs;
    private final Map<LobId, TransactionSlotKey> lobOwners;
    private final RedoLobIndexResolver lobIndexResolver;

    public RedoTransactionBuffer() {
        transactions = new LinkedHashMap<>();
        orphanedLobs = new LinkedHashMap<>();
        lobOwners = new LinkedHashMap<>();
        lobIndexResolver = new RedoLobIndexResolver();
        spillManager = null;
        memoryLimitBytes = Long.MAX_VALUE;
    }

    public RedoTransactionBuffer(
            Path spillDirectory, long memoryLimitBytes) {
        if (memoryLimitBytes <= 0) {
            throw new IllegalArgumentException(
                    "Transaction memory limit must be positive");
        }
        transactions = new LinkedHashMap<>();
        orphanedLobs = new LinkedHashMap<>();
        lobOwners = new LinkedHashMap<>();
        lobIndexResolver = new RedoLobIndexResolver();
        spillManager = new TransactionSpillManager(spillDirectory);
        this.memoryLimitBytes = memoryLimitBytes;
    }

    public List<CommittedRedoTransaction> accept(
            List<RedoLogRecord> records, RedoPosition lwnPosition) {
        List<CommittedRedoTransaction> committed = new ArrayList<>();
        int index = 0;
        while (index < records.size()) {
            RedoLogRecord record = records.get(index);
            RedoLogRecord next = null;
            if (index + 1 < records.size()) {
                next = records.get(index + 1);
            }

            if (record.opCode == 0x0501) {
                rejectSplitUndo(record);
                if (next != null && isUndoPairSecond(next.opCode)) {
                    appendPair(record, next);
                    index += 2;
                    continue;
                }
                appendSingle(record);
                index++;
                continue;
            }
            if (isRollbackRow(record.opCode)
                    && next != null && isPartialRollback(next.opCode)) {
                rollbackPair(record, next);
                index += 2;
                continue;
            }
            if (record.opCode == 0x0502) {
                begin(record, lwnPosition);
            } else if (record.opCode == 0x0504) {
                commit(record).ifPresent(committed::add);
            } else if (isPartialRollback(record.opCode)) {
                rollbackSingle(record);
            } else if (record.opCode == 0x1301
                    || record.opCode == 0x1A06) {
                appendLob(record);
            } else if (record.opCode == 0x1801) {
                appendSingle(record);
            }
            index++;
        }
        return List.copyOf(committed);
    }

    public void begin(RedoLogRecord record, RedoPosition lwnPosition) {
        requireXid(record.xid, "begin");
        if (record.xid.sequence() == 0) {
            return;
        }
        TransactionSlotKey key = TransactionSlotKey.of(
                record.conId, record.xid);
        RedoTransaction current = transactions.get(key);
        if (current != null) {
            throw conflict(record.xid, current.xid());
        }
        RedoPosition beginPosition = new RedoPosition(
                record.scn,
                record.thread,
                record.sequence,
                lwnPosition.offset());
        transactions.put(key, new RedoTransaction(record, beginPosition));
    }

    public boolean appendPair(
            RedoLogRecord undo, RedoLogRecord redo) {
        if (undo.opCode != 0x0501) {
            throw new IllegalArgumentException(
                    "A transaction pair must start with opcode 0x0501");
        }
        boolean lobIndex = lobIndexResolver.resolve(undo, redo);
        RedoTransaction transaction = findExact(undo.xid, undo.conId);
        if (transaction == null) {
            return false;
        }
        if (lobIndex) {
            TransactionSlotKey owner = lobOwners.get(redo.lobId);
            if (owner != null) {
                RedoTransaction parent = transactions.get(owner);
                if (parent != null && !parent.xid().equals(undo.xid)) {
                    undo.xid = parent.xid();
                    redo.xid = parent.xid();
                    undo.conId = owner.containerId();
                    redo.conId = owner.containerId();
                    transaction = parent;
                }
            }
        }
        propagateObjectIdentity(undo, redo);
        if (undo.bdba != 0 && redo.bdba != 0 && undo.bdba != redo.bdba) {
            throw new RedoLogException(50045,
                    "Undo and redo block addresses do not match for "
                            + undo.xid);
        }
        if (redo.opCode != 0x0B04) {
            registerLobOwner(
                    transaction, undo.conId, redo, lobIndex);
            transaction.add(RedoTransactionEntry.pair(undo, redo));
            spillIfNeeded();
        }
        return true;
    }

    public boolean appendSingle(RedoLogRecord record) {
        if (record.xid.isEmpty()) {
            return false;
        }
        RedoTransaction transaction = findExact(record.xid, record.conId);
        if (transaction == null) {
            return false;
        }
        transaction.add(RedoTransactionEntry.single(record));
        spillIfNeeded();
        return true;
    }

    public Optional<RedoLogRecord> prepareUndo(
            RedoLogRecord record,
            boolean paired,
            RedoUndoBlockMerger merger) {
        if (record.opCode != 0x0501) {
            throw new IllegalArgumentException(
                    "Only opcode 0x0501 can contain split undo");
        }
        RedoTransaction transaction = findExact(record.xid, record.conId);
        if (transaction == null) {
            if (hasSplitFlag(record)) {
                return Optional.empty();
            }
            return Optional.of(record);
        }

        if (paired) {
            if (!transaction.splitUndoPending()) {
                if ((record.flg & FLG_MULTIBLOCK_UNDO_HEAD) != 0) {
                    throw new RedoLogException(50043,
                            "Split undo HEAD has no buffered continuation at offset "
                                    + record.fileOffset + " xid: " + record.xid);
                }
                rejectSplitUndo(record);
                return Optional.of(record);
            }
            if ((record.flg & FLG_MULTIBLOCK_UNDO_HEAD) == 0) {
                throw new RedoLogException(50043,
                        "Expected split undo HEAD at offset "
                                + record.fileOffset + " xid: " + record.xid);
            }
            RedoLogRecord previous = transaction.removeLastSplitUndo();
            RedoLogRecord merged = merger.merge(record, previous);
            merger.writeKtuFlags(merged);
            transaction.splitUndoPending(false);
            return Optional.of(merged);
        }

        boolean mergedBlock = false;
        if (transaction.splitUndoPending()) {
            if ((record.flg & FLG_MULTIBLOCK_UNDO_MIDDLE) == 0) {
                throw new RedoLogException(50041,
                        "Expected split undo MID at offset "
                                + record.fileOffset + " xid: " + record.xid);
            }
            RedoLogRecord previous = transaction.removeLastSplitUndo();
            record = merger.merge(record, previous);
            mergedBlock = true;
        }

        int continuingFlags = FLG_MULTIBLOCK_UNDO_TAIL
                | FLG_MULTIBLOCK_UNDO_MIDDLE;
        if (mergedBlock || (record.flg & continuingFlags) != 0) {
            transaction.splitUndoPending(
                    (record.flg & continuingFlags) != 0);
            transaction.add(RedoTransactionEntry.single(record));
            spillIfNeeded();
            return Optional.empty();
        }
        if ((record.flg & FLG_MULTIBLOCK_UNDO_HEAD) != 0) {
            throw new RedoLogException(50043,
                    "Split undo HEAD is not paired at offset "
                            + record.fileOffset + " xid: " + record.xid);
        }
        return Optional.of(record);
    }

    public boolean rollbackPair(
            RedoLogRecord inverse, RedoLogRecord rollback) {
        RedoTransaction transaction = findForRollback(rollback);
        if (transaction == null) {
            return false;
        }
        propagateObjectIdentity(inverse, rollback);
        if (inverse.bdba != 0 && rollback.bdba != 0
                && inverse.bdba != rollback.bdba) {
            throw new RedoLogException(50045,
                    "Rollback block addresses do not match for "
                            + transaction.xid());
        }
        transaction.rollbackPair(inverse);
        return true;
    }

    public boolean rollbackSingle(RedoLogRecord rollback) {
        if ((rollback.flg & FLG_USER_UNDO_DONE) == 0) {
            return false;
        }
        RedoTransaction transaction = findForRollback(rollback);
        if (transaction == null) {
            return false;
        }
        transaction.rollbackSingle(rollback);
        return true;
    }

    public Optional<CommittedRedoTransaction> commit(
            RedoLogRecord record) {
        requireXid(record.xid, "commit");
        TransactionSlotKey key = TransactionSlotKey.of(
                record.conId, record.xid);
        RedoTransaction transaction = transactions.get(key);
        if (transaction == null) {
            return Optional.empty();
        }
        if (!transaction.xid().equals(record.xid)) {
            throw conflict(record.xid, transaction.xid());
        }
        if ((record.flg & FLG_ROLLBACK_COMMIT) != 0) {
            transactions.remove(key);
            removeLobOwners(key);
            transaction.discard();
            return Optional.empty();
        }
        if (transaction.splitUndoPending()) {
            throw new RedoLogException(50041,
                    "Transaction committed with incomplete split undo: "
                            + record.xid);
        }
        transactions.remove(key);
        removeLobOwners(key);
        if (transaction.entryCount() == 0) {
            transaction.discard();
            return Optional.empty();
        }
        return Optional.of(transaction.commit(record));
    }

    public Optional<RedoPosition> lowWatermark() {
        RedoPosition earliest = null;
        for (RedoTransaction transaction : transactions.values()) {
            RedoPosition candidate = transaction.beginPosition();
            if (earliest == null || compare(candidate, earliest) < 0) {
                earliest = candidate;
            }
        }
        return Optional.ofNullable(earliest);
    }

    public Optional<Map<Attribute, String>> attributes(
            Xid xid, int containerId) {
        RedoTransaction transaction = findExact(xid, containerId);
        if (transaction == null) {
            return Optional.empty();
        }
        return Optional.of(transaction.attributes());
    }

    public int openTransactionCount() {
        return transactions.size();
    }

    public int bufferedEntryCount() {
        int count = 0;
        for (RedoTransaction transaction : transactions.values()) {
            count += transaction.entryCount();
        }
        return count;
    }

    public long bufferedMemoryBytes() {
        long bytes = 0;
        for (RedoTransaction transaction : transactions.values()) {
            bytes += transaction.memoryBytes();
        }
        return bytes;
    }

    public int spilledTransactionCount() {
        int count = 0;
        for (RedoTransaction transaction : transactions.values()) {
            if (transaction.spilled()) {
                count++;
            }
        }
        return count;
    }

    public int orphanedLobCount() {
        int count = 0;
        for (Map<Long, RedoLogRecord> records : orphanedLobs.values()) {
            count += records.size();
        }
        return count;
    }

    public void clear() {
        for (RedoTransaction transaction : transactions.values()) {
            transaction.discard();
        }
        transactions.clear();
        orphanedLobs.clear();
        lobOwners.clear();
    }

    @Override
    public void close() {
        clear();
        if (spillManager != null) {
            spillManager.close();
        }
    }

    private RedoTransaction findExact(Xid xid, int containerId) {
        if (xid.isEmpty()) {
            return null;
        }
        RedoTransaction transaction = transactions.get(
                TransactionSlotKey.of(containerId, xid));
        if (transaction != null && !transaction.xid().equals(xid)) {
            throw conflict(xid, transaction.xid());
        }
        return transaction;
    }

    private boolean appendLob(RedoLogRecord record) {
        if (!record.xid.isEmpty()) {
            return appendSingle(record);
        }
        TransactionSlotKey owner = lobOwners.get(record.lobId);
        if (owner == null) {
            orphanedLobs.computeIfAbsent(
                            record.lobId, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(record.dba, record);
            return false;
        }
        RedoTransaction transaction = transactions.get(owner);
        if (transaction == null) {
            lobOwners.remove(record.lobId);
            return false;
        }
        record.xid = transaction.xid();
        record.conId = owner.containerId();
        transaction.add(RedoTransactionEntry.single(record));
        spillIfNeeded();
        return true;
    }

    private void registerLobOwner(
            RedoTransaction transaction, int containerId,
            RedoLogRecord record, boolean lobIndex) {
        if (!lobIndex) {
            return;
        }
        TransactionSlotKey owner = TransactionSlotKey.of(
                containerId, transaction.xid());
        lobOwners.put(record.lobId, owner);
        Map<Long, RedoLogRecord> orphans = orphanedLobs.remove(record.lobId);
        if (orphans == null) {
            return;
        }
        for (RedoLogRecord orphan : orphans.values()) {
            orphan.xid = transaction.xid();
            orphan.conId = owner.containerId();
            transaction.add(RedoTransactionEntry.single(orphan));
        }
    }

    private void removeLobOwners(TransactionSlotKey owner) {
        lobOwners.entrySet().removeIf(entry -> entry.getValue().equals(owner));
    }

    private RedoTransaction findForRollback(RedoLogRecord rollback) {
        TransactionSlotKey key = TransactionSlotKey.of(
                rollback.conId, rollback.usn, rollback.slt);
        return transactions.get(key);
    }

    private void spillIfNeeded() {
        if (spillManager == null) {
            return;
        }
        while (bufferedMemoryBytes() > memoryLimitBytes) {
            RedoTransaction largest = null;
            long largestBytes = 0;
            for (RedoTransaction transaction : transactions.values()) {
                long bytes = transaction.memoryBytes();
                if (bytes > largestBytes) {
                    largest = transaction;
                    largestBytes = bytes;
                }
            }
            if (largest == null) {
                return;
            }
            largest.spill(spillManager);
        }
    }

    private static void propagateObjectIdentity(
            RedoLogRecord first, RedoLogRecord second) {
        if (first.dataObj != 0) {
            second.obj = first.obj;
            second.dataObj = first.dataObj;
            return;
        }
        first.obj = second.obj;
        first.dataObj = second.dataObj;
    }

    private static void rejectSplitUndo(RedoLogRecord record) {
        if (hasSplitFlag(record)) {
            throw new RedoLogException(50041,
                    "Split undo must be prepared before buffering at offset "
                            + record.fileOffset);
        }
    }

    private static boolean hasSplitFlag(RedoLogRecord record) {
        int splitFlags = FLG_MULTIBLOCK_UNDO_HEAD
                | FLG_MULTIBLOCK_UNDO_TAIL
                | FLG_MULTIBLOCK_UNDO_MIDDLE;
        return (record.flg & splitFlags) != 0;
    }

    private static boolean isUndoPairSecond(int opCode) {
        return (opCode & 0xFF00) == 0x0A00
                || (opCode & 0xFF00) == 0x0B00
                || opCode == 0x0513 || opCode == 0x0514
                || opCode == 0x1A02;
    }

    private static boolean isRollbackRow(int opCode) {
        return (opCode & 0xFF00) == 0x0B00;
    }

    private static boolean isPartialRollback(int opCode) {
        return opCode == 0x0506 || opCode == 0x050B;
    }

    private static int compare(RedoPosition first, RedoPosition second) {
        int sequence = first.sequence().compareTo(second.sequence());
        if (sequence != 0) {
            return sequence;
        }
        return first.offset().compareTo(second.offset());
    }

    private static void requireXid(Xid xid, String operation) {
        if (xid.isEmpty()) {
            throw new RedoLogException(50039,
                    "Missing XID for transaction " + operation);
        }
    }

    private static RedoLogException conflict(
            Xid requested, Xid current) {
        return new RedoLogException(50039,
                "Transaction " + requested + " conflicts with " + current);
    }
}
