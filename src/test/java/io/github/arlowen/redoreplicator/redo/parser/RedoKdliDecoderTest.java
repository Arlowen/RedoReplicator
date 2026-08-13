/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedoKdliDecoderTest {
    private final RedoKdliDecoder decoder = new RedoKdliDecoder(
            new RedoByteReader(ByteOrder.LITTLE_ENDIAN));

    @Test
    void decodesCommonAndFillFields() {
        byte[] common = new byte[12];
        common[0] = 0x06;
        RedoBinaryTestSupport.writeUnsignedInt(
                common, 8, 0xF123_4567L, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord commonRecord = record(common);
        decoder.readCommon(commonRecord, 0, common.length);
        assertEquals(0x06, commonRecord.opc);
        assertEquals(0xF123_4567L, commonRecord.dba);

        byte[] fill = new byte[11];
        fill[0] = 0x06;
        RedoBinaryTestSupport.writeUnsignedShort(
                fill, 2, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                fill, 6, 3, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord fillRecord = record(fill);
        decoder.read(fillRecord, 0, fill.length);
        assertEquals(0x06, fillRecord.indKeyDataCode);
        assertEquals(0x1234, fillRecord.lobOffset);
        assertEquals(8, fillRecord.lobData);
        assertEquals(3, fillRecord.lobDataSize);
    }

    @Test
    void decodesMappingFieldLocations() {
        int[] codes = {0x07, 0x08, 0x10};
        for (int code : codes) {
            byte[] field = new byte[8];
            field[0] = (byte) code;
            RedoLogRecord record = record(field);
            decoder.read(record, 0, field.length);
            assertEquals(code, record.indKeyDataCode);
            assertEquals(0, record.indKeyData);
            assertEquals(field.length, record.indKeyDataSize);
        }

        byte[] almap = new byte[12];
        almap[0] = 0x0D;
        RedoLogRecord almapRecord = record(almap);
        decoder.read(almapRecord, 0, almap.length);
        assertEquals(0x0D, almapRecord.indKeyDataCode);
        assertEquals(12, almapRecord.indKeyDataSize);
    }

    @Test
    void decodesSupplementalAndFastPathLoadFields() {
        byte[] supplemental = new byte[24];
        supplemental[0] = 0x09;
        RedoBinaryTestSupport.writeUnsignedShort(
                supplemental, 4, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                supplemental, 6, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                supplemental, 8, 0x9ABC_DEF0L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                supplemental, 12, 0xE100_0001L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                supplemental, 18, 17, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord supplementalRecord = record(supplemental);
        decoder.read(supplementalRecord, 0, supplemental.length);
        assertEquals(Xid.of(0x1234, 0x5678, 0x9ABC_DEF0L), supplementalRecord.xid);
        assertEquals(0xE100_0001L, supplementalRecord.obj);
        assertEquals(17, supplementalRecord.col);

        byte[] fastPath = new byte[28];
        fastPath[0] = 0x0B;
        RedoBinaryTestSupport.writeUnsignedShort(
                fastPath, 16, 7, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                fastPath, 18, 8, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                fastPath, 20, 9, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                fastPath, 24, 0xD200_0002L, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord fastPathRecord = record(fastPath);
        decoder.read(fastPathRecord, 0, fastPath.length);
        assertEquals(Xid.of(7, 8, 9), fastPathRecord.xid);
        assertEquals(0xD200_0002L, fastPathRecord.dataObj);
    }

    @Test
    void decodesLobIdentityAndLoadHeaderBlocks() {
        byte[] lobId = lobId();
        byte[] loadData = new byte[56];
        loadData[0] = 0x04;
        System.arraycopy(lobId, 0, loadData, 12, lobId.length);
        RedoLogRecord loadDataRecord = record(loadData);
        decoder.read(loadDataRecord, 0, loadData.length);
        assertEquals(LobId.of(lobId), loadDataRecord.lobId);
        assertEquals(RedoLogRecord.INVALID_LOB_PAGE_NO, loadDataRecord.lobPageNo);

        byte[] loadLhb = new byte[112];
        loadLhb[0] = 0x0C;
        System.arraycopy(lobId, 0, loadLhb, 12, lobId.length);
        RedoBinaryTestSupport.writeUnsignedInt(
                loadLhb, 64, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                loadLhb, 68, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                loadLhb, 72, 3, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                loadLhb, 76, 4, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord loadLhbRecord = record(loadLhb);
        decoder.read(loadLhbRecord, 0, loadLhb.length);
        assertEquals(LobId.of(lobId), loadLhbRecord.lobId);
        assertEquals(1, loadLhbRecord.dba0);
        assertEquals(2, loadLhbRecord.dba1);
        assertEquals(3, loadLhbRecord.dba2);
        assertEquals(4, loadLhbRecord.dba3);
    }

    @Test
    void rejectsTruncatedKdliPayloads() {
        RedoLogRecord shortInfo = record(new byte[]{0x01});
        RedoLogException infoError = assertThrows(
                RedoLogException.class, () -> decoder.read(shortInfo, 0, 1));
        assertEquals(50061, infoError.getErrorCode());

        byte[] shortFill = new byte[9];
        shortFill[0] = 0x06;
        RedoBinaryTestSupport.writeUnsignedShort(
                shortFill, 6, 2, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord fillRecord = record(shortFill);
        RedoLogException fillError = assertThrows(
                RedoLogException.class, () -> decoder.read(fillRecord, 0, shortFill.length));
        assertEquals(50061, fillError.getErrorCode());
    }

    private static RedoLogRecord record(byte[] data) {
        RedoLogRecord record = new RedoLogRecord();
        record.attachData(data, 0, data.length);
        return record;
    }

    private static byte[] lobId() {
        return new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    }
}
