/*
 * Java translation derived from OpenLogReplicator CharacterSetAL16UTF16 in
 * src/locales/CharacterSetAL16UTF16.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public final class CharacterSetAL16UTF16 extends CharacterSet {
    public static final long ID = 2000;

    public CharacterSetAL16UTF16() {
        super(ID, "AL16UTF16");
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
        if (remaining == 1) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 1);
        }

        int byte2 = data[offset + 1] & 0xFF;
        if ((byte1 & 0xFC) == 0xDC) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 2);
        }
        if ((byte1 & 0xFC) != 0xD8) {
            return new DecodedCharacter((byte1 << 8) | byte2, 2);
        }
        if (remaining == 2) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 2);
        }

        int byte3 = data[offset + 2] & 0xFF;
        if (remaining == 3) {
            return new DecodedCharacter(UNKNOWN_CHARACTER, 3);
        }

        int byte4 = data[offset + 3] & 0xFF;
        if ((byte3 & 0xFC) == 0xDC) {
            int codePoint = 0x10000 + (((byte1 & 0x03) << 18)
                    | (byte2 << 10) | ((byte3 & 0x03) << 8) | byte4);
            return new DecodedCharacter(codePoint, 4);
        }
        return new DecodedCharacter(UNKNOWN_CHARACTER, 4);
    }
}
