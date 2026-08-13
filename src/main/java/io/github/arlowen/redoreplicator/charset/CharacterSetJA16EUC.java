/*
 * Java translation derived from OpenLogReplicator CharacterSetJA16EUC in
 * src/locales/CharacterSetJA16EUC.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public class CharacterSetJA16EUC extends CharacterSet {
    private static final int BYTE1_MIN = 0x8E;
    private static final int BYTE1_MAX = 0xFE;
    private static final int BYTE2_MIN = 0xA1;
    private static final int BYTE2_MAX = 0xFE;
    private static final int BYTE3_MIN = 0xA1;
    private static final int BYTE3_MAX = 0xFE;

    private final int[] unicodeMap2;
    private final int[] unicodeMap3;

    protected CharacterSetJA16EUC(
            long id, String name, String encodedMap2, String encodedMap3) {
        super(id, name);
        unicodeMap2 = CharacterSetMap.decode(encodedMap2);
        unicodeMap3 = CharacterSetMap.decode(encodedMap3);
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
            if (offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }

            int byte2 = data[offset++] & 0xFF;
            if (byte1 == 0x8F) {
                if (offset == data.length) {
                    result.appendCodePoint(UNKNOWN_CHARACTER);
                    continue;
                }
                int byte3 = data[offset++] & 0xFF;
                if (byte2 < BYTE2_MIN || byte2 > BYTE2_MAX
                        || byte3 < BYTE3_MIN || byte3 > BYTE3_MAX) {
                    result.appendCodePoint(UNKNOWN_CHARACTER);
                    continue;
                }
                result.appendCodePoint(readMap3(byte2, byte3));
                continue;
            }

            if (byte1 < BYTE1_MIN || byte1 > BYTE1_MAX
                    || byte2 < BYTE2_MIN || byte2 > BYTE2_MAX
                    || !validCode(byte1)) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            result.appendCodePoint(readMap2(byte1, byte2));
        }
        return result.toString();
    }

    protected int readMap2(int byte1, int byte2) {
        int rowWidth = BYTE2_MAX - BYTE2_MIN + 1;
        return unicodeMap2[(byte1 - BYTE1_MIN) * rowWidth
                + byte2 - BYTE2_MIN];
    }

    protected int readMap3(int byte2, int byte3) {
        int rowWidth = BYTE3_MAX - BYTE3_MIN + 1;
        return unicodeMap3[(byte2 - BYTE2_MIN) * rowWidth
                + byte3 - BYTE3_MIN];
    }

    protected boolean validCode(int byte1) {
        return byte1 < 0x90 || byte1 > 0xA0;
    }
}
