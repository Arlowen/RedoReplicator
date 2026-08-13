/*
 * Java translation derived from OpenLogReplicator CharacterSet8bit in
 * src/locales/CharacterSet8bit.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public final class CharacterSet8bit extends CharacterSet {
    private static final int CHARACTER_COUNT = 256;
    private static final int HEX_DIGITS_PER_CHARACTER = 4;

    private final int[] unicodeMap;

    CharacterSet8bit(long id, String name, String encodedMap) {
        super(id, name);
        unicodeMap = new int[CHARACTER_COUNT];
        for (int index = 0; index < unicodeMap.length; index++) {
            int start = index * HEX_DIGITS_PER_CHARACTER;
            unicodeMap[index] = Integer.parseInt(
                    encodedMap, start,
                    start + HEX_DIGITS_PER_CHARACTER, 16);
        }
    }

    @Override
    public String decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        StringBuilder result = new StringBuilder(data.length);
        for (byte value : data) {
            result.appendCodePoint(unicodeMap[value & 0xFF]);
        }
        return result.toString();
    }
}
