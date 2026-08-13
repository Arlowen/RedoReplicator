/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoLobIndexResolverTest {
    private static final LobId LOB_ID = LobId.of(
            new byte[]{0, 0, 0, 1, 2, 3, 4, 5, 6, 7});

    private final RedoLobIndexResolver resolver =
            new RedoLobIndexResolver();

    @Test
    void derivesClassicLobIndexRecordFields() {
        RedoLogRecord insert = record(0x0A02, indexKey(7));
        insert.indKeySize = 16;
        assertTrue(resolver.resolve(new RedoLogRecord(), insert));
        assertEquals(LOB_ID, insert.lobId);
        assertEquals(7, insert.lobPageNo);

        byte[] initialization = new byte[51];
        initialization[1] = 0x01;
        initialization[2] = 0x01;
        initialization[35] = 10;
        System.arraycopy(LOB_ID.bytes(), 0,
                initialization, 36, LobId.LENGTH);
        initialization[46] = 4;
        writeUnsignedInt(initialization, 47, 8);
        RedoLogRecord init = record(0x0A08, initialization);
        init.indKey = 1;
        init.indKeySize = 50;
        assertTrue(resolver.resolve(new RedoLogRecord(), init));
        assertEquals(LOB_ID, init.lobId);
        assertEquals(8, init.lobPageNo);
        assertEquals(3, init.indKeyData);
        assertEquals(32, init.indKeyDataSize);

        RedoLogRecord undo = record(0x0501, indexKey(0));
        undo.indKeySize = 16;
        byte[] sizeData = new byte[10];
        writeUnsignedInt(sizeData, 4, 2);
        sizeData[9] = 3;
        RedoLogRecord update = record(0x0A12, sizeData);
        update.indKeyDataSize = sizeData.length;
        assertTrue(resolver.resolve(undo, update));
        assertEquals(LOB_ID, update.lobId);
        assertEquals(2, update.lobSizePages);
        assertEquals(3, update.lobSizeRest);
    }

    @Test
    void ignoresKeysOutsideTheClassicLobIndexShape() {
        byte[] key = indexKey(0);
        key[0] = 9;
        RedoLogRecord record = record(0x0A02, key);
        record.indKeySize = key.length;

        assertFalse(resolver.resolve(new RedoLogRecord(), record));
    }

    private static RedoLogRecord record(int opCode, byte[] data) {
        RedoLogRecord record = new RedoLogRecord();
        record.attachData(data, 0, data.length);
        record.opCode = opCode;
        return record;
    }

    private static byte[] indexKey(long pageNumber) {
        byte[] key = new byte[16];
        key[0] = 10;
        System.arraycopy(LOB_ID.bytes(), 0, key, 1, LobId.LENGTH);
        key[11] = 4;
        writeUnsignedInt(key, 12, pageNumber);
        return key;
    }

    private static void writeUnsignedInt(
            byte[] data, int offset, long value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }
}
