/*
 * Java translation derived from KDLI field parsing in
 * OpenLogReplicator src/parser/OpCode.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;

import java.util.Arrays;

public final class RedoKdliDecoder {
    public static final int OP_BEFORE_IMAGE = 0x06;

    private static final int CODE_INFO = 0x01;
    private static final int CODE_LOAD_DATA = 0x04;
    private static final int CODE_ZERO = 0x05;
    private static final int CODE_FILL = 0x06;
    private static final int CODE_LMAP = 0x07;
    private static final int CODE_LMAPX = 0x08;
    private static final int CODE_SUPPLEMENTAL_LOG = 0x09;
    private static final int CODE_FPLOAD = 0x0B;
    private static final int CODE_LOAD_LHB = 0x0C;
    private static final int CODE_ALMAP = 0x0D;
    private static final int CODE_LOAD_ITREE = 0x0F;
    private static final int CODE_IMAP = 0x10;

    private final RedoByteReader byteReader;

    public RedoKdliDecoder(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public void readCommon(RedoLogRecord record, int fieldPosition, int fieldSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 12, "kdli common");
        int absolutePosition = record.dataOffset() + fieldPosition;
        record.opc = record.data()[absolutePosition] & 0xFF;
        record.dba = byteReader.readUnsignedInt(record.data(), absolutePosition + 8);
    }

    public void read(RedoLogRecord record, int fieldPosition, int fieldSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 1, "kdli");
        int code = record.data()[record.dataOffset() + fieldPosition] & 0xFF;
        switch (code) {
            case CODE_INFO:
                RedoOpCodeSupport.requireSize(record, fieldSize, 17, "kdli info");
                record.lobId = readLobId(record, fieldPosition + 1);
                break;
            case CODE_LOAD_DATA:
                RedoOpCodeSupport.requireSize(record, fieldSize, 56, "kdli load data");
                record.lobId = readLobId(record, fieldPosition + 12);
                record.lobPageNo = RedoLogRecord.INVALID_LOB_PAGE_NO;
                break;
            case CODE_ZERO:
                RedoOpCodeSupport.requireSize(record, fieldSize, 6, "kdli zero");
                break;
            case CODE_FILL:
                readFill(record, fieldPosition, fieldSize, code);
                break;
            case CODE_LMAP:
            case CODE_LMAPX:
            case CODE_IMAP:
                readMap(record, fieldPosition, fieldSize, code, 8);
                break;
            case CODE_SUPPLEMENTAL_LOG:
                readSupplementalLog(record, fieldPosition, fieldSize);
                break;
            case CODE_FPLOAD:
                readFastPathLoad(record, fieldPosition, fieldSize);
                break;
            case CODE_LOAD_LHB:
                readLoadLhb(record, fieldPosition, fieldSize);
                break;
            case CODE_ALMAP:
                readMap(record, fieldPosition, fieldSize, code, 12);
                break;
            case CODE_LOAD_ITREE:
                RedoOpCodeSupport.requireSize(record, fieldSize, 40, "kdli load itree");
                record.lobId = readLobId(record, fieldPosition + 12);
                record.lobPageNo = RedoLogRecord.INVALID_LOB_PAGE_NO;
                break;
            default:
                break;
        }
    }

    public void readDataLoad(RedoLogRecord record, int fieldPosition, int fieldSize) {
        record.lobData = fieldPosition;
        record.lobDataSize = fieldSize;
    }

    private void readFill(RedoLogRecord record, int fieldPosition, int fieldSize, int code) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 8, "kdli fill");
        int absolutePosition = record.dataOffset() + fieldPosition;
        record.indKeyDataCode = code;
        record.lobOffset = byteReader.readUnsignedShort(record.data(), absolutePosition + 2);
        record.lobData = fieldPosition + 8;
        record.lobDataSize = byteReader.readUnsignedShort(record.data(), absolutePosition + 6);
        RedoOpCodeSupport.requireSize(
                record, fieldSize - 8, record.lobDataSize, "kdli fill data");
    }

    private static void readMap(RedoLogRecord record, int fieldPosition,
                                int fieldSize, int code, int minimumSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, minimumSize, "kdli map");
        record.indKeyDataCode = code;
        record.indKeyData = fieldPosition;
        record.indKeyDataSize = fieldSize;
    }

    private void readSupplementalLog(RedoLogRecord record,
                                     int fieldPosition, int fieldSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 24, "kdli supplemental log");
        int absolutePosition = record.dataOffset() + fieldPosition;
        int undoSegment = byteReader.readUnsignedShort(record.data(), absolutePosition + 4);
        int slot = byteReader.readUnsignedShort(record.data(), absolutePosition + 6);
        long sequence = byteReader.readUnsignedInt(record.data(), absolutePosition + 8);
        record.xid = Xid.of(undoSegment, slot, sequence);
        record.obj = byteReader.readUnsignedInt(record.data(), absolutePosition + 12);
        record.col = byteReader.readUnsignedShort(record.data(), absolutePosition + 18);
    }

    private void readFastPathLoad(RedoLogRecord record, int fieldPosition, int fieldSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 28, "kdli fpload");
        int absolutePosition = record.dataOffset() + fieldPosition;
        int undoSegment = byteReader.readUnsignedShort(record.data(), absolutePosition + 16);
        int slot = byteReader.readUnsignedShort(record.data(), absolutePosition + 18);
        long sequence = byteReader.readUnsignedInt(record.data(), absolutePosition + 20);
        record.xid = Xid.of(undoSegment, slot, sequence);
        record.dataObj = byteReader.readUnsignedInt(record.data(), absolutePosition + 24);
    }

    private void readLoadLhb(RedoLogRecord record, int fieldPosition, int fieldSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 112, "kdli load lhb");
        int absolutePosition = record.dataOffset() + fieldPosition;
        record.lobId = readLobId(record, fieldPosition + 12);
        record.lobPageNo = RedoLogRecord.INVALID_LOB_PAGE_NO;
        record.dba0 = byteReader.readUnsignedInt(record.data(), absolutePosition + 64);
        record.dba1 = byteReader.readUnsignedInt(record.data(), absolutePosition + 68);
        record.dba2 = byteReader.readUnsignedInt(record.data(), absolutePosition + 72);
        record.dba3 = byteReader.readUnsignedInt(record.data(), absolutePosition + 76);
    }

    private static LobId readLobId(RedoLogRecord record, int position) {
        int absolutePosition = record.dataOffset() + position;
        byte[] value = Arrays.copyOfRange(
                record.data(), absolutePosition, absolutePosition + LobId.LENGTH);
        return LobId.of(value);
    }
}
