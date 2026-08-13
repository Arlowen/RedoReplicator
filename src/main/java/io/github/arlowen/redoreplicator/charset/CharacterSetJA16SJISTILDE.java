/*
 * Java translation derived from OpenLogReplicator CharacterSetJA16SJISTILDE in
 * src/locales/CharacterSetJA16SJISTILDE.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

public final class CharacterSetJA16SJISTILDE extends CharacterSetJA16SJIS {
    CharacterSetJA16SJISTILDE(long id, String encodedMap) {
        super(id, "JA16SJISTILDE", encodedMap);
    }

    @Override
    protected int readMap(int byte1, int byte2) {
        if (byte1 == 0x81 && byte2 == 0x60) {
            return 0xFF5E;
        }
        return super.readMap(byte1, byte2);
    }
}
