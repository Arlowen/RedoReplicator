/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo;

import io.github.arlowen.redoreplicator.redo.reader.RedoBlockHeaderParser;
import io.github.arlowen.redoreplicator.redo.common.Scn;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

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

    public static byte[] redoFile(
            ByteOrder byteOrder,
            int blockSize,
            long version,
            long blockCount,
            Scn nextScn,
            long sequence,
            List<byte[]> dataBlocks,
            int physicalBlockCount) {
        byte[] file = new byte[physicalBlockCount * blockSize];
        byte[] header = fileHeader(byteOrder, blockSize, version);
        System.arraycopy(header, 0, file, 0, header.length);
        int metadata = blockSize;
        writeUnsignedInt(file, metadata + 8, sequence, byteOrder);
        writeUnsignedInt(file, metadata + 156, blockCount, byteOrder);
        if (nextScn.isNone()) {
            Arrays.fill(file, metadata + 192, metadata + 198,
                    (byte) 0xFF);
        } else {
            writeScn(file, metadata + 192, nextScn.rawValue(), byteOrder);
        }
        rewriteChecksum(file, metadata, byteOrder, blockSize);
        for (int index = 0; index < dataBlocks.size(); index++) {
            System.arraycopy(dataBlocks.get(index), 0, file,
                    (index + 2) * blockSize, blockSize);
        }
        return file;
    }

    public static byte[] redoBlock(
            ByteOrder byteOrder,
            int blockSize,
            long blockNumber,
            long sequence,
            byte[] payload) {
        if (payload.length > blockSize - 16) {
            throw new IllegalArgumentException(
                    "Redo block payload is too large");
        }
        byte[] block = new byte[blockSize];
        block[0] = 1;
        block[1] = 0x22;
        if (blockSize == 4096) {
            block[1] = (byte) 0x82;
        }
        writeUnsignedInt(block, 4, blockNumber, byteOrder);
        writeUnsignedInt(block, 8, sequence, byteOrder);
        System.arraycopy(payload, 0, block, 16, payload.length);
        rewriteChecksum(block, 0, byteOrder, blockSize);
        return block;
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

    public static byte[] spanningLwnRecord() {
        int recordSize = 600;
        byte[] data = new byte[recordSize];
        writeUnsignedInt(data, 0, recordSize, ByteOrder.LITTLE_ENDIAN);
        data[4] = 0x05;
        writeUnsignedShort(data, 6, 0x1234, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, 8, 0x5678_9000L, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedShort(data, 12, 3, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, 16, 42, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedShort(data, 24, 1, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedShort(data, 26, 1, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, 28, 2, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, 32, recordSize, ByteOrder.LITTLE_ENDIAN);
        writeScn(data, 40, 0x0000_1234_5678_9ABCL, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, 64, 989_619_936L, ByteOrder.LITTLE_ENDIAN);

        int vectorOffset = 68;
        data[vectorOffset] = 0x05;
        data[vectorOffset + 1] = 0x02;
        writeUnsignedShort(data, vectorOffset + 2, 17, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, vectorOffset + 4, 0xABCD_0007L, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedInt(data, vectorOffset + 8, 0x1234_5678L, ByteOrder.LITTLE_ENDIAN);
        writeScn(data, vectorOffset + 12, 0x0000_1234_5678_8FFFL, ByteOrder.LITTLE_ENDIAN);
        data[vectorOffset + 20] = 9;
        data[vectorOffset + 21] = (byte) 0x83;
        writeUnsignedShort(data, vectorOffset + 24, 4, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedShort(data, vectorOffset + 28, 0x55AA, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedShort(data, vectorOffset + 32, 4, ByteOrder.LITTLE_ENDIAN);
        writeUnsignedShort(data, vectorOffset + 34, 496, ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < 496; index++) {
            data[vectorOffset + 36 + index] = (byte) index;
        }
        return data;
    }

    public static List<byte[]> spanningLwnBlocks() {
        byte[] record = spanningLwnRecord();
        byte[] first = new byte[512];
        byte[] second = new byte[512];
        System.arraycopy(record, 0, first, 16, 496);
        System.arraycopy(record, 496, second, 16, record.length - 496);
        return List.of(first, second);
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

    private static void rewriteChecksum(
            byte[] bytes, int offset, ByteOrder byteOrder, int blockSize) {
        writeUnsignedShort(bytes, offset + 14, 0, byteOrder);
        RedoBlockHeaderParser parser = new RedoBlockHeaderParser(
                byteOrder, blockSize);
        int checksum = parser.calculateChecksum(bytes, offset);
        writeUnsignedShort(bytes, offset + 14, checksum, byteOrder);
        checksum = parser.calculateChecksum(bytes, offset);
        writeUnsignedShort(bytes, offset + 14, checksum, byteOrder);
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
