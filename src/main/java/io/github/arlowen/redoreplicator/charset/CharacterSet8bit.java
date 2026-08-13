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
    private final int[] unicodeMap;

    CharacterSet8bit(long id, String name, String encodedMap) {
        super(id, name);
        unicodeMap = CharacterSetMap.decode(encodedMap);
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
