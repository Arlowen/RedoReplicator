/*
 * Java translation derived from field traversal in OpenLogReplicator:
 * src/common/RedoLogRecord.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import io.github.arlowen.redoreplicator.error.RedoLogException;

public final class RedoFieldCursor {
    private final RedoByteReader byteReader;
    private final RedoLogRecord record;
    private final int code;

    private int fieldNumber;
    private int fieldPosition;
    private int fieldSize;

    public RedoFieldCursor(RedoByteReader byteReader, RedoLogRecord record, int code) {
        this.byteReader = byteReader;
        this.record = record;
        this.code = code;
    }

    public boolean nextOptional() {
        if (fieldNumber >= record.fieldCnt) {
            return false;
        }
        fieldNumber++;
        updatePositionAndSize();
        if (fieldPosition + fieldSize > record.size) {
            throw new RedoLogException(50005, "field size out of vector, field: "
                    + fieldNumber + "/" + record.fieldCnt + ", pos: " + fieldPosition
                    + ", size: " + fieldSize + ", max: " + record.size + ", code: " + code);
        }
        return true;
    }

    public void next() {
        fieldNumber++;
        if (fieldNumber > record.fieldCnt) {
            throw new RedoLogException(50006, "field missing in vector, field: "
                    + fieldNumber + "/" + record.fieldCnt + ", ctx: " + record.rowData
                    + ", obj: " + record.obj + ", dataobj: " + record.dataObj
                    + ", op: " + record.opCode + ", cc: " + record.cc
                    + ", suppCC: " + record.suppLogCC + ", fieldSize: " + fieldSize
                    + ", code: " + code);
        }
        updatePositionAndSize();
        if (fieldPosition + fieldSize > record.size) {
            throw new RedoLogException(50007, "field size out of vector, field: "
                    + fieldNumber + "/" + record.fieldCnt + ", pos: " + fieldPosition
                    + ", size: " + fieldSize + ", max: " + record.size + ", code: " + code);
        }
    }

    public void skipEmptyFields() {
        while (fieldNumber + 1 <= record.fieldCnt) {
            int nextFieldSize = readFieldSize(fieldNumber + 1);
            if (nextFieldSize != 0) {
                return;
            }
            fieldNumber++;
            if (fieldNumber == 1) {
                fieldPosition = record.fieldPos;
            } else {
                fieldPosition = (fieldPosition + align4(fieldSize)) & 0xFFFF;
            }
            fieldSize = nextFieldSize;
            if (fieldPosition + fieldSize > record.size) {
                throw new RedoLogException(50008, "field size out of vector: field: "
                        + fieldNumber + "/" + record.fieldCnt + ", pos: " + fieldPosition
                        + ", size: " + fieldSize + ", max: " + record.size);
            }
        }
    }

    public int fieldNumber() {
        return fieldNumber;
    }

    public int fieldPosition() {
        return fieldPosition;
    }

    public int fieldSize() {
        return fieldSize;
    }

    private void updatePositionAndSize() {
        if (fieldNumber == 1) {
            fieldPosition = record.fieldPos;
        } else {
            fieldPosition = (fieldPosition + align4(fieldSize)) & 0xFFFF;
        }
        fieldSize = readFieldSize(fieldNumber);
    }

    private int readFieldSize(int number) {
        int offset = record.dataOffset() + record.fieldSizesDelta + number * 2;
        return byteReader.readUnsignedShort(record.data(), offset);
    }

    private static int align4(int value) {
        return (value + 3) & 0xFFFC;
    }
}
