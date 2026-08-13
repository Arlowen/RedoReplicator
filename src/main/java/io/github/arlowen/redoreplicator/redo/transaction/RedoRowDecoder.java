/*
 * Java translation derived from OpenLogReplicator Builder::processDml in
 * src/builder/Builder.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoFieldCursor;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoRecordPair;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.TableSchema;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RedoRowDecoder {
    private final RedoByteReader byteReader;

    public RedoRowDecoder(ByteOrder byteOrder) {
        byteReader = new RedoByteReader(byteOrder);
    }

    public DecodedRedoRow decode(
            TableSchema table, List<RedoRecordPair> recordPairs) {
        Objects.requireNonNull(table, "table");
        if (recordPairs.isEmpty()) {
            throw new IllegalArgumentException("Redo row record pairs are empty");
        }

        RedoRowOperation operation = operation(recordPairs);
        Map<Integer, RedoColumnValueAccumulator> before =
                new LinkedHashMap<>();
        Map<Integer, RedoColumnValueAccumulator> after =
                new LinkedHashMap<>();
        Map<Integer, RedoColumnValueAccumulator> beforeSupplemental =
                new LinkedHashMap<>();
        Map<Integer, RedoColumnValueAccumulator> afterSupplemental =
                new LinkedHashMap<>();
        boolean supplementalPrevious = false;
        for (RedoRecordPair pair : recordPairs) {
            decodeUndoValues(pair.undo(), table.columns(), before);
            supplementalPrevious = decodeSupplementalValues(
                    pair, table.columns(), beforeSupplemental,
                    afterSupplemental, supplementalPrevious);
            decodeRedoValues(
                    pair.redo(), pair.undo().suppLogAfter,
                    table.columns(), after);
        }

        Map<Integer, RedoColumnValue> beforeValues = finish(before);
        Map<Integer, RedoColumnValue> afterValues = finish(after);
        mergeSupplemental(beforeValues, finish(beforeSupplemental));
        mergeSupplemental(afterValues, finish(afterSupplemental));
        normalize(operation, table, beforeValues, afterValues);
        RedoRecordPair first = recordPairs.get(0);
        return new DecodedRedoRow(
                operation, table, rowId(operation, recordPairs),
                first.undo().fileOffset,
                byName(table, beforeValues), byName(table, afterValues));
    }

    private void decodeUndoValues(
            RedoLogRecord record,
            List<ColumnSchema> columns,
            Map<Integer, RedoColumnValueAccumulator> values) {
        if (record.rowData <= 0) {
            return;
        }
        rejectCompressed(record);
        validateNullBitmap(record);
        int columnShift = columnShift(record, record.suppLogBefore);
        RedoFieldCursor cursor = cursorAtRowData(record, 0x000003);
        for (int index = 0; index < record.cc; index++) {
            int columnIndex = columnIndex(record, index, columnShift);
            ColumnSchema column = requireColumn(columns, columnIndex, record);
            boolean nullValue = isNull(record, index);
            byte[] data = new byte[0];
            if (!nullValue) {
                cursor.skipEmptyFields();
                cursor.next();
                data = fieldData(record, cursor, cursor.fieldSize());
            }
            accumulator(values, columnIndex, column).add(
                    data, nullValue, fragmentBits(record, index));
        }
    }

    private void decodeRedoValues(
            RedoLogRecord record,
            int supplementalAfter,
            List<ColumnSchema> columns,
            Map<Integer, RedoColumnValueAccumulator> values) {
        if (record.rowData <= 0) {
            return;
        }
        rejectCompressed(record);
        validateNullBitmap(record);
        int columnShift = columnShift(record, supplementalAfter);
        RedoFieldCursor cursor = cursorAtRowData(record, 0x000008);
        for (int index = 0; index < record.cc; index++) {
            cursor.next();
            int columnIndex = columnIndex(record, index, columnShift);
            ColumnSchema column = requireColumn(columns, columnIndex, record);
            boolean nullValue = isNull(record, index);
            byte[] data = new byte[0];
            if (!nullValue) {
                data = fieldData(record, cursor, cursor.fieldSize());
            }
            accumulator(values, columnIndex, column).add(
                    data, nullValue, fragmentBits(record, index));
        }
    }

    private boolean decodeSupplementalValues(
            RedoRecordPair pair,
            List<ColumnSchema> columns,
            Map<Integer, RedoColumnValueAccumulator> before,
            Map<Integer, RedoColumnValueAccumulator> after,
            boolean supplementalPrevious) {
        RedoLogRecord undo = pair.undo();
        if (undo.suppLogRowData <= 0) {
            return supplementalPrevious;
        }
        requireRange(undo, undo.suppLogNumsDelta,
                undo.suppLogCC * 2, "supplemental column numbers");
        requireRange(undo, undo.suppLogLenDelta,
                undo.suppLogCC * 2, "supplemental column lengths");
        RedoFieldCursor cursor = cursorAt(
                undo, undo.suppLogRowData, 0x000006);
        for (int index = 0; index < undo.suppLogCC; index++) {
            cursor.next();
            int numberOffset = undo.dataOffset()
                    + undo.suppLogNumsDelta + index * 2;
            int columnIndex = byteReader.readUnsignedShort(
                    undo.data(), numberOffset) - 1;
            ColumnSchema column = requireColumn(
                    columns, columnIndex, undo);
            int lengthOffset = undo.dataOffset()
                    + undo.suppLogLenDelta + index * 2;
            int length = byteReader.readUnsignedShort(
                    undo.data(), lengthOffset);
            boolean nullValue = length == 0 || length == 0xFFFF;
            if (!nullValue && length > cursor.fieldSize()) {
                throw invalid("supplemental value exceeds its field");
            }
            int bits = supplementalFragmentBits(
                    undo, index, supplementalPrevious);
            supplementalPrevious = nextSupplementalPrevious(
                    undo, index, supplementalPrevious);
            byte[] data = new byte[0];
            if (!nullValue) {
                data = fieldData(undo, cursor, length);
            }
            addSupplemental(pair.redo().opCode, before, after,
                    columnIndex, column, data, nullValue, bits);
        }
        return supplementalPrevious;
    }

    private static void addSupplemental(
            int redoOpCode,
            Map<Integer, RedoColumnValueAccumulator> before,
            Map<Integer, RedoColumnValueAccumulator> after,
            int columnIndex,
            ColumnSchema column,
            byte[] data,
            boolean nullValue,
            int fragmentBits) {
        if (redoOpCode == 0x0B02 || redoOpCode == 0x0B04
                || redoOpCode == 0x0B05 || redoOpCode == 0x0B10) {
            accumulator(after, columnIndex, column).add(
                    data, nullValue, fragmentBits);
        }
        if (redoOpCode == 0x0B03 || redoOpCode == 0x0B05
                || redoOpCode == 0x0B06 || redoOpCode == 0x0B10) {
            accumulator(before, columnIndex, column).add(
                    data, nullValue, fragmentBits);
        }
    }

    private static void normalize(
            RedoRowOperation operation,
            TableSchema table,
            Map<Integer, RedoColumnValue> before,
            Map<Integer, RedoColumnValue> after) {
        if (operation == RedoRowOperation.INSERT) {
            removeNullNonPrimaryKey(table, after);
            addMissingPrimaryKeys(table, after);
            before.clear();
            return;
        }
        if (operation == RedoRowOperation.DELETE) {
            removeNullNonPrimaryKey(table, before);
            addMissingPrimaryKeys(table, before);
            after.clear();
            return;
        }

        Set<Integer> columns = new LinkedHashSet<>(before.keySet());
        columns.addAll(after.keySet());
        for (int column : columns) {
            ColumnSchema schema = table.columns().get(column);
            RedoColumnValue beforeValue = before.get(column);
            RedoColumnValue afterValue = after.get(column);
            if (beforeValue != null && afterValue != null
                    && schema.primaryKeyMembership() == 0
                    && beforeValue.equals(afterValue)) {
                before.remove(column);
                after.remove(column);
                continue;
            }
            if (beforeValue == null) {
                before.put(column,
                        RedoColumnValue.nullValue(
                                schema.type(), schema.charsetId()));
            }
            if (afterValue == null) {
                after.put(column,
                        RedoColumnValue.nullValue(
                                schema.type(), schema.charsetId()));
            }
        }
    }

    private static void removeNullNonPrimaryKey(
            TableSchema table, Map<Integer, RedoColumnValue> values) {
        values.entrySet().removeIf(entry ->
                entry.getValue().nullValue()
                        && table.columns().get(entry.getKey())
                        .primaryKeyMembership() == 0);
    }

    private static void addMissingPrimaryKeys(
            TableSchema table, Map<Integer, RedoColumnValue> values) {
        for (int index : table.primaryKeyColumnIndexes()) {
            ColumnSchema column = table.columns().get(index);
            values.putIfAbsent(index,
                    RedoColumnValue.nullValue(
                            column.type(), column.charsetId()));
        }
    }

    private static Map<String, RedoColumnValue> byName(
            TableSchema table, Map<Integer, RedoColumnValue> values) {
        Map<String, RedoColumnValue> named = new LinkedHashMap<>();
        for (Map.Entry<Integer, RedoColumnValue> entry : values.entrySet()) {
            named.put(table.columns().get(entry.getKey()).name(),
                    entry.getValue());
        }
        return named;
    }

    private static Map<Integer, RedoColumnValue> finish(
            Map<Integer, RedoColumnValueAccumulator> accumulators) {
        Map<Integer, RedoColumnValue> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, RedoColumnValueAccumulator> entry
                : accumulators.entrySet()) {
            values.put(entry.getKey(), entry.getValue().finish());
        }
        return values;
    }

    private static void mergeSupplemental(
            Map<Integer, RedoColumnValue> values,
            Map<Integer, RedoColumnValue> supplemental) {
        for (Map.Entry<Integer, RedoColumnValue> entry
                : supplemental.entrySet()) {
            RedoColumnValue current = values.get(entry.getKey());
            if (current == null || current.nullValue()) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static RedoRowOperation operation(
            List<RedoRecordPair> recordPairs) {
        RedoRowOperation operation = null;
        for (RedoRecordPair pair : recordPairs) {
            operation = RedoRowOperation.append(
                    operation, pair.redo().opCode);
        }
        return operation;
    }

    private static RowId rowId(
            RedoRowOperation operation,
            List<RedoRecordPair> recordPairs) {
        if (operation == RedoRowOperation.INSERT) {
            for (RedoRecordPair pair : recordPairs) {
                RedoLogRecord redo = pair.redo();
                if ((redo.fb & RedoLogRecord.FB_F) != 0) {
                    return physicalRowId(
                            redo.dataObj, redo.bdba, redo.slot);
                }
            }
            throw invalid("inserted row has no first row piece");
        }
        RedoRecordPair first = recordPairs.get(0);
        RedoLogRecord undo = first.undo();
        if (undo.suppLogBdba > 0 || undo.suppLogSlot > 0) {
            return physicalRowId(
                    undo.dataObj, undo.suppLogBdba, undo.suppLogSlot);
        }
        RedoLogRecord redo = first.redo();
        return physicalRowId(redo.dataObj, redo.bdba, redo.slot);
    }

    private static RowId physicalRowId(
            long dataObjectId, long blockAddress, int slot) {
        if (dataObjectId == 0 || blockAddress == 0) {
            throw invalid("redo row is missing physical identity");
        }
        return RowId.of(dataObjectId, blockAddress, slot);
    }

    private int columnShift(RedoLogRecord record, int supplementalColumn) {
        int shift = 0;
        if (supplementalColumn > 0) {
            shift = supplementalColumn - 1;
        }
        if (record.colNumsDelta > 0) {
            requireRange(record, record.colNumsDelta,
                    record.cc * 2, "column numbers");
            int firstColumn = byteReader.readUnsignedShort(
                    record.data(), record.dataOffset()
                            + record.colNumsDelta);
            shift -= firstColumn;
        }
        return shift;
    }

    private int columnIndex(
            RedoLogRecord record, int index, int shift) {
        if (record.colNumsDelta == 0) {
            return index + shift;
        }
        int offset = record.dataOffset()
                + record.colNumsDelta + index * 2;
        return byteReader.readUnsignedShort(record.data(), offset) + shift;
    }

    private RedoFieldCursor cursorAtRowData(
            RedoLogRecord record, int code) {
        return cursorAt(record, record.rowData, code);
    }

    private RedoFieldCursor cursorAt(
            RedoLogRecord record, int fieldNumber, int code) {
        RedoFieldCursor cursor = new RedoFieldCursor(
                byteReader, record, code);
        while (cursor.fieldNumber() < fieldNumber - 1) {
            cursor.next();
        }
        return cursor;
    }

    private byte[] fieldData(
            RedoLogRecord record, RedoFieldCursor cursor, int length) {
        int start = record.dataOffset() + cursor.fieldPosition();
        return Arrays.copyOfRange(record.data(), start, start + length);
    }

    private static RedoColumnValueAccumulator accumulator(
            Map<Integer, RedoColumnValueAccumulator> values,
            int columnIndex,
            ColumnSchema column) {
        return values.computeIfAbsent(
                columnIndex,
                ignored -> new RedoColumnValueAccumulator(column));
    }

    private static ColumnSchema requireColumn(
            List<ColumnSchema> columns,
            int columnIndex,
            RedoLogRecord record) {
        if (columnIndex < 0 || columnIndex >= columns.size()) {
            throw new RedoLogException(50060,
                    "Redo refers to invalid column " + columnIndex
                            + " of " + columns.size()
                            + " at offset " + record.fileOffset);
        }
        return columns.get(columnIndex);
    }

    private static int fragmentBits(
            RedoLogRecord record, int index) {
        int bits = 0;
        if (index == 0 && (record.fb & RedoLogRecord.FB_P) != 0) {
            bits |= RedoLogRecord.FB_P;
        }
        if (index == record.cc - 1
                && (record.fb & RedoLogRecord.FB_N) != 0) {
            bits |= RedoLogRecord.FB_N;
        }
        return bits;
    }

    private static int supplementalFragmentBits(
            RedoLogRecord record,
            int index,
            boolean supplementalPrevious) {
        int bits = 0;
        if (index == 0
                && (record.suppLogFb & RedoLogRecord.FB_P) != 0
                && supplementalPrevious) {
            bits |= RedoLogRecord.FB_P;
        }
        if (index == record.suppLogCC - 1
                && (record.suppLogFb & RedoLogRecord.FB_N) != 0) {
            bits |= RedoLogRecord.FB_N;
        }
        return bits;
    }

    private static boolean nextSupplementalPrevious(
            RedoLogRecord record,
            int index,
            boolean supplementalPrevious) {
        if (index == 0
                && (record.suppLogFb & RedoLogRecord.FB_P) != 0
                && supplementalPrevious) {
            supplementalPrevious = false;
        }
        if (index == record.suppLogCC - 1
                && (record.suppLogFb & RedoLogRecord.FB_N) != 0) {
            return true;
        }
        return supplementalPrevious;
    }

    private static boolean isNull(RedoLogRecord record, int index) {
        return (record.byteAt(record.nullsDelta + index / 8)
                & (1 << (index & 7))) != 0;
    }

    private static void validateNullBitmap(RedoLogRecord record) {
        int bytes = (record.cc + 7) / 8;
        requireRange(record, record.nullsDelta, bytes, "NULL bitmap");
    }

    private static void rejectCompressed(RedoLogRecord record) {
        if (record.compressed) {
            throw invalid("compressed row redo is not supported");
        }
    }

    private static void requireRange(
            RedoLogRecord record, int offset, int length, String field) {
        if (offset < 0 || length < 0 || offset > record.size - length) {
            throw invalid("redo " + field + " is outside the record");
        }
    }

    private static RedoLogException invalid(String message) {
        return new RedoLogException(50014, message);
    }
}
