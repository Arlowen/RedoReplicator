/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoRecordPair;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.redo.parser.RedoOpCodeDispatcher;
import io.github.arlowen.redoreplicator.redo.parser.RedoOpCodeTestSupport;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoRowDecoderTest {
    private static final long OBJECT_ID = 22;
    private static final long DATA_OBJECT_ID = 33;
    private static final long BLOCK_ADDRESS = 0xF100_0001L;
    private static final int SLOT = 7;
    private static final Xid XID = Xid.of(1, 2, 3);

    private final RedoOpCodeDispatcher dispatcher =
            new RedoOpCodeDispatcher(
                    ByteOrder.LITTLE_ENDIAN,
                    RedoLogRecord.REDO_VERSION_19_0);
    private final RedoRowDecoder decoder =
            new RedoRowDecoder(ByteOrder.LITTLE_ENDIAN);

    @Test
    void decodesInsertValuesAndRemovesNonKeyNulls() {
        byte[] id = number(12);
        byte[] name = "APP".getBytes(StandardCharsets.UTF_8);
        RedoLogRecord redo = insertRedo(
                RedoLogRecord.FB_F, 0x04, id, name, new byte[0]);

        DecodedRedoRow row = decoder.decode(table(), List.of(
                new RedoRecordPair(undo(0), redo)));

        assertEquals(RedoRowOperation.INSERT, row.operation());
        assertEquals(RowId.of(DATA_OBJECT_ID, BLOCK_ADDRESS, SLOT),
                row.rowId());
        assertTrue(row.before().isEmpty());
        assertEquals(List.of("ID", "NAME"),
                row.after().keySet().stream().toList());
        assertArrayEquals(id, row.after().get("ID").data());
        assertArrayEquals(name, row.after().get("NAME").data());
        assertEquals(873, row.after().get("NAME").charsetId());
    }

    @Test
    void decodesUpdateColumnNumbersAndCompletesMissingImage() {
        RedoLogRecord undo = undo(2);
        undo.suppLogBdba = BLOCK_ADDRESS;
        undo.suppLogSlot = SLOT;
        byte[] name = "APP_NEW".getBytes(StandardCharsets.UTF_8);

        DecodedRedoRow row = decoder.decode(table(), List.of(
                new RedoRecordPair(undo, updateRedo(
                        RedoLogRecord.FB_F, 2, name))));

        assertEquals(RedoRowOperation.UPDATE, row.operation());
        assertEquals(List.of("NAME"),
                row.before().keySet().stream().toList());
        assertTrue(row.before().get("NAME").nullValue());
        assertEquals(873, row.before().get("NAME").charsetId());
        assertArrayEquals(name, row.after().get("NAME").data());
    }

    @Test
    void usesSupplementalBeforeImageForUpdate() {
        byte[] before = "APP_OLD".getBytes(StandardCharsets.UTF_8);
        byte[] after = "APP_NEW".getBytes(StandardCharsets.UTF_8);

        DecodedRedoRow row = decoder.decode(table(), List.of(
                new RedoRecordPair(
                        supplementalUndo(before),
                        updateRedo(RedoLogRecord.FB_F, 2, after))));

        assertArrayEquals(before, row.before().get("NAME").data());
        assertArrayEquals(after, row.after().get("NAME").data());
    }

    @Test
    void mergesUpdateRowFragments() {
        RedoLogRecord firstUndo = undo(2);
        firstUndo.suppLogBdba = BLOCK_ADDRESS;
        firstUndo.suppLogSlot = SLOT;
        RedoLogRecord lastUndo = undo(2);
        lastUndo.suppLogBdba = BLOCK_ADDRESS;
        lastUndo.suppLogSlot = SLOT;

        DecodedRedoRow row = decoder.decode(table(), List.of(
                new RedoRecordPair(firstUndo, updateRedo(
                        RedoLogRecord.FB_N, 2,
                        "APP_".getBytes(StandardCharsets.UTF_8))),
                new RedoRecordPair(lastUndo, updateRedo(
                        RedoLogRecord.FB_P, 2,
                        "NEW".getBytes(StandardCharsets.UTF_8)))));

        assertArrayEquals("APP_NEW".getBytes(StandardCharsets.UTF_8),
                row.after().get("NAME").data());
    }

    @Test
    void decodesDeleteAndRetainsPrimaryKeyPlaceholder() {
        RedoLogRecord undo = undo(0);
        undo.suppLogBdba = BLOCK_ADDRESS;
        undo.suppLogSlot = SLOT;

        DecodedRedoRow row = decoder.decode(table(), List.of(
                new RedoRecordPair(undo, deleteRedo())));

        assertEquals(RedoRowOperation.DELETE, row.operation());
        assertEquals(List.of("ID"),
                row.before().keySet().stream().toList());
        assertTrue(row.before().get("ID").nullValue());
        assertTrue(row.after().isEmpty());
    }

    @Test
    void emitsCompressedRowsAsRawPayloadAndRejectsIncompleteRows() {
        DecodedRedoRow compressed = decoder.decode(table(), List.of(
                new RedoRecordPair(undo(0), compressedInsertRedo())));
        assertEquals(List.of("COMPRESSED"),
                compressed.after().keySet().stream().toList());
        assertArrayEquals(new byte[]{1, 2, 3},
                compressed.after().get("COMPRESSED").data());
        assertEquals(OracleColumnType.RAW,
                compressed.after().get("COMPRESSED").type());

        RedoLogRecord undo = undo(2);
        undo.suppLogBdba = BLOCK_ADDRESS;
        undo.suppLogSlot = SLOT;
        RedoLogException incomplete = assertThrows(
                RedoLogException.class,
                () -> decoder.decode(table(), List.of(
                        new RedoRecordPair(undo, updateRedo(
                                RedoLogRecord.FB_N, 2,
                                "APP_".getBytes(
                                        StandardCharsets.UTF_8))))));
        assertEquals(50014, incomplete.getErrorCode());
    }

    @Test
    void matchesTheFixedOpenLogReplicatorCompressedOutput() throws Exception {
        Properties expected = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/compressed-row/"
                        + "openlogreplicator-6bc92bc1.properties")) {
            assertNotNull(input);
            expected.load(input);
        }

        DecodedRedoRow row = decoder.decode(table(), List.of(
                new RedoRecordPair(undo(0), compressedInsertRedo())));
        Properties actual = new Properties();
        actual.setProperty("column.name",
                row.after().keySet().iterator().next());
        actual.setProperty("column.hex", HexFormat.of().formatHex(
                row.after().get("COMPRESSED").data()));

        assertEquals(expected, actual);
    }

    private RedoLogRecord insertRedo(
            int fb, int nulls, byte[]... values) {
        byte[][] fields = new byte[values.length + 2][];
        fields[0] = ktbNoOperation();
        fields[1] = insertRowPiece(values.length, fb, nulls);
        System.arraycopy(values, 0, fields, 2, values.length);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B02, 0, fields);
        dispatcher.dispatch(record);
        attachIdentity(record);
        return record;
    }

    private RedoLogRecord updateRedo(
            int fb, int columnNumber, byte[] value) {
        byte[] columnNumbers = new byte[2];
        RedoBinaryTestSupport.writeUnsignedShort(
                columnNumbers, 0, columnNumber,
                ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B05, 0,
                ktbNoOperation(), updateRowPiece(fb),
                columnNumbers, value);
        dispatcher.dispatch(record);
        attachIdentity(record);
        return record;
    }

    private RedoLogRecord deleteRedo() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B03, 0, ktbNoOperation(), deleteRowPiece());
        dispatcher.dispatch(record);
        attachIdentity(record);
        return record;
    }

    private RedoLogRecord compressedInsertRedo() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B02, 0,
                ktbNoOperation(),
                insertRowPiece(2, RedoLogRecord.FB_F, 0),
                new byte[]{1, 2, 3});
        dispatcher.dispatch(record);
        attachIdentity(record);
        record.compressed = true;
        return record;
    }

    private RedoLogRecord supplementalUndo(byte[] value) {
        byte[] columnNumbers = new byte[2];
        byte[] columnLengths = new byte[2];
        RedoBinaryTestSupport.writeUnsignedShort(
                columnNumbers, 0, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                columnLengths, 0, value.length,
                ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0501, 0,
                undoBlock(), ktuBlock(), ktbNoOperation(),
                deleteRowPiece(), supplementalHeader(),
                columnNumbers, columnLengths, value);
        dispatcher.dispatch(record);
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

    private static TableSchema table() {
        return new TableSchema(
                "FREEPDB1", "APP", "USERS",
                OBJECT_ID, DATA_OBJECT_ID, 0, 0, 0,
                List.of(
                        column(1, "ID", OracleColumnType.NUMBER, 1),
                        column(2, "NAME", OracleColumnType.VARCHAR, 0),
                        column(3, "NOTE", OracleColumnType.VARCHAR, 0)),
                List.of(), List.of());
    }

    private static ColumnSchema column(
            int segmentColumn,
            String name,
            OracleColumnType type,
            int primaryKeyMembership) {
        long charsetId = 0;
        if (type == OracleColumnType.VARCHAR) {
            charsetId = 873;
        }
        return new ColumnSchema(
                segmentColumn, -1, segmentColumn, segmentColumn,
                name, type, 128, -1, -1, charsetId,
                primaryKeyMembership, true, false, false,
                false, false, false, false, false, false);
    }

    private static byte[] insertRowPiece(
            int columns, int fb, int nulls) {
        byte[] field = kdoBase(48, RedoLogRecord.OP_IRP);
        field[16] = (byte) fb;
        field[18] = (byte) columns;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 40, 100, ByteOrder.LITTLE_ENDIAN);
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
