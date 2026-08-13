/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode1801.h
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

public final class OpCode1801 {
    private final RedoByteReader byteReader;

    public OpCode1801(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x180101);
        fields.next();
        RedoOpCodeSupport.requireSize(record, fields.fieldSize(), 18, "24.1.1");
        int absolutePosition = record.dataOffset() + fields.fieldPosition();
        int undoSegment = byteReader.readUnsignedShort(record.data(), absolutePosition + 4);
        int slot = byteReader.readUnsignedShort(record.data(), absolutePosition + 6);
        long sequence = byteReader.readUnsignedInt(record.data(), absolutePosition + 8);
        record.xid = Xid.of(undoSegment, slot, sequence);
        int ddlType = byteReader.readUnsignedShort(record.data(), absolutePosition + 16);
        boolean validDdl = isPersistentDdl(ddlType);

        for (int fieldNumber = 2; fieldNumber <= 12; fieldNumber++) {
            if (!fields.nextOptional()) {
                return;
            }
        }
        if (validDdl) {
            RedoOpCodeSupport.requireSize(record, fields.fieldSize(), 4, "24.1.12");
            record.obj = byteReader.readUnsignedInt(
                    record.data(), record.dataOffset() + fields.fieldPosition());
        }
    }

    private static boolean isPersistentDdl(int ddlType) {
        return ddlType != 4 && ddlType != 5 && ddlType != 6
                && ddlType != 8 && ddlType != 9 && ddlType != 10;
    }
}
