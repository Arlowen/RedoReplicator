/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.parser.RedoOpCodeDispatcher;
import io.github.arlowen.redoreplicator.redo.parser.RedoOpCodeTestSupport;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoMultiRowDecoderTest {
    private static final long OBJECT_ID = 22;
    private static final long DATA_OBJECT_ID = 33;
    private static final long BLOCK_ADDRESS = 0xF100_0001L;

    private final RedoOpCodeDispatcher dispatcher =
            new RedoOpCodeDispatcher(
                    ByteOrder.LITTLE_ENDIAN,
                    RedoLogRecord.REDO_VERSION_19_0);
    private final RedoMultiRowDecoder decoder =
            new RedoMultiRowDecoder(ByteOrder.LITTLE_ENDIAN);

    @Test
    void decodesQuickMultiRowInsertsInSlotOrder() {
        byte[] firstId = {(byte) 0xC1, 13};
        byte[] secondId = {(byte) 0xC1, 25};
        byte[] first = row(firstId,
                "FIRST".getBytes(StandardCharsets.UTF_8));
        byte[] second = row(secondId, null);
        RedoLogRecord redo = insertRedo(
                new int[]{7, 9}, first, second);

        List<DecodedRedoRow> rows = decoder.decode(
                table(), undo(), redo);

        assertEquals(2, rows.size());
        assertEquals(RowId.of(DATA_OBJECT_ID, BLOCK_ADDRESS, 7),
                rows.get(0).rowId());
        assertEquals(RowId.of(DATA_OBJECT_ID, BLOCK_ADDRESS, 9),
                rows.get(1).rowId());
        assertArrayEquals(firstId,
                rows.get(0).after().get("ID").data());
        assertArrayEquals("FIRST".getBytes(StandardCharsets.UTF_8),
                rows.get(0).after().get("NAME").data());
        assertEquals(873,
                rows.get(0).after().get("NAME").charsetId());
        assertArrayEquals(secondId,
                rows.get(1).after().get("ID").data());
        assertEquals(List.of("ID"),
                rows.get(1).after().keySet().stream().toList());
        assertTrue(rows.get(0).before().isEmpty());
    }

    @Test
    void decodesQuickMultiRowDeletesFromTheUndoImage() {
        RedoLogRecord undo = insertRedo(
                new int[]{11, 13},
                row(new byte[]{(byte) 0xC1, 2}, null),
                row(new byte[]{(byte) 0xC1, 3}, null));
        undo.opCode = 0x0501;
        undo.obj = OBJECT_ID;
        undo.fileOffset = FileOffset.of(512);
        RedoLogRecord redo = new RedoLogRecord();
        redo.opCode = 0x0B0C;
        redo.obj = OBJECT_ID;
        redo.dataObj = DATA_OBJECT_ID;
        redo.bdba = BLOCK_ADDRESS;

        List<DecodedRedoRow> rows = decoder.decode(
                table(), undo, redo);

        assertEquals(2, rows.size());
        assertEquals(RedoRowOperation.DELETE,
                rows.get(0).operation());
        assertEquals(RowId.of(DATA_OBJECT_ID, BLOCK_ADDRESS, 11),
                rows.get(0).rowId());
        assertArrayEquals(new byte[]{(byte) 0xC1, 2},
                rows.get(0).before().get("ID").data());
        assertTrue(rows.get(0).after().isEmpty());
    }

    @Test
    void decodesExtendedColumnLengthAndRejectsBrokenRowSizes() {
        byte[] large = new byte[300];
        Arrays.fill(large, (byte) 0x5A);
        RedoLogRecord redo = insertRedo(
                new int[]{5}, row(
                        new byte[]{(byte) 0xC1, 2}, large));

        List<DecodedRedoRow> rows = decoder.decode(
                table(), undo(), redo);

        assertArrayEquals(large,
                rows.get(0).after().get("NAME").data());

        redo.rowSizesDelta = redo.size - 1;
        RedoLogException error = assertThrows(
                RedoLogException.class,
                () -> decoder.decode(table(), undo(), redo));
        assertEquals(50014, error.getErrorCode());
    }

    @Test
    void skips19cRowDependencyScnBeforeColumnLengths() {
        byte[] base = row(
                new byte[]{(byte) 0xC1, 9},
                "DEPENDENT".getBytes(StandardCharsets.UTF_8));
        byte[] dependent = new byte[base.length + 8];
        System.arraycopy(base, 0, dependent, 0, 3);
        System.arraycopy(base, 3, dependent, 11, base.length - 3);
        RedoLogRecord redo = insertRedo(new int[]{17}, dependent);
        redo.op |= RedoLogRecord.OP_ROWDEPENDENCIES;

        List<DecodedRedoRow> rows = decoder.decode(
                table(), undo(), redo);

        assertArrayEquals("DEPENDENT".getBytes(StandardCharsets.UTF_8),
                rows.get(0).after().get("NAME").data());
    }

    @Test
    void matchesTheFixedOpenLogReplicatorBuilderProbe() throws Exception {
        Properties expected = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/multi-row/"
                        + "openlogreplicator-6bc92bc1.properties")) {
            assertNotNull(input);
            expected.load(input);
        }
        RedoLogRecord insertRedo = insertRedo(
                new int[]{7, 9},
                row(new byte[]{(byte) 0xC1, 2}, null),
                row(new byte[]{(byte) 0xC1, 3}, null));
        List<DecodedRedoRow> inserts = decoder.decode(
                table(), undo(), insertRedo);

        RedoLogRecord deleteUndo = insertRedo(
                new int[]{7, 9},
                row(new byte[]{(byte) 0xC1, 2}, null),
                row(new byte[]{(byte) 0xC1, 3}, null));
        deleteUndo.opCode = 0x0501;
        RedoLogRecord deleteRedo = new RedoLogRecord();
        deleteRedo.opCode = 0x0B0C;
        deleteRedo.dataObj = DATA_OBJECT_ID;
        deleteRedo.bdba = BLOCK_ADDRESS;
        List<DecodedRedoRow> deletes = decoder.decode(
                table(), deleteUndo, deleteRedo);

        Properties actual = new Properties();
        actual.setProperty("insert.count",
                Integer.toString(inserts.size()));
        actual.setProperty("delete.count",
                Integer.toString(deletes.size()));
        for (int index = 0; index < inserts.size(); index++) {
            actual.setProperty("insert." + index + ".slot",
                    Integer.toString(inserts.get(index).rowId().slot()));
            actual.setProperty("insert." + index + ".column0",
                    HexFormat.of().formatHex(
                            inserts.get(index).after().get("ID").data()));
            actual.setProperty("delete." + index + ".slot",
                    Integer.toString(deletes.get(index).rowId().slot()));
            actual.setProperty("delete." + index + ".column0",
                    HexFormat.of().formatHex(
                            deletes.get(index).before().get("ID").data()));
        }

        assertEquals(expected, actual);
    }

    private RedoLogRecord insertRedo(int[] slots, byte[]... rows) {
        byte[] rowSizes = new byte[rows.length * 2];
        int totalSize = 0;
        for (int index = 0; index < rows.length; index++) {
            RedoBinaryTestSupport.writeUnsignedShort(
                    rowSizes, index * 2, rows[index].length,
                    ByteOrder.LITTLE_ENDIAN);
            totalSize += rows[index].length;
        }
        byte[] rowData = new byte[totalSize];
        int position = 0;
        for (byte[] row : rows) {
            System.arraycopy(row, 0, rowData, position, row.length);
            position += row.length;
        }
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0B0B, 0, ktbNoOperation(),
                quickMultiRow(slots), rowSizes, rowData);
        dispatcher.dispatch(record);
        record.obj = OBJECT_ID;
        record.dataObj = DATA_OBJECT_ID;
        record.bdba = BLOCK_ADDRESS;
        return record;
    }

    private static byte[] row(byte[] id, byte[] name) {
        int size = 3 + encodedLength(id) + encodedLength(name);
        byte[] row = new byte[size];
        row[2] = 2;
        int position = writeValue(row, 3, id);
        writeValue(row, position, name);
        return row;
    }

    private static int encodedLength(byte[] value) {
        if (value == null) {
            return 1;
        }
        if (value.length <= 0xFD) {
            return 1 + value.length;
        }
        return 3 + value.length;
    }

    private static int writeValue(
            byte[] row, int position, byte[] value) {
        if (value == null) {
            row[position] = (byte) 0xFF;
            return position + 1;
        }
        if (value.length <= 0xFD) {
            row[position] = (byte) value.length;
            position++;
        } else {
            row[position] = (byte) 0xFE;
            RedoBinaryTestSupport.writeUnsignedShort(
                    row, position + 1, value.length,
                    ByteOrder.LITTLE_ENDIAN);
            position += 3;
        }
        System.arraycopy(value, 0, row, position, value.length);
        return position + value.length;
    }

    private static byte[] quickMultiRow(int[] slots) {
        byte[] field = RedoOpCodeTestSupport.field(
                Math.max(24, 22 + slots.length * 2));
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, BLOCK_ADDRESS, ByteOrder.LITTLE_ENDIAN);
        field[10] = RedoLogRecord.OP_QMI;
        field[18] = (byte) slots.length;
        for (int index = 0; index < slots.length; index++) {
            RedoBinaryTestSupport.writeUnsignedShort(
                    field, 20 + index * 2, slots[index],
                    ByteOrder.LITTLE_ENDIAN);
        }
        return field;
    }

    private static byte[] ktbNoOperation() {
        byte[] field = RedoOpCodeTestSupport.field(8);
        field[0] = 0x06;
        return field;
    }

    private static RedoLogRecord undo() {
        RedoLogRecord undo = new RedoLogRecord();
        undo.opCode = 0x0501;
        undo.obj = OBJECT_ID;
        undo.dataObj = DATA_OBJECT_ID;
        undo.fileOffset = FileOffset.of(512);
        return undo;
    }

    private static TableSchema table() {
        return new TableSchema(
                "FREEPDB1", "APP", "USERS",
                OBJECT_ID, DATA_OBJECT_ID, 0, 0, 0,
                List.of(
                        column(1, "ID", OracleColumnType.NUMBER, 1),
                        column(2, "NAME", OracleColumnType.VARCHAR, 0)),
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
                name, type, 400, -1, -1, charsetId,
                primaryKeyMembership, true, false, false,
                false, false, false, false, false, false);
    }
}
