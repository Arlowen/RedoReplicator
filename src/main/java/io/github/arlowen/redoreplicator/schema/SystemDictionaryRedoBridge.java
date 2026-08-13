/*
 * Java translation derived from OpenLogReplicator Builder::processDml and
 * parser/Transaction.cpp system transaction routing.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoFieldCursor;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoRecordPair;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Xid;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SystemDictionaryRedoBridge {
    private final RedoByteReader byteReader;

    public SystemDictionaryRedoBridge(ByteOrder byteOrder) {
        byteReader = new RedoByteReader(byteOrder);
    }

    public SystemDictionaryRedoChange decode(
            SystemDictionaryTable table, TableSchema physicalTable,
            List<RedoRecordPair> recordPairs) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(physicalTable, "physicalTable");
        if (recordPairs.isEmpty()) {
            throw new IllegalArgumentException("Dictionary redo record pairs are empty");
        }
        validatePhysicalTable(table, physicalTable);

        Xid xid = recordPairs.get(0).undo().xid;
        if (xid.isEmpty()) {
            throw invalid("System dictionary redo is missing an XID");
        }
        SystemDictionaryOperation operation = null;
        for (RedoRecordPair pair : recordPairs) {
            validatePair(pair, physicalTable, xid);
            operation = nextOperation(operation, pair.redo().opCode);
        }

        RowId rowId = findRowId(operation, recordPairs);
        if (operation == SystemDictionaryOperation.DELETE) {
            return new SystemDictionaryRedoChange(
                    xid, SystemDictionaryChange.delete(table, rowId));
        }

        Map<Integer, RedoColumnValueAccumulator> columnValues =
                new LinkedHashMap<>();
        Map<Integer, RedoColumnValueAccumulator> supplementalValues =
                new LinkedHashMap<>();
        boolean supplementalPrevious = false;
        for (RedoRecordPair pair : recordPairs) {
            decodeAfterValues(pair, physicalTable.columns(), columnValues);
            supplementalPrevious = decodeSupplementalAfterValues(
                    pair, physicalTable.columns(), supplementalValues,
                    supplementalPrevious);
        }
        if (columnValues.isEmpty() && supplementalValues.isEmpty()) {
            throw invalid("Dictionary " + operation
                    + " contains no after-image values");
        }

        Map<Integer, SystemDictionaryValue> decodedValues =
                finishValues(columnValues);
        for (Map.Entry<Integer, SystemDictionaryValue> entry
                : finishValues(supplementalValues).entrySet()) {
            SystemDictionaryValue current = decodedValues.get(entry.getKey());
            if (current == null || current.nullValue()) {
                decodedValues.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, SystemDictionaryValue> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, SystemDictionaryValue> entry
                : decodedValues.entrySet()) {
            String columnName = physicalTable.columns().get(entry.getKey()).name();
            values.put(columnName, entry.getValue());
        }
        SystemDictionaryChange change = new SystemDictionaryChange(
                operation, table, rowId, values);
        return new SystemDictionaryRedoChange(xid, change);
    }

    private boolean decodeSupplementalAfterValues(
            RedoRecordPair pair, List<ColumnSchema> columns,
            Map<Integer, RedoColumnValueAccumulator> values,
            boolean supplementalPrevious) {
        RedoLogRecord undo = pair.undo();
        int redoOpCode = pair.redo().opCode;
        boolean containsAfterValues = redoOpCode == 0x0B02
                || redoOpCode == 0x0B05 || redoOpCode == 0x0B10;
        if (!containsAfterValues || undo.suppLogRowData <= 0) {
            return supplementalPrevious;
        }
        requireRange(undo, undo.suppLogNumsDelta, undo.suppLogCC * 2,
                "supplemental column number array");
        requireRange(undo, undo.suppLogLenDelta, undo.suppLogCC * 2,
                "supplemental column length array");

        RedoFieldCursor cursor = new RedoFieldCursor(byteReader, undo, 0x000006);
        while (cursor.fieldNumber() < undo.suppLogRowData - 1) {
            cursor.next();
        }
        for (int index = 0; index < undo.suppLogCC; index++) {
            cursor.next();
            int numberOffset = undo.dataOffset()
                    + undo.suppLogNumsDelta + index * 2;
            int columnIndex = byteReader.readUnsignedShort(
                    undo.data(), numberOffset) - 1;
            if (columnIndex < 0 || columnIndex >= columns.size()) {
                throw new RedoLogException(50060,
                        "Dictionary supplemental redo refers to invalid column "
                                + columnIndex + " of " + columns.size());
            }

            int lengthOffset = undo.dataOffset()
                    + undo.suppLogLenDelta + index * 2;
            int length = byteReader.readUnsignedShort(undo.data(), lengthOffset);
            boolean nullValue = length == 0 || length == 0xFFFF;
            if (!nullValue && length > cursor.fieldSize()) {
                throw invalid("Dictionary supplemental value length exceeds its field");
            }

            int fragmentBits = 0;
            if (index == 0 && (undo.suppLogFb & RedoLogRecord.FB_P) != 0
                    && supplementalPrevious) {
                fragmentBits |= RedoLogRecord.FB_P;
                supplementalPrevious = false;
            }
            if (index == undo.suppLogCC - 1
                    && (undo.suppLogFb & RedoLogRecord.FB_N) != 0) {
                fragmentBits |= RedoLogRecord.FB_N;
                supplementalPrevious = true;
            }

            byte[] data = new byte[0];
            if (!nullValue) {
                int start = undo.dataOffset() + cursor.fieldPosition();
                data = Arrays.copyOfRange(undo.data(), start, start + length);
            }
            ColumnSchema column = columns.get(columnIndex);
            RedoColumnValueAccumulator accumulator = values.computeIfAbsent(
                    columnIndex,
                    ignored -> new RedoColumnValueAccumulator(column));
            accumulator.add(data, nullValue, fragmentBits);
        }
        return supplementalPrevious;
    }

    private static Map<Integer, SystemDictionaryValue> finishValues(
            Map<Integer, RedoColumnValueAccumulator> accumulators) {
        Map<Integer, SystemDictionaryValue> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, RedoColumnValueAccumulator> entry
                : accumulators.entrySet()) {
            values.put(entry.getKey(), entry.getValue().finish());
        }
        return values;
    }

    private void decodeAfterValues(
            RedoRecordPair pair, List<ColumnSchema> columns,
            Map<Integer, RedoColumnValueAccumulator> values) {
        RedoLogRecord redo = pair.redo();
        if (redo.rowData <= 0) {
            return;
        }
        if (redo.compressed) {
            throw invalid("Compressed system dictionary row redo is not supported");
        }
        if (redo.nullsDelta < 0 || redo.nullsDelta >= redo.size) {
            throw invalid("Dictionary redo NULL bitmap is outside the record");
        }

        int columnShift = 0;
        if (pair.undo().suppLogAfter > 0) {
            columnShift = pair.undo().suppLogAfter - 1;
        }
        if (redo.colNumsDelta > 0) {
            requireRange(redo, redo.colNumsDelta, redo.cc * 2,
                    "column number array");
            int firstColumn = byteReader.readUnsignedShort(
                    redo.data(), redo.dataOffset() + redo.colNumsDelta);
            columnShift -= firstColumn;
        }

        int nullBytes = (redo.cc + 7) / 8;
        requireRange(redo, redo.nullsDelta, nullBytes, "NULL bitmap");
        RedoFieldCursor cursor = new RedoFieldCursor(byteReader, redo, 0x000008);
        while (cursor.fieldNumber() < redo.rowData - 1) {
            cursor.next();
        }

        for (int index = 0; index < redo.cc; index++) {
            cursor.next();
            int columnIndex = index + columnShift;
            if (redo.colNumsDelta > 0) {
                int offset = redo.dataOffset() + redo.colNumsDelta + index * 2;
                columnIndex = byteReader.readUnsignedShort(redo.data(), offset)
                        + columnShift;
            }
            if (columnIndex < 0 || columnIndex >= columns.size()) {
                throw new RedoLogException(50060,
                        "Dictionary redo refers to invalid column "
                                + columnIndex + " of " + columns.size());
            }

            int fragmentBits = 0;
            if (index == 0 && (redo.fb & RedoLogRecord.FB_P) != 0) {
                fragmentBits |= RedoLogRecord.FB_P;
            }
            if (index == redo.cc - 1
                    && (redo.fb & RedoLogRecord.FB_N) != 0) {
                fragmentBits |= RedoLogRecord.FB_N;
            }

            boolean nullValue = (redo.byteAt(redo.nullsDelta + index / 8)
                    & (1 << (index & 7))) != 0;
            byte[] data = new byte[0];
            if (!nullValue) {
                int start = redo.dataOffset() + cursor.fieldPosition();
                data = Arrays.copyOfRange(
                        redo.data(), start, start + cursor.fieldSize());
            }
            ColumnSchema column = columns.get(columnIndex);
            RedoColumnValueAccumulator accumulator = values.computeIfAbsent(
                    columnIndex,
                    ignored -> new RedoColumnValueAccumulator(column));
            accumulator.add(data, nullValue, fragmentBits);
        }
    }

    private static void validatePhysicalTable(
            SystemDictionaryTable table, TableSchema physicalTable) {
        if (!"SYS".equals(physicalTable.owner())
                || !table.tableName().equals(physicalTable.name())) {
            throw new IllegalArgumentException(
                    "Physical table " + physicalTable.qualifiedName()
                            + " does not match " + table.qualifiedName());
        }
    }

    private static void validatePair(
            RedoRecordPair pair, TableSchema physicalTable, Xid xid) {
        if (!pair.undo().xid.equals(xid)) {
            throw invalid("Dictionary row pieces contain different XIDs");
        }
        if (pair.undo().opCode != 0x0501) {
            throw invalid("Expected undo opcode 0x0501, found 0x"
                    + Integer.toHexString(pair.undo().opCode));
        }
        long objectId = physicalTable.objectId();
        if (pair.undo().obj != 0 && pair.undo().obj != objectId) {
            throw invalid("Undo object " + pair.undo().obj
                    + " does not match dictionary object " + objectId);
        }
        if (pair.redo().obj != 0 && pair.redo().obj != objectId) {
            throw invalid("Redo object " + pair.redo().obj
                    + " does not match dictionary object " + objectId);
        }
    }

    private static SystemDictionaryOperation nextOperation(
            SystemDictionaryOperation current, int opCode) {
        SystemDictionaryOperation next;
        if (opCode == 0x0B02) {
            next = SystemDictionaryOperation.INSERT;
        } else if (opCode == 0x0B03) {
            next = SystemDictionaryOperation.DELETE;
        } else if (opCode == 0x0B05 || opCode == 0x0B06
                || opCode == 0x0B08 || opCode == 0x0B10
                || opCode == 0x0B16) {
            next = SystemDictionaryOperation.UPDATE;
        } else {
            throw invalid("Unsupported dictionary row opcode 0x"
                    + Integer.toHexString(opCode));
        }
        if (current == null || current == next) {
            return next;
        }
        return SystemDictionaryOperation.UPDATE;
    }

    private static RowId findRowId(
            SystemDictionaryOperation operation,
            List<RedoRecordPair> recordPairs) {
        if (operation == SystemDictionaryOperation.INSERT) {
            for (RedoRecordPair pair : recordPairs) {
                RedoLogRecord redo = pair.redo();
                if ((redo.fb & RedoLogRecord.FB_F) != 0) {
                    return rowId(redo.dataObj, redo.bdba, redo.slot);
                }
            }
            throw invalid("Inserted dictionary row has no first row piece");
        }

        RedoRecordPair first = recordPairs.get(0);
        RedoLogRecord undo = first.undo();
        if (undo.suppLogBdba > 0 || undo.suppLogSlot > 0) {
            return rowId(undo.dataObj, undo.suppLogBdba, undo.suppLogSlot);
        }
        RedoLogRecord redo = first.redo();
        return rowId(redo.dataObj, redo.bdba, redo.slot);
    }

    private static RowId rowId(long dataObject, long blockAddress, int slot) {
        if (dataObject == 0 || blockAddress == 0) {
            throw invalid("Dictionary row is missing physical row identity");
        }
        return RowId.of(dataObject, blockAddress, slot);
    }

    private static void requireRange(
            RedoLogRecord record, int offset, int length, String field) {
        if (offset < 0 || length < 0 || offset > record.size - length) {
            throw invalid("Dictionary redo " + field + " is outside the record");
        }
    }

    private static RedoLogException invalid(String message) {
        return new RedoLogException(50014, message);
    }
}
