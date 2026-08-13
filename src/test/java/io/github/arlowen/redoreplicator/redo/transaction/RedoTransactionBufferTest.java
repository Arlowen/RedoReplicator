/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoTransactionBufferTest {
    private static final Xid XID_1 = Xid.of(1, 2, 3);
    private static final Xid XID_2 = Xid.of(1, 3, 4);
    private static final long OBJECT_ID = 100;
    private static final long DATA_OBJECT_ID = 101;
    private static final long BLOCK_ADDRESS = 0xF100_0001L;

    @Test
    void reconstructsInterleavedTransactionsInCommitOrder() {
        RedoTransactionBuffer buffer = new RedoTransactionBuffer();
        RedoPosition firstBegin = position(100, 10, 512);
        RedoPosition secondBegin = position(101, 10, 1024);

        assertTrue(buffer.accept(
                List.of(begin(XID_1, 100)), firstBegin).isEmpty());
        assertTrue(buffer.accept(
                List.of(begin(XID_2, 101)), secondBegin).isEmpty());
        assertEquals(firstBegin, buffer.lowWatermark().orElseThrow());

        buffer.accept(List.of(
                undo(XID_1, RedoLogRecord.FB_L), redo(0x0B02),
                undo(XID_2, RedoLogRecord.FB_L), redo(0x0B05)),
                position(110, 10, 1536));
        List<CommittedRedoTransaction> committed = buffer.accept(
                List.of(commit(XID_2, 200, 0), commit(XID_1, 201, 0)),
                position(200, 10, 2048));

        assertEquals(List.of(XID_2, XID_1),
                committed.stream().map(CommittedRedoTransaction::xid).toList());
        assertEquals(0x05010B05,
                committed.get(0).entries().get(0).operationCode());
        assertEquals(0x05010B02,
                committed.get(1).entries().get(0).operationCode());
        assertEquals(0, buffer.openTransactionCount());
        assertTrue(buffer.lowWatermark().isEmpty());
    }

    @Test
    void removesSavepointRollbackOperationsBeforeCommit() {
        RedoTransactionBuffer buffer = new RedoTransactionBuffer();
        buffer.begin(begin(XID_1, 100), position(100, 10, 512));
        buffer.appendPair(
                undo(XID_1, RedoLogRecord.FB_L), redo(0x0B02));
        buffer.appendPair(
                undo(XID_1, RedoLogRecord.FB_L), redo(0x0B05));

        RedoLogRecord inverse = redo(0x0B05);
        RedoLogRecord rollback = rollback(XID_1, 0);
        assertTrue(buffer.rollbackPair(inverse, rollback));
        CommittedRedoTransaction committed = buffer.commit(
                commit(XID_1, 200, 0)).orElseThrow();

        assertEquals(1, committed.entries().size());
        assertEquals(0x0B02,
                committed.entries().get(0).second().orElseThrow().opCode);
    }

    @Test
    void dropsFullyRolledBackTransactionWithoutOutput() {
        RedoTransactionBuffer buffer = new RedoTransactionBuffer();
        buffer.begin(begin(XID_1, 100), position(100, 10, 512));
        buffer.appendPair(
                undo(XID_1, RedoLogRecord.FB_L), redo(0x0B02));

        assertTrue(buffer.commit(commit(
                XID_1, 200,
                RedoTransactionBuffer.FLG_ROLLBACK_COMMIT)).isEmpty());
        assertEquals(0, buffer.openTransactionCount());
        assertEquals(0, buffer.bufferedEntryCount());
    }

    @Test
    void ignoresTransactionsWhoseBeginningWasNotObserved() {
        RedoTransactionBuffer buffer = new RedoTransactionBuffer();

        assertFalse(buffer.appendPair(
                undo(XID_1, RedoLogRecord.FB_L), redo(0x0B02)));
        assertTrue(buffer.commit(commit(XID_1, 200, 0)).isEmpty());
        assertEquals(0, buffer.openTransactionCount());
    }

    @Test
    void rejectsTransactionSlotReuseBeforeCommit() {
        RedoTransactionBuffer buffer = new RedoTransactionBuffer();
        Xid reusedSlot = Xid.of(
                XID_1.unsignedUndoSegment(), XID_1.slot(), 99);
        buffer.begin(begin(XID_1, 100), position(100, 10, 512));

        RedoLogException error = assertThrows(
                RedoLogException.class,
                () -> buffer.begin(
                        begin(reusedSlot, 101),
                        position(101, 10, 1024)));

        assertEquals(50039, error.getErrorCode());
        assertTrue(error.getMessage().contains("conflicts"));
    }

    @Test
    void rejectsMismatchedRollbackAndBlockAddresses() {
        RedoTransactionBuffer buffer = new RedoTransactionBuffer();
        buffer.begin(begin(XID_1, 100), position(100, 10, 512));
        buffer.appendPair(
                undo(XID_1, RedoLogRecord.FB_L), redo(0x0B02));

        RedoLogRecord wrongInverse = redo(0x0B05);
        RedoLogException rollbackError = assertThrows(
                RedoLogException.class,
                () -> buffer.rollbackPair(
                        wrongInverse, rollback(XID_1, 0)));
        assertEquals(50044, rollbackError.getErrorCode());

        RedoLogRecord wrongBlock = redo(0x0B05);
        wrongBlock.bdba = BLOCK_ADDRESS + 1;
        RedoLogException blockError = assertThrows(
                RedoLogException.class,
                () -> buffer.appendPair(
                        undo(XID_1, RedoLogRecord.FB_L), wrongBlock));
        assertEquals(50045, blockError.getErrorCode());
    }

    @Test
    void preservesAttributesAndGroupsCompleteRows() {
        RedoTransactionBuffer buffer = new RedoTransactionBuffer();
        buffer.begin(begin(XID_1, 100), position(100, 10, 512));
        buffer.attributes(XID_1, 0).orElseThrow()
                .put(Attribute.CLIENT_ID, "capture-test");
        buffer.appendPair(undo(XID_1, 0), redo(0x0B02));
        buffer.appendPair(
                undo(XID_1, RedoLogRecord.FB_L), redo(0x0B02));

        CommittedRedoTransaction committed = buffer.commit(
                commit(XID_1, 200, 0)).orElseThrow();

        assertEquals("capture-test",
                committed.attributes().get(Attribute.CLIENT_ID));
        assertEquals(1, committed.rowGroups().size());
        assertEquals(2, committed.rowGroups().get(0).size());
        assertEquals(committed.entries().get(1).second().orElseThrow(),
                committed.rowGroups().get(0).get(0).redo());
    }

    @Test
    void preservesInsertOrderingAcrossSupplementalVectors() {
        RedoTransactionBuffer buffer = new RedoTransactionBuffer();
        buffer.begin(begin(XID_1, 100), position(100, 10, 512));
        buffer.appendPair(undo(XID_1, 0), redo(0x0B02));
        buffer.appendPair(
                undo(XID_1, RedoLogRecord.FB_L), redo(0x0B10));

        CommittedRedoTransaction committed = buffer.commit(
                commit(XID_1, 200, 0)).orElseThrow();

        assertEquals(List.of(0x0B10, 0x0B02),
                committed.rowGroups().get(0).stream()
                        .map(pair -> pair.redo().opCode)
                        .toList());
    }

    @Test
    void stopsOnIncompleteRowsAndSplitUndo() {
        RedoTransactionBuffer buffer = new RedoTransactionBuffer();
        buffer.begin(begin(XID_1, 100), position(100, 10, 512));
        buffer.appendPair(undo(XID_1, 0), redo(0x0B02));
        CommittedRedoTransaction committed = buffer.commit(
                commit(XID_1, 200, 0)).orElseThrow();

        RedoLogException incomplete = assertThrows(
                RedoLogException.class, committed::rowGroups);
        assertEquals(50057, incomplete.getErrorCode());

        RedoLogRecord split = undo(XID_2, 0);
        split.flg = RedoTransactionBuffer.FLG_MULTIBLOCK_UNDO_HEAD;
        RedoLogException splitError = assertThrows(
                RedoLogException.class,
                () -> buffer.accept(
                        List.of(split), position(300, 11, 512)));
        assertEquals(50041, splitError.getErrorCode());
    }

    private static RedoLogRecord begin(Xid xid, long scn) {
        RedoLogRecord record = record(0x0502, xid, scn);
        record.sequence = Seq.of(10);
        record.timestamp = RedoTime.of(1);
        return record;
    }

    private static RedoLogRecord commit(Xid xid, long scn, int flags) {
        RedoLogRecord record = record(0x0504, xid, scn);
        record.flg = flags;
        record.sequence = Seq.of(10);
        record.timestamp = RedoTime.of(2);
        record.fileOffset = FileOffset.of(4096);
        return record;
    }

    private static RedoLogRecord undo(Xid xid, int supplementalFlags) {
        RedoLogRecord record = record(0x0501, xid, 110);
        record.obj = OBJECT_ID;
        record.dataObj = DATA_OBJECT_ID;
        record.bdba = BLOCK_ADDRESS;
        record.suppLogBdba = BLOCK_ADDRESS;
        record.suppLogSlot = 7;
        record.suppLogFb = supplementalFlags;
        return record;
    }

    private static RedoLogRecord redo(int opCode) {
        RedoLogRecord record = record(opCode, Xid.zero(), 110);
        record.obj = OBJECT_ID;
        record.dataObj = DATA_OBJECT_ID;
        record.bdba = BLOCK_ADDRESS;
        record.slot = 7;
        return record;
    }

    private static RedoLogRecord rollback(Xid xid, int flags) {
        RedoLogRecord record = record(0x0506, Xid.zero(), 120);
        record.usn = xid.unsignedUndoSegment();
        record.slt = xid.slot();
        record.flg = flags;
        return record;
    }

    private static RedoLogRecord record(int opCode, Xid xid, long scn) {
        RedoLogRecord record = new RedoLogRecord();
        record.opCode = opCode;
        record.xid = xid;
        record.scn = Scn.of(scn);
        record.thread = 1;
        record.conId = 0;
        return record;
    }

    private static RedoPosition position(
            long scn, long sequence, long offset) {
        return new RedoPosition(
                Scn.of(scn), 1, Seq.of(sequence), FileOffset.of(offset));
    }
}
