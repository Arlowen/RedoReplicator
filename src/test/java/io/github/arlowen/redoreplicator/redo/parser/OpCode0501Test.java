/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpCode0501Test {
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);
    private final RedoByteReader byteReader = new RedoByteReader(ByteOrder.LITTLE_ENDIAN);

    @Test
    void decodesUpdateUndoAndSupplementalColumns() {
        byte[] columnNumbers = unsignedShorts(1, 4);
        byte[] supplementalNumbers = unsignedShorts(1, 4);
        byte[] supplementalLengths = unsignedShorts(1, 1);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0501, 0,
                undoBlock(), ktuBlock(0x0B01, 0), ktbNoOperation(),
                updateRowPiece(2, 0, 0, 0x2222),
                columnNumbers,
                new byte[]{0x11}, new byte[]{0x22}, empty(),
                supplementalHeader(26), supplementalNumbers, supplementalLengths,
                new byte[]{0x31}, new byte[]{0x32});

        assertTrue(dispatcher.dispatch(record));

        assertEquals(Xid.of(0x1234, 0x5678, 0x9ABC_DEF0L), record.xid);
        assertEquals(0xF100_0001L, record.obj);
        assertEquals(0xE200_0002L, record.dataObj);
        assertEquals(0x0B01, record.opc);
        assertEquals(RedoLogRecord.OP_URP, record.op & 0x1F);
        assertEquals(6, record.rowData);
        assertArrayEquals(columnNumbers,
                bytes(record, record.colNumsDelta, columnNumbers.length));
        assertEquals(0x44, record.suppLogFb);
        assertEquals(2, record.suppLogCC);
        assertEquals(5, record.suppLogBefore);
        assertEquals(7, record.suppLogAfter);
        assertEquals(0xD300_0003L, record.suppLogBdba);
        assertEquals(0x3344, record.suppLogSlot);
        assertArrayEquals(supplementalNumbers,
                bytes(record, record.suppLogNumsDelta, supplementalNumbers.length));
        assertArrayEquals(supplementalLengths,
                bytes(record, record.suppLogLenDelta, supplementalLengths.length));
        assertEquals(12, record.suppLogRowData);
    }

    @Test
    void fallsBackToRowIdentityForShortSupplementalHeader() {
        byte[] row = rowSlotOperation(RedoLogRecord.OP_DRP, 0x4567);
        byte[] supplemental = supplementalHeader(20);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0501, 0,
                undoBlock(), ktuBlock(0x0B01, 0), ktbNoOperation(), row, supplemental);

        assertTrue(dispatcher.dispatch(record));

        assertEquals(0xC100_0001L, record.bdba);
        assertEquals(0x4567, record.slot);
        assertEquals(record.bdba, record.suppLogBdba);
        assertEquals(record.slot, record.suppLogSlot);
    }

    @Test
    void decodesIndexUndoKeyLocations() {
        byte[] key = new byte[]{1, 2, 3};
        byte[] keyData = new byte[]{4, 5};
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0501, 0,
                undoBlock(), ktuBlock(0x0A16, 0), ktbNoOperation(),
                RedoOpCodeTestSupport.field(20), key, keyData,
                RedoOpCodeTestSupport.field(1), RedoOpCodeTestSupport.field(1));

        assertTrue(dispatcher.dispatch(record));

        assertEquals(key.length, record.indKeySize);
        assertEquals(keyData.length, record.indKeyDataSize);
        assertArrayEquals(key, bytes(record, record.indKey, record.indKeySize));
        assertArrayEquals(keyData,
                bytes(record, record.indKeyData, record.indKeyDataSize));
    }

    @Test
    void decodesKdliUndoIdentity() {
        byte[] common = RedoOpCodeTestSupport.field(12);
        common[0] = 1;
        RedoBinaryTestSupport.writeUnsignedInt(
                common, 8, 0xB100_0001L, ByteOrder.LITTLE_ENDIAN);
        byte[] info = RedoOpCodeTestSupport.field(17);
        info[0] = 1;
        byte[] lobId = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        System.arraycopy(lobId, 0, info, 1, lobId.length);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0501, 0,
                undoBlock(), ktuBlock(0x1A01, 0), ktbNoOperation(), common, info);

        assertTrue(dispatcher.dispatch(record));

        assertEquals(1, record.opc);
        assertEquals(0xB100_0001L, record.dba);
        assertEquals(LobId.of(lobId), record.lobId);
    }

    @Test
    void decodesQuickMultiRowUndoLayout() {
        byte[] rowSizes = unsignedShorts(3, 5);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0501, 0,
                undoBlock(), ktuBlock(0x0B01, 0), ktbNoOperation(),
                quickMultiRow(2, 7, 9), rowSizes, new byte[]{1, 2, 3, 4, 5});

        assertTrue(dispatcher.dispatch(record));

        assertEquals(2, record.nRow);
        assertArrayEquals(rowSizes,
                bytes(record, record.rowSizesDelta, rowSizes.length));
        assertEquals(6, record.rowData);
    }

    @Test
    void stopsAtMultiBlockUndoHeader() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0501, 0, undoBlock(), ktuBlock(0x0B01, 0x0100));

        assertTrue(dispatcher.dispatch(record));

        assertEquals(0x0100, record.flg);
        assertEquals(0x0B01, record.opc);
        assertEquals(0, record.op);
    }

    @Test
    void rejectsTruncatedUndoAndSupplementalHeaders() {
        RedoLogRecord shortUndo = RedoOpCodeTestSupport.record(
                0x0501, 0, RedoOpCodeTestSupport.field(19));
        RedoLogException undoError = assertThrows(
                RedoLogException.class, () -> dispatcher.dispatch(shortUndo));
        assertEquals(50061, undoError.getErrorCode());

        RedoLogRecord shortSupplemental = RedoOpCodeTestSupport.record(
                0x0501, 0,
                undoBlock(), ktuBlock(0x0B01, 0), ktbNoOperation(),
                rowSlotOperation(RedoLogRecord.OP_DRP, 1),
                RedoOpCodeTestSupport.field(19));
        RedoLogException supplementalError = assertThrows(
                RedoLogException.class, () -> dispatcher.dispatch(shortSupplemental));
        assertEquals(50061, supplementalError.getErrorCode());
    }

    private static byte[] undoBlock() {
        byte[] field = RedoOpCodeTestSupport.field(20);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 8, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 10, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 12, 0x9ABC_DEF0L, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] ktuBlock(int opCode, int flags) {
        byte[] field = RedoOpCodeTestSupport.field(24);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, 0xF100_0001L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, 0xE200_0002L, ByteOrder.LITTLE_ENDIAN);
        field[16] = (byte) (opCode >> 8);
        field[17] = (byte) opCode;
        field[18] = 0x22;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 20, flags, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] ktbNoOperation() {
        byte[] field = RedoOpCodeTestSupport.field(8);
        field[0] = 0x06;
        return field;
    }

    private static byte[] updateRowPiece(int columns, int nulls, int flags, int slot) {
        byte[] field = kdoBase(28, RedoLogRecord.OP_URP, flags);
        field[16] = 0x0C;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 20, slot, ByteOrder.LITTLE_ENDIAN);
        field[23] = (byte) columns;
        field[26] = (byte) nulls;
        return field;
    }

    private static byte[] rowSlotOperation(int operation, int slot) {
        byte[] field = kdoBase(20, operation, 0);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 16, slot, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] quickMultiRow(int rows, int... slots) {
        byte[] field = kdoBase(22 + rows * 2, RedoLogRecord.OP_QMI, 0);
        if (field.length < 24) {
            field = kdoBase(24, RedoLogRecord.OP_QMI, 0);
        }
        field[18] = (byte) rows;
        for (int index = 0; index < slots.length; index++) {
            RedoBinaryTestSupport.writeUnsignedShort(
                    field, 20 + index * 2, slots[index], ByteOrder.LITTLE_ENDIAN);
        }
        return field;
    }

    private static byte[] kdoBase(int size, int operation, int flags) {
        byte[] field = RedoOpCodeTestSupport.field(size);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, 0xC100_0001L, ByteOrder.LITTLE_ENDIAN);
        field[10] = (byte) operation;
        field[11] = (byte) flags;
        return field;
    }

    private static byte[] supplementalHeader(int size) {
        byte[] field = RedoOpCodeTestSupport.field(size);
        field[0] = 1;
        field[1] = 0x44;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 2, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 6, 5, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 8, 7, ByteOrder.LITTLE_ENDIAN);
        if (size >= 26) {
            RedoBinaryTestSupport.writeUnsignedInt(
                    field, 20, 0xD300_0003L, ByteOrder.LITTLE_ENDIAN);
            RedoBinaryTestSupport.writeUnsignedShort(
                    field, 24, 0x3344, ByteOrder.LITTLE_ENDIAN);
        }
        return field;
    }

    private static byte[] unsignedShorts(int... values) {
        byte[] field = RedoOpCodeTestSupport.field(values.length * 2);
        for (int index = 0; index < values.length; index++) {
            RedoBinaryTestSupport.writeUnsignedShort(
                    field, index * 2, values[index], ByteOrder.LITTLE_ENDIAN);
        }
        return field;
    }

    private static byte[] empty() {
        return RedoOpCodeTestSupport.field(0);
    }

    private static byte[] bytes(RedoLogRecord record, int position, int size) {
        int start = record.dataOffset() + position;
        return Arrays.copyOfRange(record.data(), start, start + size);
    }
}
