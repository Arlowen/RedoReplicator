/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode0504.h
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

public final class OpCode0504 {
    private final RedoByteReader byteReader;

    public OpCode0504(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x050401);
        fields.next();
        readCommit(record, fields.fieldPosition(), fields.fieldSize());

        if (fields.nextOptional()
                && (record.flg & RedoOpCodeSupport.FLG_KTUCF_0504) != 0) {
            RedoOpCodeSupport.requireSize(record, fields.fieldSize(), 16, "ktucf");
        }
    }

    private void readCommit(RedoLogRecord record, int fieldPosition, int fieldSize) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 20, "ktucm");
        int absolutePosition = record.dataOffset() + fieldPosition;
        int slot = byteReader.readUnsignedShort(record.data(), absolutePosition);
        long sequence = byteReader.readUnsignedInt(record.data(), absolutePosition + 4);
        record.xid = Xid.of(record.usn, slot, sequence);
        record.flg = record.data()[absolutePosition + 16] & 0xFF;
    }
}
