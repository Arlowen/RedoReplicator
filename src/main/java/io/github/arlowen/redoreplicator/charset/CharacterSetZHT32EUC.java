/*
 * Java translation derived from OpenLogReplicator CharacterSetZHT32EUC in
 * src/locales/CharacterSetZHT32EUC.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public final class CharacterSetZHT32EUC extends CharacterSet {
    public static final long ID = 860;

    private static final int TWO_BYTE1_MIN = 0xA1;
    private static final int TWO_BYTE1_MAX = 0xFD;
    private static final int TWO_BYTE2_MIN = 0xA1;
    private static final int TWO_BYTE2_MAX = 0xFE;
    private static final int FOUR_BYTE1 = 0x8E;
    private static final int FOUR_BYTE2_MIN = 0xA2;
    private static final int FOUR_BYTE2_MAX = 0xAE;
    private static final int FOUR_BYTE3_MIN = 0xA1;
    private static final int FOUR_BYTE3_MAX = 0xF2;
    private static final int FOUR_BYTE4_MIN = 0xA1;
    private static final int FOUR_BYTE4_MAX = 0xFE;

    private final int[] unicodeMap2;
    private final int[] unicodeMap4;

    CharacterSetZHT32EUC(String encodedMap2, String encodedMap4) {
        super(ID, "ZHT32EUC");
        unicodeMap2 = CharacterSetMap.decode(encodedMap2);
        unicodeMap4 = CharacterSetMap.decode(encodedMap4);
    }

    @Override
    public String decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        StringBuilder result = new StringBuilder(data.length);
        int offset = 0;
        while (offset < data.length) {
            int byte1 = data[offset++] & 0xFF;
            if ((byte1 & 0x80) == 0) {
                result.appendCodePoint(byte1);
                continue;
            }
            if (offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }

            if (byte1 == FOUR_BYTE1) {
                int byte2 = data[offset++] & 0xFF;
                if (byte2 < FOUR_BYTE2_MIN || byte2 > FOUR_BYTE2_MAX
                        || offset == data.length) {
                    result.appendCodePoint(UNKNOWN_CHARACTER);
                    continue;
                }
                int byte3 = data[offset++] & 0xFF;
                if (byte3 < FOUR_BYTE3_MIN || byte3 > FOUR_BYTE3_MAX
                        || offset == data.length) {
                    result.appendCodePoint(UNKNOWN_CHARACTER);
                    continue;
                }
                int byte4 = data[offset++] & 0xFF;
                if (byte4 < FOUR_BYTE4_MIN || byte4 > FOUR_BYTE4_MAX) {
                    result.appendCodePoint(UNKNOWN_CHARACTER);
                    continue;
                }
                int byte3Range = FOUR_BYTE3_MAX - FOUR_BYTE3_MIN + 1;
                int byte4Range = FOUR_BYTE4_MAX - FOUR_BYTE4_MIN + 1;
                int index = ((byte2 - FOUR_BYTE2_MIN) * byte3Range
                        + byte3 - FOUR_BYTE3_MIN) * byte4Range
                        + byte4 - FOUR_BYTE4_MIN;
                result.appendCodePoint(unicodeMap4[index]);
                continue;
            }

            if (byte1 < TWO_BYTE1_MIN || byte1 > TWO_BYTE1_MAX) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            int byte2 = data[offset++] & 0xFF;
            if (byte2 < TWO_BYTE2_MIN || byte2 > TWO_BYTE2_MAX) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            int rowWidth = TWO_BYTE2_MAX - TWO_BYTE2_MIN + 1;
            int index = (byte1 - TWO_BYTE1_MIN) * rowWidth
                    + byte2 - TWO_BYTE2_MIN;
            result.appendCodePoint(unicodeMap2[index]);
        }
        return result.toString();
    }
}
