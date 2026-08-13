/*
 * Java translation derived from OpenLogReplicator CharacterSetAL32UTF8 in
 * src/locales/CharacterSetAL32UTF8.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public final class CharacterSetAL32UTF8 extends CharacterSet {
    public static final long ID = 873;

    public CharacterSetAL32UTF8() {
        super(ID, "AL32UTF8");
    }

    @Override
    public String decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        StringBuilder result = new StringBuilder();
        int offset = 0;
        while (offset < data.length) {
            DecodedCharacter character = decodeCharacter(data, offset);
            result.appendCodePoint(character.codePoint());
            offset += character.consumedBytes();
        }
        return result.toString();
    }

    private static DecodedCharacter decodeCharacter(byte[] data, int offset) {
        int remaining = data.length - offset;
        int byte1 = data[offset] & 0xFF;
        if ((byte1 & 0x80) == 0) {
            return new DecodedCharacter(byte1, 1);
        }
        if (remaining == 1) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 1);
        }

        int byte2 = data[offset + 1] & 0xFF;
        if ((byte2 & 0xC0) != 0x80) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 2);
        }
        if ((byte1 & 0xE0) == 0xC0) {
            int codePoint = ((byte1 & 0x1F) << 6) | (byte2 & 0x3F);
            return new DecodedCharacter(codePoint, 2);
        }
        if (remaining == 2) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 2);
        }

        int byte3 = data[offset + 2] & 0xFF;
        if ((byte3 & 0xC0) != 0x80) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 3);
        }
        if ((byte1 & 0xF0) == 0xE0) {
            int codePoint = ((byte1 & 0x0F) << 12)
                    | ((byte2 & 0x3F) << 6) | (byte3 & 0x3F);
            return new DecodedCharacter(codePoint, 3);
        }
        if (remaining == 3) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 3);
        }

        int byte4 = data[offset + 3] & 0xFF;
        if ((byte4 & 0xC0) != 0x80) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 4);
        }
        if ((byte1 & 0xF8) == 0xF0) {
            int codePoint = ((byte1 & 0x07) << 18)
                    | ((byte2 & 0x3F) << 12)
                    | ((byte3 & 0x3F) << 6) | (byte4 & 0x3F);
            if (codePoint <= 0x10FFFF
                    && (codePoint < 0xD800 || codePoint > 0xDFFF)) {
                return new DecodedCharacter(codePoint, 4);
            }
        }
        return new DecodedCharacter(UNKNOWN_CHARACTER, 4);
    }
}
