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

    public static void requireSize(RedoLogRecord record, int fieldSize,
                                   int minimumSize, String fieldName) {
        if (fieldSize < minimumSize) {
            throw new RedoLogException(50061, "too short field " + fieldName + ": "
                    + fieldSize + " offset: " + record.fileOffset);
        }
    }
}
