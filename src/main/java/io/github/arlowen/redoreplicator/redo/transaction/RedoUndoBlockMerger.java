/*
 * Java translation derived from OpenLogReplicator
 * src/parser/TransactionBuffer.cpp mergeBlocks.
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
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

import java.nio.ByteOrder;

public final class RedoUndoBlockMerger {
    private static final int KTU_FLAGS_OFFSET = 20;

    private final RedoByteReader byteReader;
    private final ByteOrder byteOrder;

    public RedoUndoBlockMerger(ByteOrder byteOrder) {
        byteReader = new RedoByteReader(byteOrder);
        this.byteOrder = byteOrder;
    }

    public RedoLogRecord merge(
            RedoLogRecord current, RedoLogRecord previous) {
        requireMergeable(current);
        requireMergeable(previous);

        int currentFieldCount = current.fieldCnt;
        boolean lastBufferSplit = (current.flg
                & RedoTransactionBuffer.FLG_LAST_BUFFER_SPLIT) != 0;
        int combinedFieldSize = 0;
        if (lastBufferSplit) {
            current.flg &= ~RedoTransactionBuffer.FLG_LAST_BUFFER_SPLIT;
            int currentLastSize = fieldSize(current, current.fieldCnt);
            int previousThirdSize = fieldSize(previous, 3);
            combinedFieldSize = currentLastSize + previousThirdSize;
            currentFieldCount--;
        }

        int fieldCount = currentFieldCount + previous.fieldCnt - 2;
        int fieldListLength = 2 + fieldCount * 2;
        int fieldPosition = current.fieldSizesDelta + align4(fieldListLength);
        byte[] merged = new byte[current.size + previous.size];
        System.arraycopy(current.data(), current.dataOffset(), merged, 0,
                current.fieldSizesDelta);
        writeUnsignedShort(merged, current.fieldSizesDelta, fieldListLength);

        int fieldListPosition = current.fieldSizesDelta + 2;
        System.arraycopy(current.data(),
                current.dataOffset() + current.fieldSizesDelta + 2,
                merged, fieldListPosition, currentFieldCount * 2);
        fieldListPosition += currentFieldCount * 2;
        for (int field = 3; field <= previous.fieldCnt; field++) {
            int size = fieldSize(previous, field);
            if (lastBufferSplit && field == 3) {
                size = combinedFieldSize;
            }
            writeUnsignedShort(merged, fieldListPosition, size);
            fieldListPosition += 2;
        }

        int position = fieldPosition;
        int currentDataSize = current.size - current.fieldPos;
        System.arraycopy(current.data(),
                current.dataOffset() + current.fieldPos,
                merged, position, currentDataSize);
        position += align4(currentDataSize);

        int previousDataPosition = previous.fieldPos
                + align4(fieldSize(previous, 1))
                + align4(fieldSize(previous, 2));
        int previousDataSize = previous.size - previousDataPosition;
        System.arraycopy(previous.data(),
                previous.dataOffset() + previousDataPosition,
                merged, position, previousDataSize);
        position += align4(previousDataSize);

        current.fieldCnt = fieldCount;
        current.fieldPos = fieldPosition;
        current.attachData(merged, 0, position);
        current.flg |= previous.flg;
        if ((current.flg
                & RedoTransactionBuffer.FLG_MULTIBLOCK_UNDO_TAIL) != 0) {
            current.flg &= ~(RedoTransactionBuffer.FLG_MULTIBLOCK_UNDO_HEAD
                    | RedoTransactionBuffer.FLG_MULTIBLOCK_UNDO_MIDDLE
                    | RedoTransactionBuffer.FLG_MULTIBLOCK_UNDO_TAIL);
        }
        return current;
    }

    public void writeKtuFlags(RedoLogRecord record) {
        int ktuPosition = record.fieldPos + align4(fieldSize(record, 1));
        if (fieldSize(record, 2) < KTU_FLAGS_OFFSET + 2) {
            throw new RedoLogException(50041,
                    "Split undo KTU block is too short at offset "
                            + record.fileOffset);
        }
        writeUnsignedShort(record.data(),
                record.dataOffset() + ktuPosition + KTU_FLAGS_OFFSET,
                record.flg);
    }

    private int fieldSize(RedoLogRecord record, int field) {
        int position = record.dataOffset()
                + record.fieldSizesDelta + field * 2;
        return byteReader.readUnsignedShort(record.data(), position);
    }

    private void writeUnsignedShort(byte[] data, int offset, int value) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            data[offset] = (byte) (value >>> 8);
            data[offset + 1] = (byte) value;
            return;
        }
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void requireMergeable(RedoLogRecord record) {
        if (record.data() == null || record.fieldCnt < 3) {
            throw new RedoLogException(50041,
                    "Invalid split undo block at offset " + record.fileOffset);
        }
    }

    private static int align4(int size) {
        return (size + 3) & 0xFFFC;
    }
}
