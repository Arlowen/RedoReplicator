/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobAndDdlOpCodeTest {
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);

    @Test
    void decodesDirectLoaderLobPayload() {
        byte[] lobId = lobId();
        byte[] field = RedoOpCodeTestSupport.field(40);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, 0xF100_0001L, ByteOrder.LITTLE_ENDIAN);
        System.arraycopy(lobId, 0, field, 4, lobId.length);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 24, 77, ByteOrder.LITTLE_ENDIAN);
        field[36] = 0x11;
        field[37] = 0x22;
        field[38] = 0x33;
        field[39] = 0x44;
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x1301, 0, field, RedoOpCodeTestSupport.field(0));

        assertTrue(dispatcher.dispatch(record));

        assertEquals(0xF100_0001L, record.dataObj);
        assertEquals(record.dataObj, record.recordDataObj);
        assertEquals(LobId.of(lobId), record.lobId);
        assertEquals(77, record.lobPageNo);
        assertEquals(4, record.lobDataSize);
        assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0x44},
                bytes(record, record.lobData, record.lobDataSize));
    }

    @Test
    void decodesPersistentDdlIdentityAndObject() {
        byte[] header = ddlHeader(3);
        byte[] object = unsignedIntField(0xE200_0002L);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x1801, 0,
                header,
                empty(), empty(), empty(), empty(), empty(),
                empty(), empty(), empty(), empty(), empty(),
                object);

        assertTrue(dispatcher.dispatch(record));

        assertEquals(Xid.of(0x1234, 0x5678, 0x9ABC_DEF0L), record.xid);
        assertEquals(0xE200_0002L, record.obj);
    }

    @Test
    void decodesDdlTypeSequenceAndPayloadFields() {
        byte[] header = ddlHeader(3, 15, 2, 4);
        byte[] schemaOrChunk = new byte[]{0x41, 0x50, 0x50};
        byte[] finalChunk = new byte[]{0x41, 0x4C, 0x54, 0x45, 0x52};
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x1801, 0,
                header,
                schemaOrChunk,
                empty(), empty(), empty(), empty(), empty(),
                finalChunk,
                empty(), empty(), empty(),
                unsignedIntField(0xE200_0002L));

        assertTrue(dispatcher.dispatch(record));

        assertEquals(15, record.ddlType);
        assertEquals(3, record.ddlObjectType);
        assertEquals(2, record.ddlSequence);
        assertEquals(4, record.ddlCount);
        assertArrayEquals(schemaOrChunk,
                bytes(record, record.ddlPayload1, record.ddlPayload1Size));
        assertArrayEquals(finalChunk,
                bytes(record, record.ddlPayload2, record.ddlPayload2Size));
    }

    @Test
    void ignoresTemporaryDdlObject() {
        byte[] header = ddlHeader(4);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x1801, 0,
                header,
                empty(), empty(), empty(), empty(), empty(),
                empty(), empty(), empty(), empty(), empty(),
                unsignedIntField(0xE200_0002L));

        assertTrue(dispatcher.dispatch(record));

        assertEquals(Xid.of(0x1234, 0x5678, 0x9ABC_DEF0L), record.xid);
        assertEquals(0, record.obj);
    }

    @Test
    void decodesKdliFillOperation() {
        byte[] common = kdliCommon(0, 0xF200_0002L);
        byte[] fill = RedoOpCodeTestSupport.field(11);
        fill[0] = 0x06;
        RedoBinaryTestSupport.writeUnsignedShort(
                fill, 2, 15, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                fill, 6, 3, ByteOrder.LITTLE_ENDIAN);
        fill[8] = 1;
        fill[9] = 2;
        fill[10] = 3;
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x1A02, 0, ktbNoOperation(), common, fill);

        assertTrue(dispatcher.dispatch(record));

        assertEquals(0, record.opc);
        assertEquals(0xF200_0002L, record.dba);
        assertEquals(0x06, record.indKeyDataCode);
        assertEquals(15, record.lobOffset);
        assertArrayEquals(new byte[]{1, 2, 3},
                bytes(record, record.lobData, record.lobDataSize));
    }

    @Test
    void decodesKdliBeforeImageAndSupplementalIdentity() {
        byte[] lobId = lobId();
        byte[] info = RedoOpCodeTestSupport.field(32);
        info[0] = 0x01;
        System.arraycopy(lobId, 0, info, 1, lobId.length);
        RedoBinaryTestSupport.writeUnsignedInt(
                info, 24, 0xD300_0003L, ByteOrder.LITTLE_ENDIAN);
        byte[] zero = RedoOpCodeTestSupport.field(6);
        zero[0] = 0x05;
        byte[] beforeImage = new byte[]{0x21, 0x22, 0x23};
        byte[] supplemental = RedoOpCodeTestSupport.field(24);
        supplemental[0] = 0x09;
        RedoBinaryTestSupport.writeUnsignedShort(
                supplemental, 4, 7, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                supplemental, 6, 8, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                supplemental, 8, 9, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                supplemental, 12, 0xC400_0004L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                supplemental, 18, 11, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x1A06, 0,
                kdliCommon(RedoKdliDecoder.OP_BEFORE_IMAGE, 0xF500_0005L),
                info, zero, beforeImage, supplemental);

        assertTrue(dispatcher.dispatch(record));

        assertEquals(0xD300_0003L, record.recordDataObj);
        assertEquals(0xF500_0005L, record.dba);
        assertEquals(LobId.of(lobId), record.lobId);
        assertArrayEquals(beforeImage, bytes(record, record.lobData, record.lobDataSize));
        assertEquals(Xid.of(7, 8, 9), record.xid);
        assertEquals(0xC400_0004L, record.obj);
        assertEquals(11, record.col);
    }

    private static byte[] ddlHeader(int ddlType) {
        byte[] header = RedoOpCodeTestSupport.field(18);
        RedoBinaryTestSupport.writeUnsignedShort(
                header, 4, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                header, 6, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                header, 8, 0x9ABC_DEF0L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                header, 16, ddlType, ByteOrder.LITTLE_ENDIAN);
        return header;
    }

    private static byte[] ddlHeader(int objectType, int statementType,
                                    int sequence, int count) {
        byte[] header = RedoOpCodeTestSupport.field(22);
        RedoBinaryTestSupport.writeUnsignedShort(
                header, 4, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                header, 6, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                header, 8, 0x9ABC_DEF0L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                header, 12, statementType, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                header, 16, objectType, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                header, 18, sequence, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                header, 20, count, ByteOrder.LITTLE_ENDIAN);
        return header;
    }

    private static byte[] kdliCommon(int operation, long dba) {
        byte[] field = RedoOpCodeTestSupport.field(12);
        field[0] = (byte) operation;
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 8, dba, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] unsignedIntField(long value) {
        byte[] field = RedoOpCodeTestSupport.field(4);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, value, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] ktbNoOperation() {
        byte[] field = RedoOpCodeTestSupport.field(8);
        field[0] = 0x06;
        return field;
    }

    private static byte[] empty() {
        return RedoOpCodeTestSupport.field(0);
    }

    private static byte[] lobId() {
        return new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    }

    private static byte[] bytes(RedoLogRecord record, int position, int size) {
        int start = record.dataOffset() + position;
        return Arrays.copyOfRange(record.data(), start, start + size);
    }
}
