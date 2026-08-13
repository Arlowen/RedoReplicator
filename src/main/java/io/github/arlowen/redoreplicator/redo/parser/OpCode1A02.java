/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode1A02.h
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

public final class OpCode1A02 {
    private final RedoByteReader byteReader;
    private final RedoKdliDecoder kdliDecoder;

    public OpCode1A02(RedoByteReader byteReader) {
        this.byteReader = byteReader;
        kdliDecoder = new RedoKdliDecoder(byteReader);
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x1A0201);
        fields.next();
        RedoOpCodeSupport.readKtbRedo(
                byteReader, record, fields.fieldPosition(), fields.fieldSize());

        fields.next();
        kdliDecoder.readCommon(record, fields.fieldPosition(), fields.fieldSize());
        fields.next();
        kdliDecoder.read(record, fields.fieldPosition(), fields.fieldSize());
        if (fields.nextOptional()) {
            kdliDecoder.read(record, fields.fieldPosition(), fields.fieldSize());
        }
    }
}
