/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionOpCodeTest {
    private static final int FLG_ROLLBACK = 0x0004;
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);

    @Test
    void decodesBeginTransactionAndPdbIdentity() {
        byte[] transaction = RedoOpCodeTestSupport.field(32);
        RedoBinaryTestSupport.writeUnsignedShort(
                transaction, 0, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                transaction, 4, 0x89AB_CDEFL, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                transaction, 16, 0x0108, ByteOrder.LITTLE_ENDIAN);
        byte[] extentMap = RedoOpCodeTestSupport.field(36);
        byte[] pdb = RedoOpCodeTestSupport.field(4);
        RedoBinaryTestSupport.writeUnsignedInt(
                pdb, 0, 0xF000_0001L, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0502, 7, transaction, extentMap, pdb);

        assertTrue(dispatcher.dispatch(record));

        assertEquals(Xid.of(7, 0x1234, 0x89AB_CDEFL), record.xid);
        assertEquals(0x0108, record.flg);
        assertEquals(0xF000_0001L, record.dbId);
    }

    @Test
    void decodesCommitAndRollbackFlag() {
        byte[] commit = RedoOpCodeTestSupport.field(20);
        RedoBinaryTestSupport.writeUnsignedShort(
                commit, 0, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                commit, 4, 0x1020_3040L, ByteOrder.LITTLE_ENDIAN);
        commit[16] = 0x06;
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0504, 8, commit, RedoOpCodeTestSupport.field(16));

        assertTrue(dispatcher.dispatch(record));

        assertEquals(Xid.of(8, 0x5678, 0x1020_3040L), record.xid);
        assertEquals(0x06, record.flg);
        assertTrue((record.flg & FLG_ROLLBACK) != 0);
    }

    @Test
    void decodesPartialAndBlockUndoRecords() {
        RedoLogRecord partial = RedoOpCodeTestSupport.record(
                0x0506, 0, ktuBlock(), RedoOpCodeTestSupport.field(8));
        RedoLogRecord undo = RedoOpCodeTestSupport.record(
                0x050B, 0, ktuBlock());

        assertTrue(dispatcher.dispatch(partial));
        assertTrue(dispatcher.dispatch(undo));

        assertKtuBlock(partial);
        assertKtuBlock(undo);
    }

    @Test
    void rejectsShortFieldsAndReportsUntranslatedOpcode() {
        RedoLogRecord shortBegin = RedoOpCodeTestSupport.record(
                0x0502, 7, RedoOpCodeTestSupport.field(31));
        RedoLogException error = assertThrows(
                RedoLogException.class, () -> dispatcher.dispatch(shortBegin));
        assertEquals(50061, error.getErrorCode());

        RedoLogRecord untranslated = RedoOpCodeTestSupport.record(
                0x1301, 0, RedoOpCodeTestSupport.field(16));
        assertFalse(dispatcher.dispatch(untranslated));
    }

    private static byte[] ktuBlock() {
        byte[] field = RedoOpCodeTestSupport.field(24);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, 0xF100_0001L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, 0xE200_0002L, ByteOrder.LITTLE_ENDIAN);
        field[16] = 0x0B;
        field[17] = 0x01;
        field[18] = 0x22;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 20, 0x010C, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static void assertKtuBlock(RedoLogRecord record) {
        assertEquals(0xF100_0001L, record.obj);
        assertEquals(0xE200_0002L, record.dataObj);
        assertEquals(0x0B01, record.opc);
        assertEquals(0x22, record.slt);
        assertEquals(0x010C, record.flg);
    }
}
