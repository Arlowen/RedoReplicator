/*
 * Java translation derived from OpenLogReplicator CharacterSetUTF8 in
 * src/locales/CharacterSetUTF8.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public final class CharacterSetUTF8 extends CharacterSet {
    public static final long ID = 871;

    public CharacterSetUTF8() {
        super(ID, "UTF8");
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
        if ((byte1 & 0xE0) == 0xC0) {
            if ((byte2 & 0xC0) != 0x80) {
                return new DecodedCharacter(UNKNOWN_CHARACTER, 2);
            }
            int codePoint = ((byte1 & 0x1F) << 6) | (byte2 & 0x3F);
            return new DecodedCharacter(codePoint, 2);
        }
        if (remaining == 2) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 2);
        }

        int byte3 = data[offset + 2] & 0xFF;
        if (byte1 == 0xED && (byte2 & 0xF0) == 0xA0) {
            if ((byte3 & 0xC0) != 0x80 || remaining == 3) {
                return new DecodedCharacter(UNKNOWN_CHARACTER, 3);
            }

            int byte4 = data[offset + 3] & 0xFF;
            if (byte4 != 0xED || remaining == 4) {
                return new DecodedCharacter(UNKNOWN_CHARACTER, 4);
            }

            int byte5 = data[offset + 4] & 0xFF;
            if ((byte5 & 0xF0) != 0xB0 || remaining == 5) {
                return new DecodedCharacter(UNKNOWN_CHARACTER, 5);
            }

            int byte6 = data[offset + 5] & 0xFF;
            if ((byte6 & 0xC0) != 0x80) {
                return new DecodedCharacter(UNKNOWN_CHARACTER, 6);
            }
            int codePoint = (((byte2 & 0x0F) << 16)
                    | ((byte3 & 0x3F) << 10)
                    | ((byte5 & 0x0F) << 6) | (byte6 & 0x3F)) + 0x10000;
            if (codePoint <= 0x10FFFF) {
                return new DecodedCharacter(codePoint, 6);
            }
            return new DecodedCharacter(UNKNOWN_CHARACTER, 6);
        }

        if ((byte2 & 0xC0) != 0x80 || (byte3 & 0xC0) != 0x80) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 3);
        }
        int codePoint = ((byte1 & 0x0F) << 12)
                | ((byte2 & 0x3F) << 6) | (byte3 & 0x3F);
        return new DecodedCharacter(codePoint, 3);
    }
}
