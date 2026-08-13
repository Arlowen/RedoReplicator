/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode1301.h
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
import io.github.arlowen.redoreplicator.redo.common.RedoFieldCursor;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

import java.util.Arrays;

public final class OpCode1301 {
    private final RedoByteReader byteReader;

    public OpCode1301(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x130101);
        fields.next();
        RedoOpCodeSupport.requireSize(record, fields.fieldSize(), 36, "19.1.1");
        int absolutePosition = record.dataOffset() + fields.fieldPosition();
        record.dataObj = byteReader.readUnsignedInt(record.data(), absolutePosition);
        record.recordDataObj = record.dataObj;
        byte[] lobId = Arrays.copyOfRange(
                record.data(), absolutePosition + 4, absolutePosition + 4 + LobId.LENGTH);
        record.lobId = LobId.of(lobId);
        record.lobPageNo = byteReader.readUnsignedInt(record.data(), absolutePosition + 24);
        record.lobData = fields.fieldPosition() + 36;
        record.lobDataSize = fields.fieldSize() - 36;
        fields.next();
    }
}
