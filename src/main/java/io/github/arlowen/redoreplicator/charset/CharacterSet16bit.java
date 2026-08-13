/*
 * Java translation derived from OpenLogReplicator CharacterSet16bit in
 * src/locales/CharacterSet16bit.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public class CharacterSet16bit extends CharacterSet {
    private final int[] unicodeMap;
    private final int byte1Min;
    private final int byte1Max;
    private final int byte2Min;
    private final int byte2Max;

    protected CharacterSet16bit(
            long id, String name, int byte1Min, int byte1Max,
            int byte2Min, int byte2Max, String encodedMap) {
        this(id, name, byte1Min, byte1Max, byte2Min, byte2Max,
                CharacterSetMap.decode(encodedMap));
    }

    protected CharacterSet16bit(
            long id, String name, int byte1Min, int byte1Max,
            int byte2Min, int byte2Max, int[] unicodeMap) {
        super(id, name);
        this.byte1Min = byte1Min;
        this.byte1Max = byte1Max;
        this.byte2Min = byte2Min;
        this.byte2Max = byte2Max;
        this.unicodeMap = unicodeMap;
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
            if (byte1 < byte1Min || byte1 > byte1Max
                    || byte2 < byte2Min || byte2 > byte2Max) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            result.appendCodePoint(readMap(byte1, byte2));
        }
        return result.toString();
    }

    protected int readMap(int byte1, int byte2) {
        int rowWidth = byte2Max - byte2Min + 1;
        return unicodeMap[(byte1 - byte1Min) * rowWidth
                + byte2 - byte2Min];
    }
}
