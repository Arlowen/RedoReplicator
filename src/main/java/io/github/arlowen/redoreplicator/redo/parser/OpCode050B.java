/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode050B.h
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

public final class OpCode050B {
    private final RedoByteReader byteReader;

    public OpCode050B(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x050B01);
        fields.next();
        RedoOpCodeSupport.readObjectPair(
                byteReader, record, fields.fieldPosition(), fields.fieldSize(), "5.11");
        RedoOpCodeSupport.readKtuBlock(
                byteReader, record, fields.fieldPosition(), fields.fieldSize());
    }
}
