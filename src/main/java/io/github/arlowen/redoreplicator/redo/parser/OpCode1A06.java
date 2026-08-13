/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode1A06.h
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

public final class OpCode1A06 {
    private final RedoByteReader byteReader;
    private final RedoKdliDecoder kdliDecoder;

    public OpCode1A06(RedoByteReader byteReader) {
        this.byteReader = byteReader;
        kdliDecoder = new RedoKdliDecoder(byteReader);
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x1A0601);
        fields.next();
        RedoOpCodeSupport.requireSize(record, fields.fieldSize(), 12, "26.6.1");
        kdliDecoder.readCommon(record, fields.fieldPosition(), fields.fieldSize());

        fields.next();
        RedoOpCodeSupport.requireSize(record, fields.fieldSize(), 32, "26.6.2");
        record.recordDataObj = byteReader.readUnsignedInt(
                record.data(), record.dataOffset() + fields.fieldPosition() + 24);
        kdliDecoder.read(record, fields.fieldPosition(), fields.fieldSize());

        fields.next();
        kdliDecoder.read(record, fields.fieldPosition(), fields.fieldSize());
        if (!fields.nextOptional()) {
            return;
        }

        if (record.opc == RedoKdliDecoder.OP_BEFORE_IMAGE) {
            kdliDecoder.readDataLoad(record, fields.fieldPosition(), fields.fieldSize());
            if (!fields.nextOptional()) {
                return;
            }
        }
        kdliDecoder.read(record, fields.fieldPosition(), fields.fieldSize());
    }
}
