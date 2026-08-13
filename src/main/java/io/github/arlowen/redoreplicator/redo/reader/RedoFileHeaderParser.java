/*
 * Java translation derived from OpenLogReplicator redo header handling in
 * src/reader/Reader.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class RedoFileHeaderParser {
    private static final int MAX_BLOCK_SIZE = 4096;

    public RedoFileHeader parse(byte[] data, boolean verifyChecksum) {
        if (data.length < 32) {
            throw new RedoLogException(40003, "redo file header is shorter than 32 bytes");
        }
        if (data[0] != 0) {
            throw new RedoLogException(40003, "invalid header[0]: " + (data[0] & 0xFF));
        }

        ByteOrder byteOrder = detectByteOrder(data);
        RedoByteReader byteReader = new RedoByteReader(byteOrder);
        long rawBlockSize = byteReader.readUnsignedInt(data, 20);
        if (rawBlockSize > Integer.MAX_VALUE) {
            throw new RedoLogException(40005, "invalid block size: " + rawBlockSize);
        }
        int blockSize = (int) rawBlockSize;
        int blockType = data[1] & 0xFF;
        if (!isSupportedBlockSize(blockSize, blockType)) {
            throw new RedoLogException(40005, "invalid block size: " + blockSize
                    + ", header[1]: " + blockType);
        }
        if (data.length < blockSize * 2) {
            throw new RedoLogException(40003, "redo file header requires " + (blockSize * 2) + " bytes");
        }

        int fileHeaderOffset = blockSize;
        long compatibleVersion = byteReader.readUnsignedInt(data, fileHeaderOffset + 20);
        if (compatibleVersion == 0) {
            return new RedoFileHeader(true, byteOrder, blockSize, 0, "", Seq.zero(),
                    0, "", 0, 0, 0, 0, Scn.zero(), RedoTime.zero(),
                    Scn.zero(), RedoTime.zero());
        }
        if (!isSupportedVersion(compatibleVersion)) {
            throw new RedoLogException(40006, "invalid database version: " + compatibleVersion);
        }

        RedoBlockHeaderParser blockParser = new RedoBlockHeaderParser(byteOrder, blockSize);
        RedoBlockHeader blockHeader = blockParser.parse(
                data, fileHeaderOffset, 1, Seq.zero(), verifyChecksum);
        if (blockHeader.isEmpty()) {
            throw new RedoLogException(40003, "redo file metadata block is empty");
        }

        long databaseId = byteReader.readUnsignedInt(data, fileHeaderOffset + 24);
        String databaseSid = readSid(data, fileHeaderOffset + 28);
        long activation = byteReader.readUnsignedInt(data, fileHeaderOffset + 52);
        long blockCount = byteReader.readUnsignedInt(data, fileHeaderOffset + 156);
        long resetlogs = byteReader.readUnsignedInt(data, fileHeaderOffset + 160);
        int thread = byteReader.readUnsignedShort(data, fileHeaderOffset + 176);
        Scn firstScn = byteReader.readScn(data, fileHeaderOffset + 180);
        RedoTime firstTime = RedoTime.of(byteReader.readUnsignedInt(data, fileHeaderOffset + 188));
        Scn nextScn = byteReader.readScn(data, fileHeaderOffset + 192);
        RedoTime nextTime = RedoTime.of(byteReader.readUnsignedInt(data, fileHeaderOffset + 200));

        return new RedoFileHeader(false, byteOrder, blockSize, compatibleVersion,
                formatVersion(compatibleVersion), blockHeader.sequence(), databaseId,
                databaseSid, activation, blockCount, resetlogs, thread, firstScn,
                firstTime, nextScn, nextTime);
    }

    private static ByteOrder detectByteOrder(byte[] data) {
        if (matches(data, 28, 0x7A, 0x7B, 0x7C, 0x7D)) {
            return ByteOrder.BIG_ENDIAN;
        }
        if (matches(data, 28, 0x7D, 0x7C, 0x7B, 0x7A)) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        throw new RedoLogException(40004, "invalid header[28-31]: "
                + (data[28] & 0xFF) + ", " + (data[29] & 0xFF) + ", "
                + (data[30] & 0xFF) + ", " + (data[31] & 0xFF));
    }

    private static boolean matches(byte[] data, int offset, int byte0, int byte1, int byte2, int byte3) {
        return (data[offset] & 0xFF) == byte0
                && (data[offset + 1] & 0xFF) == byte1
                && (data[offset + 2] & 0xFF) == byte2
                && (data[offset + 3] & 0xFF) == byte3;
    }

    private static boolean isSupportedBlockSize(int blockSize, int blockType) {
        if (blockSize == 512 || blockSize == 1024) {
            return blockType == 0x22;
        }
        return blockSize == MAX_BLOCK_SIZE && blockType == 0x82;
    }

    private static boolean isSupportedVersion(long version) {
        boolean oracle19c = version >= 0x1300_0000L && version <= 0x131C_0000L;
        // The redo compatibility field identifies the shared 23/26 family.
        // JDBC startup validation is responsible for accepting only 26ai Free.
        boolean oracle26ai = version >= 0x1700_0000L && version <= 0x171A_2000L;
        return oracle19c || oracle26ai;
    }

    private static String formatVersion(long version) {
        long major = version >>> 24;
        long update = version >>> 16 & 0xFF;
        long patch = version >>> 8 & 0xFF;
        return major + "." + update + "." + patch;
    }

    private static String readSid(byte[] data, int offset) {
        int length = 0;
        while (length < 8 && data[offset + length] != 0) {
            length++;
        }
        return new String(data, offset, length, StandardCharsets.ISO_8859_1);
    }
}
