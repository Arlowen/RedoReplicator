/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode0B0B.h
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

public final class OpCode0B0B {
    private final RedoByteReader byteReader;

    public OpCode0B0B(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = RedoOpCodeSupport.readRowHeaders(
                byteReader, record, 0x0B0B01);
        if (fields == null || !fields.nextOptional()) {
            return;
        }

        record.rowSizesDelta = fields.fieldPosition();
        RedoOpCodeSupport.requireSize(
                record, fields.fieldSize(), record.nRow * 2, "11.11.3");
        if (!fields.nextOptional()) {
            return;
        }
        record.rowData = fields.fieldNumber();
    }
}
