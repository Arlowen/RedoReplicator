/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowOpCodeTest {
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);
    private final RedoByteReader byteReader = new RedoByteReader(ByteOrder.LITTLE_ENDIAN);

    @Test
    void decodesInsertRowPieceAndColumnLayout() {
        byte[] kdo = insertRowPiece(3, 0x02, 9, 0x1234);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B02, 0,
                ktbNoOperation(), kdo,
                new byte[]{0x11},
                RedoOpCodeTestSupport.field(0),
                new byte[]{0x33});

        assertTrue(dispatcher.dispatch(record));

        assertEquals(0xF100_0001L, record.bdba);
        assertEquals(RedoLogRecord.OP_IRP, record.op);
        assertEquals(0x41, record.flags);
        assertEquals(0x0C, record.fb);
        assertEquals(3, record.cc);
        assertEquals(3, record.ccData);
        assertEquals(9, record.sizeDelt);
        assertEquals(0x1234, record.slot);
        assertEquals(3, record.rowData);
        assertFalse(record.compressed);
        assertFalse(RedoOpCodeSupport.isColumnNull(record, 0));
        assertTrue(RedoOpCodeSupport.isColumnNull(record, 1));
        assertFalse(RedoOpCodeSupport.isColumnNull(record, 2));
    }

    @Test
    void detectsCompressedRowImage() {
        byte[] kdo = insertRowPiece(2, 0, 3, 7);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B02, 0, ktbNoOperation(), kdo, new byte[]{1, 2, 3});

        assertTrue(dispatcher.dispatch(record));

        assertTrue(record.compressed);
        assertEquals(3, record.rowData);
    }

    @Test
    void rejectsDataForNullColumn() {
        byte[] kdo = insertRowPiece(2, 0x01, 8, 7);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B02, 0,
                ktbNoOperation(), kdo,
                new byte[]{0x01},
                RedoOpCodeTestSupport.field(0));

        RedoLogException error = assertThrows(
                RedoLogException.class, () -> dispatcher.dispatch(record));
        assertEquals(50061, error.getErrorCode());
    }

    @Test
    void decodesUpdatedColumnNumbersAndValues() {
        byte[] columnNumbers = RedoOpCodeTestSupport.field(6);
        RedoBinaryTestSupport.writeUnsignedShort(
                columnNumbers, 0, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                columnNumbers, 2, 4, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                columnNumbers, 4, 9, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B05, 0,
                ktbNoOperation(), updateRowPiece(3, 0x02, 0, 0x2222),
                columnNumbers,
                new byte[]{0x11},
                RedoOpCodeTestSupport.field(0),
                new byte[]{0x33});

        assertTrue(dispatcher.dispatch(record));

        assertEquals(3, record.cc);
        assertEquals(3, record.ccData);
        assertEquals(0x2222, record.slot);
        assertEquals(4, record.rowData);
        assertTrue(record.colNumsDelta > 0);
        int columnNumbersPosition = record.dataOffset() + record.colNumsDelta;
        assertEquals(1, byteReader.readUnsignedShort(record.data(), columnNumbersPosition));
        assertEquals(4, byteReader.readUnsignedShort(record.data(), columnNumbersPosition + 2));
        assertEquals(9, byteReader.readUnsignedShort(record.data(), columnNumbersPosition + 4));
    }

    @Test
    void decodesKdom2ColumnVectorLayout() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B05, 0,
                ktbNoOperation(), updateRowPiece(2, 0, 0x80, 7),
                new byte[]{0, 0, 1, 0},
                new byte[]{1, 0x55, 1, 0x66});

        assertTrue(dispatcher.dispatch(record));

        assertEquals(4, record.rowData);
        assertTrue(record.colNumsDelta > 0);
    }

    @Test
    void decodesOverwriteRowPieceColumns() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B06, 0,
                ktbNoOperation(), overwriteRowPiece(1, 0, 5, 0x3333),
                new byte[]{0x44});

        assertTrue(dispatcher.dispatch(record));

        assertEquals(RedoLogRecord.OP_ORP, record.op);
        assertEquals(1, record.cc);
        assertEquals(0x3333, record.slot);
        assertEquals(3, record.rowData);
    }

    @Test
    void decodesQuickMultiRowInsertLayout() {
        byte[] kdo = quickMultiRow(RedoLogRecord.OP_QMI, 2, 7, 9);
        byte[] rowSizes = RedoOpCodeTestSupport.field(4);
        RedoBinaryTestSupport.writeUnsignedShort(
                rowSizes, 0, 3, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                rowSizes, 2, 5, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B0B, 0,
                ktbNoOperation(), kdo, rowSizes,
                new byte[]{0x04, 0, 0, 0x08, 0});

        assertTrue(dispatcher.dispatch(record));

        assertEquals(2, record.nRow);
        assertEquals(4, record.rowData);
        int slotsPosition = record.dataOffset() + record.slotsDelta;
        assertEquals(7, byteReader.readUnsignedShort(record.data(), slotsPosition));
        assertEquals(9, byteReader.readUnsignedShort(record.data(), slotsPosition + 2));
        int sizesPosition = record.dataOffset() + record.rowSizesDelta;
        assertEquals(3, byteReader.readUnsignedShort(record.data(), sizesPosition));
        assertEquals(5, byteReader.readUnsignedShort(record.data(), sizesPosition + 2));
    }

    @Test
    void decodesQuickMultiRowDeleteAndSimpleRowOpcodes() {
        RedoLogRecord delete = RedoOpCodeTestSupport.record(
                0x0B0C, 0,
                ktbNoOperation(), quickMultiRow(RedoLogRecord.OP_QMD, 1, 13));
        assertTrue(dispatcher.dispatch(delete));
        assertEquals(1, delete.nRow);

        int[] simpleOpCodes = {0x0B03, 0x0B04, 0x0B08, 0x0B10, 0x0B16};
        for (int opCode : simpleOpCodes) {
            RedoLogRecord record = RedoOpCodeTestSupport.record(
                    opCode, 0, ktbNoOperation(), rowSlotOperation(RedoLogRecord.OP_DRP, 21));
            assertTrue(dispatcher.dispatch(record));
            assertEquals(21, record.slot);
        }
    }

    @Test
    void decodesCfaAndClusterKeySlots() {
        byte[] cfa = kdoBase(32, RedoLogRecord.OP_CFA, 0);
        RedoBinaryTestSupport.writeUnsignedShort(
                cfa, 24, 0x4567, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord cfaRecord = RedoOpCodeTestSupport.record(
                0x0B03, 0, ktbNoOperation(), cfa);
        assertTrue(dispatcher.dispatch(cfaRecord));
        assertEquals(0x4567, cfaRecord.slot);

        byte[] clusterKey = kdoBase(28, RedoLogRecord.OP_CKI, 0);
        clusterKey[27] = 0x45;
        RedoLogRecord clusterKeyRecord = RedoOpCodeTestSupport.record(
                0x0B03, 0, ktbNoOperation(), clusterKey);
        assertTrue(dispatcher.dispatch(clusterKeyRecord));
        assertEquals(0x45, clusterKeyRecord.slot);
    }

    @Test
    void rejectsTruncatedKdoStructures() {
        RedoLogRecord shortCommon = RedoOpCodeTestSupport.record(
                0x0B03, 0,
                ktbNoOperation(), RedoOpCodeTestSupport.field(15));
        RedoLogException commonError = assertThrows(
                RedoLogException.class, () -> dispatcher.dispatch(shortCommon));
        assertEquals(50061, commonError.getErrorCode());

        byte[] shortNullBitmap = insertRowPiece(25, 0, 0, 0);
        RedoLogRecord shortNulls = RedoOpCodeTestSupport.record(
                0x0B02, 0, ktbNoOperation(), shortNullBitmap);
        RedoLogException nullError = assertThrows(
                RedoLogException.class, () -> dispatcher.dispatch(shortNulls));
        assertEquals(50061, nullError.getErrorCode());
    }

    private static byte[] insertRowPiece(int columns, int nulls, int sizeDelta, int slot) {
        byte[] field = kdoBase(48, RedoLogRecord.OP_IRP, 0x41);
        field[16] = 0x0C;
        field[18] = (byte) columns;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 40, sizeDelta, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 42, slot, ByteOrder.LITTLE_ENDIAN);
        field[45] = (byte) nulls;
        return field;
    }

    private static byte[] overwriteRowPiece(int columns, int nulls,
                                            int sizeDelta, int slot) {
        byte[] field = kdoBase(48, RedoLogRecord.OP_ORP, 0);
        field[16] = 0x0C;
        field[18] = (byte) columns;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 40, sizeDelta, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 42, slot, ByteOrder.LITTLE_ENDIAN);
        field[45] = (byte) nulls;
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

    private static byte[] quickMultiRow(int operation, int rows, int... slots) {
        byte[] field = kdoBase(22 + rows * 2, operation, 0);
        if (field.length < 24) {
            field = kdoBase(24, operation, 0);
        }
        field[18] = (byte) rows;
        for (int index = 0; index < slots.length; index++) {
            RedoBinaryTestSupport.writeUnsignedShort(
                    field, 20 + index * 2, slots[index], ByteOrder.LITTLE_ENDIAN);
        }
        return field;
    }

    private static byte[] rowSlotOperation(int operation, int slot) {
        byte[] field = kdoBase(20, operation, 0);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 16, slot, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] kdoBase(int size, int operation, int flags) {
        byte[] field = RedoOpCodeTestSupport.field(size);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, 0xF100_0001L, ByteOrder.LITTLE_ENDIAN);
        field[10] = (byte) operation;
        field[11] = (byte) flags;
        return field;
    }

    private static byte[] ktbNoOperation() {
        byte[] field = RedoOpCodeTestSupport.field(8);
        field[0] = 0x06;
        return field;
    }
}
