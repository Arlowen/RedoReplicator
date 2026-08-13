/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedoLogRecordTest {
    @Test
    void attachesAViewOverRedoData() {
        byte[] data = {0, 1, 2, 3, 4, 5};
        RedoLogRecord record = new RedoLogRecord();

        record.attachData(data, 2, 3);

        assertEquals(3, record.size);
        assertEquals(2, record.dataOffset());
        assertEquals(3, record.byteAt(1));
        assertThrows(IndexOutOfBoundsException.class, () -> record.byteAt(3));
        assertThrows(IndexOutOfBoundsException.class, () -> record.attachData(data, 5, 2));
    }

    @Test
    void clearsRecordStateForReuse() {
        RedoLogRecord record = new RedoLogRecord();
        record.attachData(new byte[8], 0, 8);
        record.xid = Xid.of(1, 2, 3);
        record.obj = 42;
        record.compressed = true;

        record.clear();

        assertNull(record.data());
        assertEquals(Xid.zero(), record.xid);
        assertEquals(0, record.obj);
        assertFalse(record.compressed);
        assertEquals(LobId.zero(), record.lobId);
    }
}
