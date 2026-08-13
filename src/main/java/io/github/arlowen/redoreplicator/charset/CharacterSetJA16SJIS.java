/*
 * Java translation derived from OpenLogReplicator CharacterSetJA16SJIS in
 * src/locales/CharacterSetJA16SJIS.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public class CharacterSetJA16SJIS extends CharacterSet16bit {
    private static final int BYTE1_MIN = 0x81;
    private static final int BYTE1_MAX = 0xFC;
    private static final int BYTE2_MIN = 0x40;
    private static final int BYTE2_MAX = 0xFC;

    protected CharacterSetJA16SJIS(
            long id, String name, String encodedMap) {
        super(id, name, BYTE1_MIN, BYTE1_MAX,
                BYTE2_MIN, BYTE2_MAX, encodedMap);
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
            if (byte1 >= 0xA1 && byte1 <= 0xDF) {
                result.appendCodePoint(byte1 + 0xFF61 - 0xA1);
                continue;
            }
            if (offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }

            int byte2 = data[offset++] & 0xFF;
            if (byte1 < BYTE1_MIN || byte1 > BYTE1_MAX
                    || byte2 < BYTE2_MIN || byte2 > BYTE2_MAX) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            result.appendCodePoint(readMap(byte1, byte2));
        }
        return result.toString();
    }
}
