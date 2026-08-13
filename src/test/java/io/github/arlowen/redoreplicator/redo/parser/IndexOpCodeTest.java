/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexOpCodeTest {
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);

    @Test
    void decodesLeafInsertKeyAndTransactionIdentity() {
        byte[] key = new byte[]{0x11, 0x22, 0x33};
        byte[] keyData = new byte[]{0x44, 0x55, 0x66, 0x77};
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0A02, 0,
                ktbFastCommit(),
                RedoOpCodeTestSupport.field(6),
                key,
                keyData,
                RedoOpCodeTestSupport.field(2));

        assertTrue(dispatcher.dispatch(record));

        assertEquals(Xid.of(0x1234, 0x5678, 0x9ABC_DEF0L), record.xid);
        assertEquals(key.length, record.indKeySize);
        assertEquals(keyData.length, record.indKeyDataSize);
        assertArrayEquals(key, bytes(record, record.indKey, record.indKeySize));
        assertArrayEquals(keyData,
                bytes(record, record.indKeyData, record.indKeyDataSize));
    }

    @Test
    void decodesKeyDataUpdate() {
        byte[] keyData = new byte[]{0x01, 0x23, 0x45};
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0A12, 0,
                ktbFastCommit(),
                RedoOpCodeTestSupport.field(6),
                keyData);

        assertTrue(dispatcher.dispatch(record));

        assertEquals(Xid.of(0x1234, 0x5678, 0x9ABC_DEF0L), record.xid);
        assertEquals(keyData.length, record.indKeyDataSize);
        assertArrayEquals(keyData,
                bytes(record, record.indKeyData, record.indKeyDataSize));
    }

    @Test
    void capturesSingleRowKeyFromNewLeafBlock() {
        byte[] key = new byte[]{0x10, 0x20, 0x30, 0x40};
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0A08, 0,
                ktbNoOperation(),
                RedoOpCodeTestSupport.field(16),
                RedoOpCodeTestSupport.field(4),
                key);

        assertTrue(dispatcher.dispatch(record));

        assertEquals(key.length, record.indKeySize);
        assertArrayEquals(key, bytes(record, record.indKey, record.indKeySize));
    }

    @Test
    void stopsSplitLeafParsingOnShortNextBlockField() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0A08, 0,
                RedoOpCodeTestSupport.field(0),
                RedoOpCodeTestSupport.field(3),
                RedoOpCodeTestSupport.field(4),
                new byte[]{0x01, 0x02});

        assertTrue(dispatcher.dispatch(record));

        assertEquals(0, record.indKey);
        assertEquals(0, record.indKeySize);
    }

    @Test
    void validatesKtbOperationSpecificLengths() {
        assertKtbLengthRejected(0x01, 23);
        assertKtbLengthRejected(0x02, 15);
        assertKtbLengthRejected(0x04, 31);
    }

    private static byte[] ktbFastCommit() {
        byte[] field = RedoOpCodeTestSupport.field(24);
        field[0] = 0x01;
        field[1] = 0x08;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 8, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 10, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 12, 0x9ABC_DEF0L, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] ktbNoOperation() {
        byte[] field = RedoOpCodeTestSupport.field(8);
        field[0] = 0x06;
        return field;
    }

    private static byte[] bytes(RedoLogRecord record, int position, int size) {
        int start = record.dataOffset() + position;
        return Arrays.copyOfRange(record.data(), start, start + size);
    }

    private void assertKtbLengthRejected(int operation, int size) {
        byte[] field = RedoOpCodeTestSupport.field(size);
        field[0] = (byte) operation;
        field[1] = 0x08;
        RedoLogRecord record = RedoOpCodeTestSupport.record(0x0A12, 0, field);

        RedoLogException error = assertThrows(
                RedoLogException.class, () -> dispatcher.dispatch(record));
        assertEquals(50061, error.getErrorCode());
    }
}
