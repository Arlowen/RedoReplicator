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
import io.github.arlowen.redoreplicator.redo.common.RedoFieldCursor;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;

public final class RedoOpCodeSupport {
    public static final int FLG_KTUCF_0504 = 0x0002;
    public static final int FLAGS_KDO_KDOM2 = 0x80;

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

    public static RedoFieldCursor readRowHeaders(RedoByteReader byteReader,
                                                 RedoLogRecord record, int code) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, code);
        fields.next();
        readKtbRedo(byteReader, record, fields.fieldPosition(), fields.fieldSize());
        if (!fields.nextOptional()) {
            return null;
        }
        readKdoOperation(byteReader, record, fields.fieldPosition(), fields.fieldSize());
        return fields;
    }

    public static void readKdoOperation(RedoByteReader byteReader, RedoLogRecord record,
                                        int fieldPosition, int fieldSize) {
        requireSize(record, fieldSize, 16, "kdo OpCode");
        int absolutePosition = record.dataOffset() + fieldPosition;
        record.bdba = byteReader.readUnsignedInt(record.data(), absolutePosition);
        record.op = record.data()[absolutePosition + 10] & 0xFF;
        record.flags = record.data()[absolutePosition + 11] & 0xFF;

        switch (record.op & 0x1F) {
            case RedoLogRecord.OP_IRP:
                readKdoInsert(byteReader, record, fieldPosition, fieldSize);
                break;
            case RedoLogRecord.OP_DRP:
            case RedoLogRecord.OP_LKR:
                requireSize(record, fieldSize, 20, "kdo OpCode row slot");
                record.slot = byteReader.readUnsignedShort(record.data(), absolutePosition + 16);
                break;
            case RedoLogRecord.OP_URP:
                readKdoUpdate(byteReader, record, fieldPosition, fieldSize);
                break;
            case RedoLogRecord.OP_ORP:
                readKdoOverwrite(byteReader, record, fieldPosition, fieldSize);
                break;
            case RedoLogRecord.OP_CKI:
                requireSize(record, fieldSize, 28, "kdo OpCode SKL");
                record.slot = record.data()[absolutePosition + 27] & 0xFF;
                break;
            case RedoLogRecord.OP_CFA:
                requireSize(record, fieldSize, 32, "kdo OpCode CFA");
                record.slot = byteReader.readUnsignedShort(record.data(), absolutePosition + 24);
                break;
            case RedoLogRecord.OP_QMI:
            case RedoLogRecord.OP_QMD:
                readKdoQuickMultiRow(record, fieldPosition, fieldSize);
                break;
            default:
                break;
        }
    }

    public static void readRowColumns(RedoLogRecord record, RedoFieldCursor fields,
                                      String errorField) {
        record.rowData = fields.fieldNumber() + 1;
        if (!fields.nextOptional()) {
            return;
        }
        if (fields.fieldSize() == record.sizeDelt && (record.cc > 1 || record.cc == 0)) {
            record.compressed = true;
            return;
        }

        for (int column = 0; column < record.cc; column++) {
            if (fields.fieldSize() > 0 && isColumnNull(record, column)) {
                throw new RedoLogException(50061, "too short field " + errorField + "."
                        + fields.fieldNumber() + ": " + fields.fieldSize()
                        + " offset: " + record.fileOffset);
            }
            if (fields.fieldNumber() < record.fieldCnt && column < record.ccData) {
                fields.next();
            } else {
                break;
            }
        }
    }

    public static boolean isColumnNull(RedoLogRecord record, int column) {
        int nullBytePosition = record.dataOffset() + record.nullsDelta + column / 8;
        int bit = 1 << (column % 8);
        return (record.data()[nullBytePosition] & bit) != 0;
    }

    private static void readKdoInsert(RedoByteReader byteReader, RedoLogRecord record,
                                      int fieldPosition, int fieldSize) {
        requireSize(record, fieldSize, 48, "kdo OpCode IRP");
        int absolutePosition = record.dataOffset() + fieldPosition;
        record.fb = record.data()[absolutePosition + 16] & 0xFF;
        record.cc = record.data()[absolutePosition + 18] & 0xFF;
        record.sizeDelt = byteReader.readUnsignedShort(record.data(), absolutePosition + 40);
        record.slot = byteReader.readUnsignedShort(record.data(), absolutePosition + 42);
        readNullBitmap(record, fieldPosition + 45, fieldSize, 45, "kdo OpCode IRP");
    }

    private static void readKdoUpdate(RedoByteReader byteReader, RedoLogRecord record,
                                      int fieldPosition, int fieldSize) {
        requireSize(record, fieldSize, 28, "kdo OpCode URP");
        int absolutePosition = record.dataOffset() + fieldPosition;
        record.fb = record.data()[absolutePosition + 16] & 0xFF;
        record.slot = byteReader.readUnsignedShort(record.data(), absolutePosition + 20);
        record.cc = record.data()[absolutePosition + 23] & 0xFF;
        readNullBitmap(record, fieldPosition + 26, fieldSize, 26, "kdo OpCode URP");
    }

    private static void readKdoOverwrite(RedoByteReader byteReader, RedoLogRecord record,
                                         int fieldPosition, int fieldSize) {
        requireSize(record, fieldSize, 48, "kdo OpCode ORP");
        int absolutePosition = record.dataOffset() + fieldPosition;
        record.fb = record.data()[absolutePosition + 16] & 0xFF;
        record.cc = record.data()[absolutePosition + 18] & 0xFF;
        record.sizeDelt = byteReader.readUnsignedShort(record.data(), absolutePosition + 40);
        record.slot = byteReader.readUnsignedShort(record.data(), absolutePosition + 42);
        readNullBitmap(record, fieldPosition + 45, fieldSize, 45, "kdo OpCode ORP");
    }

    private static void readKdoQuickMultiRow(RedoLogRecord record,
                                             int fieldPosition, int fieldSize) {
        requireSize(record, fieldSize, 24, "kdo OpCode QM");
        int absolutePosition = record.dataOffset() + fieldPosition;
        record.nRow = record.data()[absolutePosition + 18] & 0xFF;
        requireSize(record, fieldSize, 22 + record.nRow * 2, "kdo OpCode QM slots");
        record.slotsDelta = fieldPosition + 20;
    }

    private static void readNullBitmap(RedoLogRecord record, int nullsDelta,
                                       int fieldSize, int bitmapOffset, String fieldName) {
        int nullBytes = (record.cc + 7) / 8;
        if (fieldSize - bitmapOffset < nullBytes) {
            throw new RedoLogException(50061, "too short field " + fieldName
                    + " for nulls: " + fieldSize
                    + " offset: " + record.fileOffset);
        }

        record.nullsDelta = nullsDelta;
        record.ccData = 0;
        for (int column = 0; column < record.cc; column++) {
            if (!isColumnNull(record, column)) {
                record.ccData = column + 1;
            }
        }
    }

    public static void requireSize(RedoLogRecord record, int fieldSize,
                                   int minimumSize, String fieldName) {
        if (fieldSize < minimumSize) {
            throw new RedoLogException(50061, "too short field " + fieldName + ": "
                    + fieldSize + " offset: " + record.fileOffset);
        }
    }
}
