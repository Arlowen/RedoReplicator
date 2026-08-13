/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode0502.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoFieldCursor;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;

public final class OpCode0502 {
    private final RedoByteReader byteReader;
    private final long redoVersion;

    public OpCode0502(RedoByteReader byteReader, long redoVersion) {
        this.byteReader = byteReader;
        this.redoVersion = redoVersion;
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x050201);
        fields.next();
        readTransactionHeader(record, fields.fieldPosition(), fields.fieldSize());

        if (redoVersion < RedoLogRecord.REDO_VERSION_12_1 || !fields.nextOptional()) {
            return;
        }
        if (fields.fieldSize() == 4) {
            readPdbId(record, fields.fieldPosition(), fields.fieldSize());
            return;
        }

        RedoOpCodeSupport.requireSize(record, fields.fieldSize(), 36, "kteop");
        if (fields.nextOptional()) {
            readPdbId(record, fields.fieldPosition(), fields.fieldSize());
        }
    }

    private void readTransactionHeader(RedoLogRecord record, int fieldPosition, int fieldSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 32, "ktudh");
        int absolutePosition = record.dataOffset() + fieldPosition;
        int slot = byteReader.readUnsignedShort(record.data(), absolutePosition);
        long sequence = byteReader.readUnsignedInt(record.data(), absolutePosition + 4);
        record.xid = Xid.of(record.usn, slot, sequence);
        record.flg = byteReader.readUnsignedShort(record.data(), absolutePosition + 16);
    }

    private void readPdbId(RedoLogRecord record, int fieldPosition, int fieldSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 4, "pdb");
        record.dbId = byteReader.readUnsignedInt(
                record.data(), record.dataOffset() + fieldPosition);
    }
}
