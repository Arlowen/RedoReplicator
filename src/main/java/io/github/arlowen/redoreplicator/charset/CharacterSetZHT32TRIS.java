/*
 * Java translation derived from OpenLogReplicator CharacterSetZHT32TRIS in
 * src/locales/CharacterSetZHT32TRIS.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public final class CharacterSetZHT32TRIS extends CharacterSet {
    public static final long ID = 863;

    private static final int BYTE1 = 0x8E;
    private static final int BYTE2_MIN = 0xA1;
    private static final int BYTE2_MAX = 0xAE;
    private static final int BYTE3_MIN = 0xA1;
    private static final int BYTE3_MAX = 0xFE;
    private static final int BYTE4_MIN = 0xA1;
    private static final int BYTE4_MAX = 0xFE;

    private final int[] unicodeMap;

    CharacterSetZHT32TRIS(String encodedMap) {
        super(ID, "ZHT32TRIS");
        unicodeMap = CharacterSetMap.decode(encodedMap);
    }

    @Override
    public String decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        StringBuilder result = new StringBuilder(data.length);
        int offset = 0;
        while (offset < data.length) {
            int byte1 = data[offset++] & 0xFF;
            if (byte1 <= 0x7F) {
                result.appendCodePoint(byte1);
                continue;
            }
            if (byte1 != BYTE1 || offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            int byte2 = data[offset++] & 0xFF;
            if (byte2 < BYTE2_MIN || byte2 > BYTE2_MAX
                    || offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            int byte3 = data[offset++] & 0xFF;
            if (byte3 < BYTE3_MIN || byte3 > BYTE3_MAX
                    || offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            int byte4 = data[offset++] & 0xFF;
            if (byte4 < BYTE4_MIN || byte4 > BYTE4_MAX) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            int byte3Range = BYTE3_MAX - BYTE3_MIN + 1;
            int byte4Range = BYTE4_MAX - BYTE4_MIN + 1;
            int index = ((byte2 - BYTE2_MIN) * byte3Range
                    + byte3 - BYTE3_MIN) * byte4Range
                    + byte4 - BYTE4_MIN;
            result.appendCodePoint(unicodeMap[index]);
        }
        return result.toString();
    }
}
