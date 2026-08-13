/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

import java.nio.ByteOrder;

public final class RedoOpCodeTestSupport {
    private RedoOpCodeTestSupport() {
    }

    public static RedoLogRecord record(int opCode, int undoSegment, byte[]... fields) {
        int fieldListLength = 2 + fields.length * 2;
        int fieldPosition = align4(fieldListLength + 2);
        int recordSize = fieldPosition;
        for (byte[] field : fields) {
            recordSize += align4(field.length);
        }

        byte[] data = new byte[recordSize];
        RedoBinaryTestSupport.writeUnsignedShort(
                data, 0, fieldListLength, ByteOrder.LITTLE_ENDIAN);
        int position = fieldPosition;
        for (int index = 0; index < fields.length; index++) {
            byte[] field = fields[index];
            RedoBinaryTestSupport.writeUnsignedShort(
                    data, 2 + index * 2, field.length, ByteOrder.LITTLE_ENDIAN);
            System.arraycopy(field, 0, data, position, field.length);
            position += align4(field.length);
        }

        RedoLogRecord record = new RedoLogRecord();
        record.attachData(data, 0, data.length);
        record.opCode = opCode;
        record.usn = undoSegment;
        record.fieldSizesDelta = 0;
        record.fieldCnt = fields.length;
        record.fieldPos = fieldPosition;
        record.fileOffset = FileOffset.of(123_456);
        return record;
    }

    public static byte[] field(int size) {
        return new byte[size];
    }

    private static int align4(int size) {
        return (size + 3) & 0xFFFC;
    }
}
