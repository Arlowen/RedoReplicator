/*
 * Java translation derived from redo vector parsing in
 * OpenLogReplicator src/parser/Parser.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Seq;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class RedoVectorParser {
    private final RedoByteReader byteReader;
    private final RedoRecordHeaderParser headerParser;
    private final long redoVersion;
    private final int blockSize;

    public RedoVectorParser(ByteOrder byteOrder, long redoVersion, int blockSize) {
        byteReader = new RedoByteReader(byteOrder);
        headerParser = new RedoRecordHeaderParser(byteReader);
        this.redoVersion = redoVersion;
        this.blockSize = blockSize;
        if (blockSize != 512 && blockSize != 1024 && blockSize != 4096) {
            throw new IllegalArgumentException("Unsupported redo block size: " + blockSize);
        }
    }

    public List<RedoLogRecord> parseAll(AssembledRedoRecord assembledRecord,
                                        Seq sequence, RedoTime timestamp, int thread) {
        byte[] data = assembledRecord.data();
        RedoRecordHeader recordHeader = headerParser.parse(data, 0, data.length);
        if (!recordHeader.isValid()) {
            return List.of();
        }

        List<RedoLogRecord> vectors = new ArrayList<>();
        int vectorOffset = recordHeader.headerSize();
        while (vectorOffset < data.length) {
            RedoLogRecord vector = parseVector(assembledRecord, vectorOffset,
                    vectors.size() + 1L, sequence, timestamp, thread);
            vectors.add(vector);
            vectorOffset += vector.size;
        }
        return List.copyOf(vectors);
    }

    private RedoLogRecord parseVector(AssembledRedoRecord assembledRecord, int vectorOffset,
                                      long vectorNumber, Seq sequence,
                                      RedoTime timestamp, int thread) {
        byte[] data = assembledRecord.data();
        RedoLogRecord vector = readVectorHeader(data, vectorOffset);
        int vectorSize = readFieldLayout(data, vectorOffset, vector);
        vector.attachData(data, vectorOffset, vectorSize);

        LwnMember member = assembledRecord.member();
        vector.vectorNo = vectorNumber;
        vector.sequence = sequence;
        vector.scn = member.scn();
        vector.subScn = member.subScn();
        vector.timestamp = timestamp;
        vector.fileOffset = FileOffset.fromBlock(member.block(), blockSize)
                .plus(member.pageOffset() + vectorOffset);
        vector.recordObj = 0xFFFF_FFFFL;
        vector.recordDataObj = 0xFFFF_FFFFL;
        vector.thread = thread;
        return vector;
    }

    private RedoLogRecord readVectorHeader(byte[] data, int vectorOffset) {
        int fieldOffset = 24;
        if (redoVersion >= RedoLogRecord.REDO_VERSION_12_1) {
            fieldOffset = 32;
        }
        if (vectorOffset < 0 || vectorOffset > data.length - fieldOffset - 2) {
            throw new RedoLogException(50046, "position of field list outside of record: "
                    + vectorOffset + "/" + data.length);
        }

        RedoLogRecord vector = new RedoLogRecord();
        vector.opCode = (data[vectorOffset] & 0xFF) << 8 | (data[vectorOffset + 1] & 0xFF);
        vector.cls = byteReader.readUnsignedShort(data, vectorOffset + 2);
        vector.afn = (int) byteReader.readUnsignedInt(data, vectorOffset + 4) & 0xFFFF;
        vector.dba = byteReader.readUnsignedInt(data, vectorOffset + 8);
        vector.scnRecord = byteReader.readScn(data, vectorOffset + 12);
        vector.rbl = 0;
        vector.seq = data[vectorOffset + 20] & 0xFF;
        vector.typ = data[vectorOffset + 21] & 0xFF;
        vector.usn = -1;
        if (vector.cls >= 15) {
            vector.usn = (vector.cls - 15) / 2;
        }
        if ((vector.typ & RedoLogRecord.TYP_ENCRYPTED_TABLESPACE) != 0) {
            vector.typ &= ~RedoLogRecord.TYP_ENCRYPTED_TABLESPACE;
            vector.encryptedTablespace = true;
        }
        if (fieldOffset == 32) {
            vector.conId = byteReader.readUnsignedShort(data, vectorOffset + 24);
            vector.flgRecord = byteReader.readUnsignedShort(data, vectorOffset + 28);
        }
        vector.fieldSizesDelta = fieldOffset;
        return vector;
    }

    private int readFieldLayout(byte[] data, int vectorOffset, RedoLogRecord vector) {
        int recordSize = data.length;
        int fieldListOffset = vectorOffset + vector.fieldSizesDelta;
        int fieldListLength = byteReader.readUnsignedShort(data, fieldListOffset);
        if (fieldListLength < 2 || (fieldListLength & 1) != 0
                || fieldListOffset > recordSize - fieldListLength) {
            throw new RedoLogException(50046, "invalid field size list: " + fieldListLength);
        }

        vector.fieldCnt = (fieldListLength - 2) / 2;
        int paddedFieldListLength = (fieldListLength + 2) & 0xFFFC;
        vector.fieldPos = vector.fieldSizesDelta + paddedFieldListLength;
        long vectorSize = vector.fieldPos;
        if (vectorOffset + vector.fieldPos > recordSize) {
            throw new RedoLogException(50046, "fields outside of record: "
                    + vector.fieldPos + "/" + recordSize);
        }

        for (int field = 1; field <= vector.fieldCnt; field++) {
            int fieldSize = byteReader.readUnsignedShort(data, fieldListOffset + field * 2);
            vectorSize += (fieldSize + 3) & 0xFFFC;
            if (vectorOffset + vectorSize > recordSize) {
                throw new RedoLogException(50046, "position of field list outside of record: "
                        + field + "/" + vector.fieldCnt + ", size: " + vectorSize
                        + ", record: " + recordSize);
            }
        }
        if (vector.fieldPos > vectorSize) {
            throw new RedoLogException(50046, "incomplete record, offset: "
                    + vector.fieldPos + ", size: " + vectorSize);
        }
        return (int) vectorSize;
    }
}
