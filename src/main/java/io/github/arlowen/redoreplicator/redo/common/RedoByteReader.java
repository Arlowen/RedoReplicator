/*
 * Java translation derived from OpenLogReplicator byte readers in src/common/Ctx.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import java.nio.ByteOrder;

public final class RedoByteReader {
    private final ByteOrder byteOrder;

    public RedoByteReader(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    public int readUnsignedShort(byte[] bytes, int offset) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
        }
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    public long readUnsignedInt(byte[] bytes, int offset) {
        int value;
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            value = ((bytes[offset] & 0xFF) << 24)
                    | ((bytes[offset + 1] & 0xFF) << 16)
                    | ((bytes[offset + 2] & 0xFF) << 8)
                    | (bytes[offset + 3] & 0xFF);
        } else {
            value = (bytes[offset] & 0xFF)
                    | ((bytes[offset + 1] & 0xFF) << 8)
                    | ((bytes[offset + 2] & 0xFF) << 16)
                    | ((bytes[offset + 3] & 0xFF) << 24);
        }
        return Integer.toUnsignedLong(value);
    }

    public long readLong(byte[] bytes, int offset) {
        long result = 0;
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            for (int index = 0; index < Long.BYTES; index++) {
                result = (result << 8) | ((long) bytes[offset + index] & 0xFFL);
            }
        } else {
            for (int index = Long.BYTES - 1; index >= 0; index--) {
                result = (result << 8) | ((long) bytes[offset + index] & 0xFFL);
            }
        }
        return result;
    }

    public long read56(byte[] bytes, int offset) {
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            long result = 0;
            for (int index = 6; index >= 0; index--) {
                result = (result << 8) | ((long) bytes[offset + index] & 0xFFL);
            }
            return result;
        }

        return ((long) bytes[offset] & 0xFFL) << 24
                | ((long) bytes[offset + 1] & 0xFFL) << 16
                | ((long) bytes[offset + 2] & 0xFFL) << 8
                | ((long) bytes[offset + 3] & 0xFFL)
                | ((long) bytes[offset + 4] & 0xFFL) << 40
                | ((long) bytes[offset + 5] & 0xFFL) << 32
                | ((long) bytes[offset + 6] & 0xFFL) << 48;
    }

    public Scn readScn(byte[] bytes, int offset) {
        if (allScnBytesAreFF(bytes, offset)) {
            return Scn.none();
        }

        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            if ((bytes[offset + 5] & 0x80) != 0) {
                return Scn.fromLittleEndian(
                        bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3],
                        bytes[offset + 6], bytes[offset + 7], bytes[offset + 4], bytes[offset + 5] & 0x7F);
            }
            return Scn.fromLittleEndian(bytes[offset], bytes[offset + 1], bytes[offset + 2],
                    bytes[offset + 3], bytes[offset + 4], bytes[offset + 5]);
        }

        if ((bytes[offset + 4] & 0x80) != 0) {
            return Scn.fromLittleEndian(
                    bytes[offset + 3], bytes[offset + 2], bytes[offset + 1], bytes[offset],
                    bytes[offset + 7], bytes[offset + 6], bytes[offset + 5], bytes[offset + 4] & 0x7F);
        }
        return Scn.fromLittleEndian(bytes[offset + 3], bytes[offset + 2], bytes[offset + 1],
                bytes[offset], bytes[offset + 5], bytes[offset + 4]);
    }

    public Scn readReverseScn(byte[] bytes, int offset) {
        if (allScnBytesAreFF(bytes, offset)) {
            return Scn.none();
        }

        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            if ((bytes[offset + 1] & 0x80) != 0) {
                return Scn.fromLittleEndian(
                        bytes[offset + 2], bytes[offset + 3], bytes[offset + 4], bytes[offset + 5],
                        0, 0, bytes[offset], bytes[offset + 1] & 0x7F);
            }
            return Scn.fromLittleEndian(bytes[offset + 2], bytes[offset + 3], bytes[offset + 4],
                    bytes[offset + 5], bytes[offset], bytes[offset + 1]);
        }

        if ((bytes[offset] & 0x80) != 0) {
            return Scn.fromLittleEndian(
                    bytes[offset + 5], bytes[offset + 4], bytes[offset + 3], bytes[offset + 2],
                    0, 0, bytes[offset + 1], bytes[offset] & 0x7F);
        }
        return Scn.fromLittleEndian(bytes[offset + 5], bytes[offset + 4], bytes[offset + 3],
                bytes[offset + 2], bytes[offset + 1], bytes[offset]);
    }

    private static boolean allScnBytesAreFF(byte[] bytes, int offset) {
        for (int index = 0; index < 6; index++) {
            if ((bytes[offset + index] & 0xFF) != 0xFF) {
                return false;
            }
        }
        return true;
    }
}
