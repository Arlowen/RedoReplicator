/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode0B05.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoFieldCursor;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

public final class OpCode0B05 {
    private final RedoByteReader byteReader;

    public OpCode0B05(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public void process(RedoLogRecord record) {
        RedoFieldCursor fields = RedoOpCodeSupport.readRowHeaders(
                byteReader, record, 0x0B0501);
        if (fields == null || !fields.nextOptional()) {
            return;
        }

        boolean hasColumnNumbers = fields.fieldSize() > 0 && record.cc > 0;
        if (hasColumnNumbers) {
            RedoOpCodeSupport.requireSize(
                    record, fields.fieldSize(), record.cc * 2, "11.5 column numbers");
            record.colNumsDelta = fields.fieldPosition();
        }

        if ((record.flags & RedoOpCodeSupport.FLAGS_KDO_KDOM2) != 0) {
            fields.next();
            record.rowData = fields.fieldNumber();
            return;
        }
        if (!hasColumnNumbers) {
            return;
        }

        record.rowData = fields.fieldNumber() + 1;
        for (int column = 0; column < record.cc; column++) {
            if (fields.fieldNumber() >= record.fieldCnt) {
                break;
            }
            if (column < record.ccData) {
                fields.next();
            }
            if (fields.fieldSize() > 0
                    && RedoOpCodeSupport.isColumnNull(record, column)
                    && column < record.ccData) {
                throw new RedoLogException(50061, "too short field 11.5."
                        + fields.fieldNumber() + ": " + fields.fieldSize()
                        + " offset: " + record.fileOffset);
            }
        }
    }
}
