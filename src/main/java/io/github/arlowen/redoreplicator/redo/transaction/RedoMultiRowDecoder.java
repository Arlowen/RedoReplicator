/*
 * Java translation derived from OpenLogReplicator
 * Builder::processInsertMultiple and Builder::processDeleteMultiple in
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
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.TableSchema;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RedoMultiRowDecoder {
    private static final int ROW_HEADER_SIZE = 3;
    private static final int ROW_DEPENDENCIES_SIZE = 8;

    private final RedoByteReader byteReader;

    public RedoMultiRowDecoder(ByteOrder byteOrder) {
        byteReader = new RedoByteReader(Objects.requireNonNull(
                byteOrder, "byteOrder"));
    }

    public List<DecodedRedoRow> decode(
            TableSchema table,
            RedoLogRecord undo,
            RedoLogRecord redo) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(undo, "undo");
        Objects.requireNonNull(redo, "redo");
        if (redo.opCode == 0x0B0B) {
            return decodeRows(
                    table, undo, redo, redo,
                    RedoRowOperation.INSERT);
        }
        if (redo.opCode == 0x0B0C) {
            return decodeRows(
                    table, undo, redo, undo,
                    RedoRowOperation.DELETE);
        }
        throw invalid("unsupported multi-row opcode 0x"
                + Integer.toHexString(redo.opCode));
    }

    private List<DecodedRedoRow> decodeRows(
            TableSchema table,
            RedoLogRecord undo,
            RedoLogRecord redo,
            RedoLogRecord rowRecord,
            RedoRowOperation operation) {
        if (rowRecord.nRow <= 0 || rowRecord.rowData <= 0) {
            throw invalid("multi-row redo contains no rows");
        }
        requireRange(rowRecord, rowRecord.rowSizesDelta,
                rowRecord.nRow * 2, "row size array");
        requireRange(rowRecord, rowRecord.slotsDelta,
                rowRecord.nRow * 2, "row slot array");
        RedoFieldCursor cursor = new RedoFieldCursor(
                byteReader, rowRecord, 0x000009);
        while (cursor.fieldNumber() < rowRecord.rowData) {
            cursor.next();
        }

        int rowPosition = cursor.fieldPosition();
        int fieldEnd = rowPosition + cursor.fieldSize();
        List<DecodedRedoRow> rows = new ArrayList<>(rowRecord.nRow);
        for (int index = 0; index < rowRecord.nRow; index++) {
            int rowSizeOffset = rowRecord.dataOffset()
                    + rowRecord.rowSizesDelta + index * 2;
            int rowSize = byteReader.readUnsignedShort(
                    rowRecord.data(), rowSizeOffset);
            if (rowSize < ROW_HEADER_SIZE
                    || rowPosition > fieldEnd - rowSize) {
                throw invalid("multi-row image exceeds its data field");
            }
            int slotOffset = rowRecord.dataOffset()
                    + rowRecord.slotsDelta + index * 2;
            int slot = byteReader.readUnsignedShort(
                    rowRecord.data(), slotOffset);
            rows.add(decodeRow(
                    table, undo, redo, rowRecord, operation,
                    rowPosition, rowSize, slot));
            rowPosition += rowSize;
        }
        if (rowPosition != fieldEnd) {
            throw invalid("multi-row size array does not cover its data field");
        }
        return List.copyOf(rows);
    }

    private DecodedRedoRow decodeRow(
            TableSchema table,
            RedoLogRecord undo,
            RedoLogRecord redo,
            RedoLogRecord rowRecord,
            RedoRowOperation operation,
            int rowPosition,
            int rowSize,
            int slot) {
        int absoluteRow = rowRecord.dataOffset() + rowPosition;
        int columnCount = rowRecord.data()[absoluteRow + 2] & 0xFF;
        if (columnCount > table.columns().size()) {
            throw new RedoLogException(50060,
                    "Multi-row redo refers to " + columnCount
                            + " columns of " + table.columns().size()
                            + " at offset " + undo.fileOffset);
        }
        int position = ROW_HEADER_SIZE;
        if ((rowRecord.op & RedoLogRecord.OP_ROWDEPENDENCIES) != 0) {
            position += ROW_DEPENDENCIES_SIZE;
        }
        if (position > rowSize) {
            throw invalid("multi-row header exceeds its row image");
        }

        Map<String, RedoColumnValue> values = new LinkedHashMap<>();
        for (int index = 0; index < table.columns().size(); index++) {
            ColumnSchema column = table.columns().get(index);
            int length = 0;
            if (index < columnCount) {
                if (position >= rowSize) {
                    throw invalid("multi-row column length is missing");
                }
                int marker = rowRecord.data()[
                        absoluteRow + position] & 0xFF;
                position++;
                if (marker == 0xFE) {
                    if (position > rowSize - 2) {
                        throw invalid("multi-row extended length is truncated");
                    }
                    length = byteReader.readUnsignedShort(
                            rowRecord.data(), absoluteRow + position);
                    position += 2;
                } else if (marker != 0xFF) {
                    length = marker;
                }
            }
            if (position > rowSize - length) {
                throw invalid("multi-row column value is truncated");
            }
            if (length > 0) {
                byte[] data = Arrays.copyOfRange(
                        rowRecord.data(), absoluteRow + position,
                        absoluteRow + position + length);
                values.put(column.name(),
                        RedoColumnValue.of(
                                column.type(), column.charsetId(), data));
            } else if (column.primaryKeyMembership() > 0) {
                values.put(column.name(),
                        RedoColumnValue.nullValue(
                                column.type(), column.charsetId()));
            }
            position += length;
        }
        if (position != rowSize) {
            throw invalid("multi-row image contains unparsed column bytes");
        }

        RedoLogRecord identity = redo;
        if (identity.dataObj == 0 || identity.bdba == 0) {
            throw invalid("multi-row redo is missing physical row identity");
        }
        RowId rowId = RowId.of(identity.dataObj, identity.bdba, slot);
        if (operation == RedoRowOperation.INSERT) {
            return new DecodedRedoRow(
                    operation, table, rowId, undo.fileOffset,
                    Map.of(), values);
        }
        return new DecodedRedoRow(
                operation, table, rowId, undo.fileOffset,
                values, Map.of());
    }

    private static void requireRange(
            RedoLogRecord record, int offset, int length, String field) {
        if (offset < 0 || length < 0 || offset > record.size - length) {
            throw invalid("multi-row " + field + " is outside the record");
        }
    }

    private static RedoLogException invalid(String message) {
        return new RedoLogException(50014, message);
    }
}
