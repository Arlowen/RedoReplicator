/*
 * Java translation derived from OpenLogReplicator CharacterSet7bit in
 * src/locales/CharacterSet7bit.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public final class CharacterSet7bit extends CharacterSet {
    private final int[] unicodeMap;

    public CharacterSet7bit(
            long id, String name, int... substitutions) {
        super(id, name);
        unicodeMap = new int[128];
        for (int index = 0; index < unicodeMap.length; index++) {
            unicodeMap[index] = index;
        }
        for (int index = 0; index < substitutions.length; index += 2) {
            unicodeMap[substitutions[index]] = substitutions[index + 1];
        }
    }

    @Override
    public String decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        StringBuilder result = new StringBuilder(data.length);
        for (byte value : data) {
            result.appendCodePoint(unicodeMap[value & 0x7F]);
        }
        return result.toString();
    }
}
