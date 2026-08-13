/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoRecordPair;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.redo.parser.RedoOpCodeDispatcher;
import io.github.arlowen.redoreplicator.redo.parser.RedoOpCodeTestSupport;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemDictionaryRedoBridgeTest {
    private static final long OBJECT_ID = 22;
    private static final long DATA_OBJECT_ID = 33;
    private static final long BLOCK_ADDRESS = 0xF100_0001L;
    private static final int SLOT = 7;
    private static final Xid XID = Xid.of(1, 2, 3);

    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);
    private final SystemDictionaryRedoBridge bridge =
            new SystemDictionaryRedoBridge(ByteOrder.LITTLE_ENDIAN);

    @Test
    void decodesParsedInsertAndAppliesItToSystemTransaction() throws Exception {
        byte[] userId = number(12);
        byte[] name = "APP".getBytes(StandardCharsets.UTF_8);
        RedoLogRecord redo = insertRedo(
                RedoLogRecord.FB_F, 0x04, userId, name, new byte[0]);
        SystemDictionaryRedoChange decoded = bridge.decode(
                SystemDictionaryTable.USER,
                userTable(),
                List.of(new RedoRecordPair(undo(0), redo)));

        assertEquals(XID, decoded.xid());
        assertEquals(SystemDictionaryOperation.INSERT,
                decoded.change().operation());
        assertEquals(RowId.of(DATA_OBJECT_ID, BLOCK_ADDRESS, SLOT),
                decoded.change().rowId());
        assertArrayEquals(userId,
                decoded.change().values().get("USER#").data());
        assertArrayEquals(name,
                decoded.change().values().get("NAME").data());
        assertTrue(decoded.change().values().get("SPARE1").nullValue());

        SystemTransactionManager manager = new SystemTransactionManager(
                SystemDictionaryState.empty(), "FREEPDB1", 873, 2000,
                StandardCharsets.UTF_8, new TableSchemaJsonCodec());
        manager.apply(decoded.xid(), decoded.change());

        SysUser inserted = manager.commit(
                XID, io.github.arlowen.redoreplicator.redo.common.Scn.of(500))
                .dictionaryState().users().get(0);
        assertEquals(12, inserted.userId());
        assertEquals("APP", inserted.name());
    }

    @Test
    void decodesUpdateColumnNumbersAndSupplementalRowIdentity() {
        RedoLogRecord redo = updateRedo(
                RedoLogRecord.FB_F,
                2,
                "APP_NEW".getBytes(StandardCharsets.UTF_8));
        RedoLogRecord undo = undo(2);
        undo.suppLogBdba = BLOCK_ADDRESS;
        undo.suppLogSlot = SLOT;

        SystemDictionaryRedoChange decoded = bridge.decode(
                SystemDictionaryTable.USER,
                userTable(),
                List.of(new RedoRecordPair(undo, redo)));

        assertEquals(SystemDictionaryOperation.UPDATE,
                decoded.change().operation());
        assertEquals(RowId.of(DATA_OBJECT_ID, BLOCK_ADDRESS, SLOT),
                decoded.change().rowId());
        assertEquals(List.of("NAME"),
                decoded.change().values().keySet().stream().toList());
        assertArrayEquals("APP_NEW".getBytes(StandardCharsets.UTF_8),
                decoded.change().values().get("NAME").data());
    }

    @Test
    void mergesFirstAndLastColumnFragments() {
        RedoLogRecord first = updateRedo(
                RedoLogRecord.FB_N, 2,
                "APP_".getBytes(StandardCharsets.UTF_8));
        RedoLogRecord last = updateRedo(
                RedoLogRecord.FB_P, 2,
                "NEW".getBytes(StandardCharsets.UTF_8));
        RedoLogRecord firstUndo = undo(2);
        firstUndo.suppLogBdba = BLOCK_ADDRESS;
        firstUndo.suppLogSlot = SLOT;
        RedoLogRecord lastUndo = undo(2);
        lastUndo.suppLogBdba = BLOCK_ADDRESS;
        lastUndo.suppLogSlot = SLOT;

        SystemDictionaryRedoChange decoded = bridge.decode(
                SystemDictionaryTable.USER,
                userTable(),
                List.of(new RedoRecordPair(firstUndo, first),
                        new RedoRecordPair(lastUndo, last)));

        assertArrayEquals("APP_NEW".getBytes(StandardCharsets.UTF_8),
                decoded.change().values().get("NAME").data());
    }

    @Test
    void decodesDeleteWithoutAfterValues() {
        RedoLogRecord redo = deleteRedo();
        RedoLogRecord undo = undo(0);
        undo.suppLogBdba = BLOCK_ADDRESS;
        undo.suppLogSlot = SLOT;

        SystemDictionaryRedoChange decoded = bridge.decode(
                SystemDictionaryTable.USER,
                userTable(),
                List.of(new RedoRecordPair(undo, redo)));

        assertEquals(SystemDictionaryOperation.DELETE,
                decoded.change().operation());
        assertEquals(RowId.of(DATA_OBJECT_ID, BLOCK_ADDRESS, SLOT),
                decoded.change().rowId());
        assertTrue(decoded.change().values().isEmpty());
    }

    @Test
    void usesParsedSupplementalAfterImageWhenRowRedoHasNoValues() {
        byte[] name = "APP_SUP".getBytes(StandardCharsets.UTF_8);
        RedoLogRecord undo = supplementalUndo(name);
        RedoLogRecord redo = supplementalRedo();

        SystemDictionaryRedoChange decoded = bridge.decode(
                SystemDictionaryTable.USER,
                userTable(),
                List.of(new RedoRecordPair(undo, redo)));

        assertEquals(SystemDictionaryOperation.UPDATE,
                decoded.change().operation());
        assertEquals(List.of("NAME"),
                decoded.change().values().keySet().stream().toList());
        assertArrayEquals(name,
                decoded.change().values().get("NAME").data());
    }

    @Test
    void rejectsCompressedDictionaryRows() {
        RedoLogRecord redo = compressedInsertRedo();
        assertTrue(redo.compressed);

        RedoLogException error = assertThrows(
                RedoLogException.class,
                () -> bridge.decode(
                        SystemDictionaryTable.USER,
                        userTable(),
                        List.of(new RedoRecordPair(undo(0), redo))));

        assertEquals(50014, error.getErrorCode());
        assertTrue(error.getMessage().contains("Compressed"));
    }

    @Test
    void rejectsIncompleteColumnFragments() {
        RedoLogRecord redo = updateRedo(
                RedoLogRecord.FB_N, 2,
                "APP_".getBytes(StandardCharsets.UTF_8));
        RedoLogRecord undo = undo(2);
        undo.suppLogBdba = BLOCK_ADDRESS;
        undo.suppLogSlot = SLOT;

        RedoLogException error = assertThrows(
                RedoLogException.class,
                () -> bridge.decode(
                        SystemDictionaryTable.USER,
                        userTable(),
                        List.of(new RedoRecordPair(undo, redo))));

        assertEquals(50014, error.getErrorCode());
        assertTrue(error.getMessage().contains("incomplete"));
    }

    private RedoLogRecord insertRedo(int fb, int nulls, byte[]... values) {
        int sizeDelta = 100;
        if (values.length == 1) {
            sizeDelta = values[0].length;
        }
        byte[][] fields = new byte[values.length + 2][];
        fields[0] = ktbNoOperation();
        fields[1] = insertRowPiece(values.length, fb, nulls, sizeDelta);
        System.arraycopy(values, 0, fields, 2, values.length);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B02, 0, fields);
        dispatcher.dispatch(record);
        attachIdentity(record);
        return record;
    }

    private RedoLogRecord updateRedo(int fb, int columnNumber, byte[] value) {
        byte[] columnNumbers = new byte[2];
        RedoBinaryTestSupport.writeUnsignedShort(
                columnNumbers, 0, columnNumber, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B05, 0,
                ktbNoOperation(),
                updateRowPiece(fb),
                columnNumbers,
                value);
        dispatcher.dispatch(record);
        attachIdentity(record);
        return record;
    }

    private RedoLogRecord compressedInsertRedo() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B02, 0,
                ktbNoOperation(),
                insertRowPiece(2, RedoLogRecord.FB_F, 0, 3),
                new byte[]{1, 2, 3});
        dispatcher.dispatch(record);
        attachIdentity(record);
        return record;
    }

    private RedoLogRecord supplementalUndo(byte[] value) {
        byte[] columnNumbers = new byte[2];
        byte[] columnLengths = new byte[2];
        RedoBinaryTestSupport.writeUnsignedShort(
                columnNumbers, 0, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                columnLengths, 0, value.length, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0501, 0,
                undoBlock(),
                ktuBlock(),
                ktbNoOperation(),
                deleteRowPiece(),
                supplementalHeader(),
                columnNumbers,
                columnLengths,
                value);
        dispatcher.dispatch(record);
        return record;
    }

    private RedoLogRecord supplementalRedo() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B10, 0,
                ktbNoOperation(),
                deleteRowPiece());
        dispatcher.dispatch(record);
        attachIdentity(record);
        return record;
    }

    private RedoLogRecord deleteRedo() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B03, 0,
                ktbNoOperation(),
                deleteRowPiece());
        dispatcher.dispatch(record);
        attachIdentity(record);
        return record;
    }

    private static RedoLogRecord undo(int suppLogAfter) {
        RedoLogRecord undo = new RedoLogRecord();
        undo.opCode = 0x0501;
        undo.xid = XID;
        undo.obj = OBJECT_ID;
        undo.dataObj = DATA_OBJECT_ID;
        undo.suppLogAfter = suppLogAfter;
        return undo;
    }

    private static void attachIdentity(RedoLogRecord record) {
        record.obj = OBJECT_ID;
        record.dataObj = DATA_OBJECT_ID;
    }

    private static TableSchema userTable() {
        return new TableSchema(
                "FREEPDB1", "SYS", "USER$",
                OBJECT_ID, DATA_OBJECT_ID, 0, 0, 0,
                List.of(
                        column(1, "USER#", OracleColumnType.NUMBER),
                        column(2, "NAME", OracleColumnType.VARCHAR),
                        column(3, "SPARE1", OracleColumnType.NUMBER)),
                List.of(), List.of());
    }

    private static ColumnSchema column(
            int segmentColumn, String name, OracleColumnType type) {
        return new ColumnSchema(
                segmentColumn, -1, segmentColumn, segmentColumn,
                name, type, 128, -1, -1, 0, 0,
                true, false, false, false, false,
                false, false, false, false);
    }

    private static byte[] insertRowPiece(
            int columns, int fb, int nulls, int sizeDelta) {
        byte[] field = kdoBase(48, RedoLogRecord.OP_IRP);
        field[16] = (byte) fb;
        field[18] = (byte) columns;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 40, sizeDelta, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 42, SLOT, ByteOrder.LITTLE_ENDIAN);
        field[45] = (byte) nulls;
        return field;
    }

    private static byte[] updateRowPiece(int fb) {
        byte[] field = kdoBase(28, RedoLogRecord.OP_URP);
        field[16] = (byte) fb;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 20, SLOT, ByteOrder.LITTLE_ENDIAN);
        field[23] = 1;
        return field;
    }

    private static byte[] deleteRowPiece() {
        byte[] field = kdoBase(20, RedoLogRecord.OP_DRP);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 16, SLOT, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] kdoBase(int size, int operation) {
        byte[] field = new byte[size];
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, BLOCK_ADDRESS, ByteOrder.LITTLE_ENDIAN);
        field[10] = (byte) operation;
        return field;
    }

    private static byte[] ktbNoOperation() {
        byte[] field = new byte[8];
        field[0] = 0x06;
        return field;
    }

    private static byte[] undoBlock() {
        byte[] field = new byte[20];
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 8, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 10, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 12, 3, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] ktuBlock() {
        byte[] field = new byte[24];
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, OBJECT_ID, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, DATA_OBJECT_ID, ByteOrder.LITTLE_ENDIAN);
        field[16] = 0x0B;
        field[17] = 0x01;
        return field;
    }

    private static byte[] supplementalHeader() {
        byte[] field = new byte[26];
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 2, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 8, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 20, BLOCK_ADDRESS, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 24, SLOT, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] number(int value) {
        return new byte[]{(byte) 0xC1, (byte) (value + 1)};
    }
}
