/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode0501.h
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

public final class OpCode0501 {
    private static final int FLG_MULTIBLOCK_UNDO = 0x0103;

    private final RedoByteReader byteReader;
    private final RedoKdliDecoder kdliDecoder;

    public OpCode0501(RedoByteReader byteReader) {
        this.byteReader = byteReader;
        kdliDecoder = new RedoKdliDecoder(byteReader);
    }

    public void process(RedoLogRecord record) {
        readInitialObjectPair(record);

        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x050112);
        fields.next();
        readUndoBlock(record, fields.fieldPosition(), fields.fieldSize());

        if (!fields.nextOptional()) {
            return;
        }
        RedoOpCodeSupport.readKtuBlock(
                byteReader, record, fields.fieldPosition(), fields.fieldSize());
        if ((record.flg & FLG_MULTIBLOCK_UNDO) != 0) {
            return;
        }

        if (!fields.nextOptional()) {
            return;
        }
        switch (record.opc) {
            case 0x0A16:
                RedoOpCodeSupport.readKtbRedo(
                        byteReader, record, fields.fieldPosition(), fields.fieldSize());
                if (fields.nextOptional()) {
                    processIndexUndo(record, fields);
                }
                break;
            case 0x0B01:
                RedoOpCodeSupport.readKtbRedo(
                        byteReader, record, fields.fieldPosition(), fields.fieldSize());
                if (fields.nextOptional()) {
                    processRowUndo(record, fields);
                }
                break;
            case 0x1A01:
                RedoOpCodeSupport.readKtbRedo(
                        byteReader, record, fields.fieldPosition(), fields.fieldSize());
                if (!fields.nextOptional()) {
                    return;
                }
                kdliDecoder.readCommon(record, fields.fieldPosition(), fields.fieldSize());
                if (fields.nextOptional()) {
                    kdliDecoder.read(record, fields.fieldPosition(), fields.fieldSize());
                }
                break;
            case 0x0E08:
                RedoOpCodeSupport.requireSize(
                        record, fields.fieldSize(), 4, "kteoputrn");
                break;
            default:
                break;
        }
    }

    private void readInitialObjectPair(RedoLogRecord record) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x050101);
        if (!fields.nextOptional() || !fields.nextOptional()) {
            return;
        }
        RedoOpCodeSupport.readObjectPair(
                byteReader, record, fields.fieldPosition(), fields.fieldSize(), "5.1.2");
    }

    private void readUndoBlock(RedoLogRecord record, int fieldPosition, int fieldSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 20, "ktudb");
        int absolutePosition = record.dataOffset() + fieldPosition;
        int undoSegment = byteReader.readUnsignedShort(record.data(), absolutePosition + 8);
        int slot = byteReader.readUnsignedShort(record.data(), absolutePosition + 10);
        long sequence = byteReader.readUnsignedInt(record.data(), absolutePosition + 12);
        record.xid = Xid.of(undoSegment, slot, sequence);
    }

    private void processIndexUndo(RedoLogRecord record, RedoFieldCursor fields) {
        RedoOpCodeSupport.requireSize(record, fields.fieldSize(), 20, "kdilk");
        if (!fields.nextOptional()) {
            return;
        }
        record.indKey = fields.fieldPosition();
        record.indKeySize = fields.fieldSize();

        if (!fields.nextOptional()) {
            return;
        }
        record.indKeyData = fields.fieldPosition();
        record.indKeyDataSize = fields.fieldSize();

        if (!fields.nextOptional()) {
            return;
        }
        fields.nextOptional();
    }

    private void processRowUndo(RedoLogRecord record, RedoFieldCursor fields) {
        RedoOpCodeSupport.readKdoOperation(
                byteReader, record, fields.fieldPosition(), fields.fieldSize());
        int operation = record.op & 0x1F;
        if (operation == RedoLogRecord.OP_URP) {
            processUpdateUndo(record, fields);
            return;
        }
        if (operation == RedoLogRecord.OP_DRP) {
            skipRowDependencies(record, fields, false);
            readSupplementalLog(record, fields);
            return;
        }
        if (operation == RedoLogRecord.OP_IRP || operation == RedoLogRecord.OP_ORP) {
            processInsertOrOverwriteUndo(record, fields);
            return;
        }
        if (operation == RedoLogRecord.OP_QMI) {
            fields.next();
            record.rowSizesDelta = fields.fieldPosition();
            fields.next();
            record.rowData = fields.fieldNumber();
            return;
        }
        if (operation == RedoLogRecord.OP_LMN
                || operation == RedoLogRecord.OP_LKR
                || operation == RedoLogRecord.OP_CFA) {
            readSupplementalLog(record, fields);
        }
    }

    private void processUpdateUndo(RedoLogRecord record, RedoFieldCursor fields) {
        fields.next();
        if (fields.fieldSize() > 0 && record.cc > 0) {
            record.colNumsDelta = fields.fieldPosition();
        }

        if ((record.flags & RedoOpCodeSupport.FLAGS_KDO_KDOM2) != 0) {
            fields.next();
            record.rowData = fields.fieldPosition();
            return;
        }

        record.rowData = fields.fieldNumber() + 1;
        for (int column = 0; column < record.cc; column++) {
            if (!RedoOpCodeSupport.isColumnNull(record, column)) {
                fields.skipEmptyFields();
                if (fields.fieldNumber() >= record.fieldCnt) {
                    return;
                }
                fields.next();
            }
        }

        skipRowDependencies(record, fields, true);
        readSupplementalLog(record, fields);
    }

    private void processInsertOrOverwriteUndo(RedoLogRecord record,
                                               RedoFieldCursor fields) {
        if (record.cc > 0) {
            record.rowData = fields.fieldNumber() + 1;
            if (fields.fieldNumber() >= record.fieldCnt) {
                return;
            }
            fields.next();

            if (fields.fieldSize() == record.sizeDelt && record.cc > 1) {
                record.compressed = true;
            } else {
                for (int column = 0; column < record.cc; column++) {
                    if (column > 0) {
                        if (fields.fieldNumber() >= record.fieldCnt) {
                            return;
                        }
                        fields.next();
                    }
                    if (fields.fieldSize() > 0
                            && RedoOpCodeSupport.isColumnNull(record, column)) {
                        throw new RedoLogException(50061,
                                "too short field for nulls: " + fields.fieldSize()
                                        + " offset: " + record.fileOffset);
                    }
                }
            }
        }

        skipRowDependencies(record, fields, false);
        readSupplementalLog(record, fields);
    }

    private void skipRowDependencies(RedoLogRecord record, RedoFieldCursor fields,
                                     boolean skipEmptyFields) {
        if ((record.op & RedoLogRecord.OP_ROWDEPENDENCIES) == 0) {
            return;
        }
        if (skipEmptyFields) {
            fields.skipEmptyFields();
        }
        fields.next();
    }

    private void readSupplementalLog(RedoLogRecord record, RedoFieldCursor fields) {
        fields.skipEmptyFields();
        if (!fields.nextOptional()) {
            return;
        }

        RedoOpCodeSupport.requireSize(
                record, fields.fieldSize(), 20, "supplemental log");
        int absolutePosition = record.dataOffset() + fields.fieldPosition();
        record.suppLogFb = record.data()[absolutePosition + 1] & 0xFF;
        record.suppLogCC = byteReader.readUnsignedShort(record.data(), absolutePosition + 2);
        record.suppLogBefore = byteReader.readUnsignedShort(record.data(), absolutePosition + 6);
        record.suppLogAfter = byteReader.readUnsignedShort(record.data(), absolutePosition + 8);
        if (fields.fieldSize() >= 26) {
            record.suppLogBdba = byteReader.readUnsignedInt(
                    record.data(), absolutePosition + 20);
            record.suppLogSlot = byteReader.readUnsignedShort(
                    record.data(), absolutePosition + 24);
        } else {
            record.suppLogBdba = record.bdba;
            record.suppLogSlot = record.slot;
        }

        if (!fields.nextOptional()) {
            return;
        }
        record.suppLogNumsDelta = fields.fieldPosition();
        if (!fields.nextOptional()) {
            return;
        }
        record.suppLogLenDelta = fields.fieldPosition();
        record.suppLogRowData = fields.fieldNumber() + 1;
        for (int column = 0; column < record.suppLogCC; column++) {
            fields.next();
        }
    }
}
