/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode0A02.h
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

public final class OpCode0A02 {
    private final RedoByteReader byteReader;

    public OpCode0A02(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x0A0201);
        fields.next();
        RedoOpCodeSupport.readKtbRedo(
                byteReader, record, fields.fieldPosition(), fields.fieldSize());

        if (!fields.nextOptional()) {
            return;
        }
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
        fields.nextOptional();
    }
}
