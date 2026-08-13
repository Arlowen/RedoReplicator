/*
 * Java translation derived from common opcode field parsing in
 * OpenLogReplicator src/parser/OpCode.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;

public final class RedoOpCodeSupport {
    public static final int FLG_KTUCF_0504 = 0x0002;

    private RedoOpCodeSupport() {
    }

    public static void readObjectPair(RedoByteReader byteReader, RedoLogRecord record,
                                      int fieldPosition, int fieldSize, String fieldName) {
        requireSize(record, fieldSize, 8, fieldName);
        record.obj = byteReader.readUnsignedInt(record.data(), record.dataOffset() + fieldPosition);
        record.dataObj = byteReader.readUnsignedInt(
                record.data(), record.dataOffset() + fieldPosition + 4);
    }

    public static void readKtuBlock(RedoByteReader byteReader, RedoLogRecord record,
                                    int fieldPosition, int fieldSize) {
        requireSize(record, fieldSize, 24, "ktub");
        int absolutePosition = record.dataOffset() + fieldPosition;
        record.obj = byteReader.readUnsignedInt(record.data(), absolutePosition);
        record.dataObj = byteReader.readUnsignedInt(record.data(), absolutePosition + 4);
        record.opc = (record.data()[absolutePosition + 16] & 0xFF) << 8
                | (record.data()[absolutePosition + 17] & 0xFF);
        record.slt = record.data()[absolutePosition + 18] & 0xFF;
        record.flg = byteReader.readUnsignedShort(record.data(), absolutePosition + 20);
    }

    public static void readKtbRedo(RedoByteReader byteReader, RedoLogRecord record,
                                   int fieldPosition, int fieldSize) {
        if (fieldSize < 8) {
            return;
        }

        int absolutePosition = record.dataOffset() + fieldPosition;
        int operation = record.data()[absolutePosition] & 0x0F;
        int flags = record.data()[absolutePosition + 1] & 0xFF;
        int startPosition = 4;
        if ((flags & 0x08) != 0) {
            startPosition = 8;
        }

        if (operation == 0x02) {
            requireSize(record, fieldSize, startPosition + 8, "KTB Redo C");
            return;
        }
        if (operation == 0x04) {
            requireSize(record, fieldSize, startPosition + 24, "KTB Redo L2");
            return;
        }
        if (operation != 0x01) {
            return;
        }

        requireSize(record, fieldSize, startPosition + 16, "KTB Redo F");
        int xidPosition = absolutePosition + startPosition;
        int undoSegment = byteReader.readUnsignedShort(record.data(), xidPosition);
        int slot = byteReader.readUnsignedShort(record.data(), xidPosition + 2);
        long sequence = byteReader.readUnsignedInt(record.data(), xidPosition + 4);
        record.xid = Xid.of(undoSegment, slot, sequence);
    }

    public static void requireSize(RedoLogRecord record, int fieldSize,
                                   int minimumSize, String fieldName) {
        if (fieldSize < minimumSize) {
            throw new RedoLogException(50061, "too short field " + fieldName + ": "
                    + fieldSize + " offset: " + record.fileOffset);
        }
    }
}
