/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo;

import io.github.arlowen.redoreplicator.redo.reader.RedoBlockHeaderParser;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class RedoBinaryTestSupport {
    private RedoBinaryTestSupport() {
    }

    public static byte[] fileHeader(ByteOrder byteOrder, int blockSize, long version) {
        byte[] data = new byte[blockSize * 2];
        int blockType = 0x22;
        if (blockSize == 4096) {
            blockType = 0x82;
        }
        data[1] = (byte) blockType;
        writeUnsignedInt(data, 20, blockSize, byteOrder);
        writeEndianMarker(data, byteOrder);

        int header = blockSize;
        data[header] = 1;
        data[header + 1] = (byte) blockType;
        writeUnsignedInt(data, header + 4, 1, byteOrder);
        writeUnsignedInt(data, header + 8, 77, byteOrder);
        writeUnsignedInt(data, header + 20, version, byteOrder);
        writeUnsignedInt(data, header + 24, 0xF000_0001L, byteOrder);
        byte[] sid = "ORCL19".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(sid, 0, data, header + 28, sid.length);
        writeUnsignedInt(data, header + 52, 9, byteOrder);
        writeUnsignedInt(data, header + 156, 1_000, byteOrder);
        writeUnsignedInt(data, header + 160, 5, byteOrder);
        writeUnsignedShort(data, header + 176, 2, byteOrder);
        writeScn(data, header + 180, 0x0000_1234_5678_9ABCL, byteOrder);
        writeUnsignedInt(data, header + 188, 989_619_936L, byteOrder);
        writeScn(data, header + 192, 0x0000_1234_5678_ABCDL, byteOrder);
        writeUnsignedInt(data, header + 200, 989_619_996L, byteOrder);

        RedoBlockHeaderParser blockParser = new RedoBlockHeaderParser(byteOrder, blockSize);
        int checksum = blockParser.calculateChecksum(data, header);
        writeUnsignedShort(data, header + 14, checksum, byteOrder);
        // A big-endian checksum is a fixed point because Reader.cpp folds
        // native words but reads the stored value in redo byte order.
        checksum = blockParser.calculateChecksum(data, header);
        writeUnsignedShort(data, header + 14, checksum, byteOrder);
        return data;
    }

    public static byte[] extendedRecord(int recordSize) {
        byte[] data = new byte[512];
        int offset = 16;
        writeUnsignedInt(data, offset, recordSize, ByteOrder.LITTLE_ENDIAN);
        data[offset + 4] = 0x05;
        writeUnsignedInt(data, offset + 16, 42, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedShort(data, offset + 24, 2, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedShort(data, offset + 26, 3, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, offset + 28, 4, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, offset + 32, recordSize, ByteOrder.LITTLE_ENDIAN);
        writeScn(data, offset + 40, 0x0000_1234_5678_9ABCL, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, offset + 64, 989_619_936L, ByteOrder.LITTLE_ENDIAN);
        return data;
    }

    public static void writeScn(byte[] data, int offset, long value, ByteOrder byteOrder) {
        writeUnsignedInt(data, offset, value & 0xFFFF_FFFFL, byteOrder);
        writeUnsignedShort(data, offset + 4, (int) (value >>> 32) & 0xFFFF, byteOrder);
    }

    public static void writeUnsignedShort(byte[] data, int offset, int value, ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            data[offset] = (byte) (value >>> 8);
            data[offset + 1] = (byte) value;
        } else {
            data[offset] = (byte) value;
            data[offset + 1] = (byte) (value >>> 8);
        }
    }

    public static void writeUnsignedInt(byte[] data, int offset, long value, ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            data[offset] = (byte) (value >>> 24);
            data[offset + 1] = (byte) (value >>> 16);
            data[offset + 2] = (byte) (value >>> 8);
            data[offset + 3] = (byte) value;
        } else {
            data[offset] = (byte) value;
            data[offset + 1] = (byte) (value >>> 8);
            data[offset + 2] = (byte) (value >>> 16);
            data[offset + 3] = (byte) (value >>> 24);
        }
    }

    private static void writeEndianMarker(byte[] data, ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            data[28] = 0x7A;
            data[29] = 0x7B;
            data[30] = 0x7C;
            data[31] = 0x7D;
        } else {
            data[28] = 0x7D;
            data[29] = 0x7C;
            data[30] = 0x7B;
            data[31] = 0x7A;
        }
    }
}
