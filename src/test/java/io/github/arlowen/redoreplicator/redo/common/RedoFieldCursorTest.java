/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoFieldCursorTest {
    private final RedoByteReader byteReader = new RedoByteReader(ByteOrder.LITTLE_ENDIAN);

    @Test
    void traversesAndSkipsEmptyFieldsUsingUpstreamAlignment() {
        RedoLogRecord record = recordWithFieldSizes(3, 0, 5);
        RedoFieldCursor cursor = new RedoFieldCursor(byteReader, record, 91);

        assertTrue(cursor.nextOptional());
        assertEquals(1, cursor.fieldNumber());
        assertEquals(16, cursor.fieldPosition());
        assertEquals(3, cursor.fieldSize());

        cursor.skipEmptyFields();
        assertEquals(2, cursor.fieldNumber());
        assertEquals(20, cursor.fieldPosition());
        assertEquals(0, cursor.fieldSize());

        cursor.next();
        assertEquals(3, cursor.fieldNumber());
        assertEquals(20, cursor.fieldPosition());
        assertEquals(5, cursor.fieldSize());
        assertFalse(cursor.nextOptional());
    }

    @Test
    void reportsMissingAndOutOfBoundsFieldsWithUpstreamCodes() {
        RedoLogRecord record = recordWithFieldSizes(3);
        RedoFieldCursor cursor = new RedoFieldCursor(byteReader, record, 92);
        cursor.next();

        RedoLogException missing = assertThrows(RedoLogException.class, cursor::next);
        assertEquals(50006, missing.getErrorCode());

        record.fieldPos = 63;
        RedoFieldCursor outOfBoundsCursor = new RedoFieldCursor(byteReader, record, 93);
        RedoLogException outOfBounds = assertThrows(RedoLogException.class, outOfBoundsCursor::next);
        assertEquals(50007, outOfBounds.getErrorCode());
    }

    @Test
    void preservesOptionalAndEmptyFieldErrorCodes() {
        RedoLogRecord optionalRecord = recordWithFieldSizes(3);
        optionalRecord.fieldPos = 63;
        RedoFieldCursor optionalCursor = new RedoFieldCursor(byteReader, optionalRecord, 94);
        RedoLogException optionalError = assertThrows(RedoLogException.class, optionalCursor::nextOptional);
        assertEquals(50005, optionalError.getErrorCode());

        RedoLogRecord emptyRecord = recordWithFieldSizes(0);
        emptyRecord.fieldPos = 65;
        RedoFieldCursor emptyCursor = new RedoFieldCursor(byteReader, emptyRecord, 95);
        RedoLogException emptyError = assertThrows(RedoLogException.class, emptyCursor::skipEmptyFields);
        assertEquals(50008, emptyError.getErrorCode());
    }

    private RedoLogRecord recordWithFieldSizes(int... sizes) {
        byte[] data = new byte[64];
        for (int index = 0; index < sizes.length; index++) {
            int offset = (index + 1) * 2;
            data[offset] = (byte) sizes[index];
            data[offset + 1] = (byte) (sizes[index] >>> 8);
        }

        RedoLogRecord record = new RedoLogRecord();
        record.attachData(data, 0, data.length);
        record.fieldCnt = sizes.length;
        record.fieldPos = 16;
        record.fieldSizesDelta = 0;
        return record;
    }
}
