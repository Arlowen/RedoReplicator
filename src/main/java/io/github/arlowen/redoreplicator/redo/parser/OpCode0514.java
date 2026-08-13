/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode0514.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoFieldCursor;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

import java.util.Map;

public final class OpCode0514 extends OpCode0513 {
    public OpCode0514(RedoByteReader byteReader, long redoVersion) {
        super(byteReader, redoVersion);
    }

    @Override
    public void process(RedoLogRecord record, Map<Attribute, String> attributes) {
        if (attributes == null) {
            return;
        }

        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x051401);
        fields.next();
        readSessionSerial(record, fields.fieldPosition(), fields.fieldSize(), attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.TRANSACTION_NAME, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readFlags(record, fields.fieldPosition(), fields.fieldSize(), attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readVersion(record, fields.fieldPosition(), fields.fieldSize(), attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAuditSessionId(record, fields.fieldPosition(), fields.fieldSize(), attributes);
        if (!fields.nextOptional()) {
            return;
        }

        // Field 6 is reserved in the 5.14 protocol layout.
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.CLIENT_ID, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.LOGIN_USER_NAME, attributes);
    }
}
