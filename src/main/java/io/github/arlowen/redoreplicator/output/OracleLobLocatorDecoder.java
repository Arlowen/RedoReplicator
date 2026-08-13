/*
 * Java translation derived from OpenLogReplicator Builder::parseLob in
 * src/builder/Builder.h.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.lob.RedoLobContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class OracleLobLocatorDecoder {
    private static final int LOB_ID_END = 20;
    private static final int IN_ROW_FLAG = 0x04;
    private static final int IN_INDEX_FLAG = 0x0400;
    private static final int IN_VALUE_FLAG = 0x0100;
    private static final int INLINE_DATA_FLAG = 0x0800;

    byte[] decodeInline(byte[] locator) {
        return decode(locator, null);
    }

    byte[] decode(byte[] locator, RedoLobContext context) {
        if (locator.length < LOB_ID_END) {
            throw invalid("locator is shorter than the LOB header");
        }
        LobId lobId = LobId.of(Arrays.copyOfRange(locator, 10, LOB_ID_END));
        if ((locator[5] & IN_ROW_FLAG) == 0) {
            throw requiresTransactionPages();
        }
        if (locator.length < 23) {
            throw invalid("in-row locator is shorter than its body header");
        }

        int bodySize = readUnsignedShort(locator, 20);
        if (locator.length != bodySize + LOB_ID_END) {
            throw invalid("locator body length does not match its header");
        }
        int flags = readUnsignedShort(locator, 22);
        if ((flags & IN_INDEX_FLAG) != 0) {
            return decodeIndexed(locator, lobId, context);
        }
        if ((flags & IN_VALUE_FLAG) != 0) {
            return decodeFixedInline(locator, bodySize);
        }
        return decodeVariableInline(locator, bodySize, flags);
    }

    private static byte[] decodeIndexed(
            byte[] locator, LobId lobId, RedoLobContext context) {
        if (context == null) {
            throw requiresTransactionPages();
        }
        if (locator.length < 36 || (locator.length - 36) % 4 != 0) {
            throw invalid("in-index locator page list is malformed");
        }
        long pageCount = readUnsignedInt(locator, 24);
        int sizeRest = readUnsignedShort(locator, 28);
        long totalPages = pageCount;
        if (sizeRest > 0) {
            totalPages++;
        }
        int explicitCount = (locator.length - 36) / 4;
        if (explicitCount > totalPages) {
            throw invalid("in-index locator contains excess page references");
        }
        List<Long> pages = new ArrayList<>(explicitCount);
        int position = 36;
        while (position < locator.length) {
            pages.add(readUnsignedInt(locator, position));
            position += 4;
        }
        return context.readIndexed(lobId, pageCount, sizeRest, pages);
    }

    private static byte[] decodeFixedInline(byte[] locator, int bodySize) {
        if (bodySize < 16 || locator.length < 34) {
            throw invalid("fixed in-value locator is too short");
        }
        long zero1 = readUnsignedInt(locator, 24);
        int valueSize = readUnsignedShort(locator, 28);
        long zero2 = readUnsignedInt(locator, 30);
        if (zero1 != 0 || zero2 != 0 || valueSize + 16 != bodySize) {
            throw invalid("fixed in-value locator header is inconsistent");
        }
        int valueOffset = 36;
        requireExactPayload(locator, valueOffset, valueSize);
        return Arrays.copyOfRange(locator, valueOffset, locator.length);
    }

    private static byte[] decodeVariableInline(
            byte[] locator, int bodySize, int flags) {
        if (bodySize < 10 || locator.length < 30) {
            throw invalid("variable in-value locator is too short");
        }
        int sizeCode = locator[26] & 0x03;
        int valueOffset = 28;
        long valueSize;
        if (sizeCode == 0) {
            valueSize = locator[valueOffset] & 0xFFL;
            valueOffset++;
        } else if (sizeCode == 1) {
            valueSize = readUnsignedShort(locator, valueOffset);
            valueOffset += 2;
        } else if (sizeCode == 2) {
            valueSize = readUnsignedMedium(locator, valueOffset);
            valueOffset += 3;
        } else {
            valueSize = readUnsignedInt(locator, valueOffset);
            valueOffset += 4;
        }

        int descriptorSize = locator[27] & 0x0F;
        if (descriptorSize == 0) {
            valueOffset++;
        } else if (descriptorSize == 1) {
            valueOffset += 2;
        } else {
            throw invalid("variable in-value locator has an unknown descriptor");
        }
        if (valueSize == 0) {
            return new byte[0];
        }
        if ((flags & INLINE_DATA_FLAG) == 0) {
            throw requiresTransactionPages();
        }
        if (valueSize > Integer.MAX_VALUE) {
            throw invalid("inline LOB value is too large");
        }
        requireExactPayload(locator, valueOffset, (int) valueSize);
        return Arrays.copyOfRange(locator, valueOffset, locator.length);
    }

    private static void requireExactPayload(
            byte[] locator, int offset, int size) {
        if (offset < 0 || size < 0 || offset > locator.length - size
                || offset + size != locator.length) {
            throw invalid("inline LOB payload length is inconsistent");
        }
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        requireBytes(data, offset, 2);
        return (data[offset] & 0xFF) << 8
                | data[offset + 1] & 0xFF;
    }

    private static long readUnsignedMedium(byte[] data, int offset) {
        requireBytes(data, offset, 3);
        return (long) (data[offset] & 0xFF) << 16
                | (long) (data[offset + 1] & 0xFF) << 8
                | data[offset + 2] & 0xFFL;
    }

    private static long readUnsignedInt(byte[] data, int offset) {
        requireBytes(data, offset, 4);
        return (long) (data[offset] & 0xFF) << 24
                | (long) (data[offset + 1] & 0xFF) << 16
                | (long) (data[offset + 2] & 0xFF) << 8
                | data[offset + 3] & 0xFFL;
    }

    private static void requireBytes(byte[] data, int offset, int size) {
        if (offset < 0 || offset > data.length - size) {
            throw invalid("locator field extends past the available bytes");
        }
    }

    private static RedoLogException requiresTransactionPages() {
        return new RedoLogException(50075,
                "LOB locator requires transaction LOB page reconstruction");
    }

    private static RedoLogException invalid(String reason) {
        return new RedoLogException(50075,
                "Invalid Oracle LOB locator: " + reason);
    }
}
