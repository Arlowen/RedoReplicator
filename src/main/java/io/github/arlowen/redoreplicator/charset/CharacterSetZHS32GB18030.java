/*
 * Java translation derived from OpenLogReplicator CharacterSetZHS32GB18030 in
 * src/locales/CharacterSetZHS32GB18030.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public final class CharacterSetZHS32GB18030 extends CharacterSet {
    public static final long ID = 854;

    private static final int BYTE1_MIN = 0x81;
    private static final int BYTE1_MAX = 0xFE;
    private static final int BYTE2_MIN = 0x40;
    private static final int BYTE2_MAX = 0xFE;
    private static final int FOUR_BYTE2_MIN = 0x30;
    private static final int FOUR_BYTE2_MAX = 0x39;
    private static final int FOUR_BYTE3_MIN = 0x81;
    private static final int FOUR_BYTE3_MAX = 0xFE;
    private static final int FOUR_BYTE4_MIN = 0x30;
    private static final int FOUR_BYTE4_MAX = 0x39;
    private static final int FOUR_GROUP1_BYTE1_MAX = 0x84;
    private static final int FOUR_GROUP2_BYTE1_MIN = 0x90;
    private static final int FOUR_GROUP2_BYTE1_MAX = 0xE3;

    private final int[] unicodeMap2;
    private final int[] unicodeMap4Group1;
    private final int[] unicodeMap4Group2;

    CharacterSetZHS32GB18030(
            String encodedMap2,
            String encodedMap4Group1,
            String encodedMap4Group2) {
        super(ID, "ZHS32GB18030");
        unicodeMap2 = CharacterSetMap.decode(encodedMap2);
        unicodeMap4Group1 = CharacterSetMap.decode(encodedMap4Group1);
        unicodeMap4Group2 = CharacterSetMap.decode32(encodedMap4Group2);
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
            if (byte1 < BYTE1_MIN || byte1 > BYTE1_MAX
                    || offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }

            int byte2 = data[offset++] & 0xFF;
            if (byte2 >= BYTE2_MIN && byte2 <= BYTE2_MAX) {
                if (byte2 == 0x7F) {
                    result.appendCodePoint(UNKNOWN_CHARACTER);
                    continue;
                }
                int rowWidth = BYTE2_MAX - BYTE2_MIN + 1;
                int index = (byte1 - BYTE1_MIN) * rowWidth
                        + byte2 - BYTE2_MIN;
                result.appendCodePoint(unicodeMap2[index]);
                continue;
            }

            if (offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            int byte3 = data[offset++] & 0xFF;
            if (offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            int byte4 = data[offset++] & 0xFF;
            result.appendCodePoint(readFourByteMap(
                    byte1, byte2, byte3, byte4));
        }
        return result.toString();
    }

    private int readFourByteMap(
            int byte1, int byte2, int byte3, int byte4) {
        if (byte2 < FOUR_BYTE2_MIN || byte2 > FOUR_BYTE2_MAX
                || byte3 < FOUR_BYTE3_MIN || byte3 > FOUR_BYTE3_MAX
                || byte4 < FOUR_BYTE4_MIN || byte4 > FOUR_BYTE4_MAX) {
            return UNKNOWN_CHARACTER;
        }

        int byte1Min = BYTE1_MIN;
        int[] unicodeMap = unicodeMap4Group1;
        if (byte1 >= FOUR_GROUP2_BYTE1_MIN
                && byte1 <= FOUR_GROUP2_BYTE1_MAX) {
            byte1Min = FOUR_GROUP2_BYTE1_MIN;
            unicodeMap = unicodeMap4Group2;
        } else if (byte1 > FOUR_GROUP1_BYTE1_MAX) {
            return UNKNOWN_CHARACTER;
        }

        int byte2Range = FOUR_BYTE2_MAX - FOUR_BYTE2_MIN + 1;
        int byte3Range = FOUR_BYTE3_MAX - FOUR_BYTE3_MIN + 1;
        int byte4Range = FOUR_BYTE4_MAX - FOUR_BYTE4_MIN + 1;
        int index = (((byte1 - byte1Min) * byte2Range
                + byte2 - FOUR_BYTE2_MIN) * byte3Range
                + byte3 - FOUR_BYTE3_MIN) * byte4Range
                + byte4 - FOUR_BYTE4_MIN;
        return unicodeMap[index];
    }
}
