/*
 * Java translation derived from OpenLogReplicator CharacterSetJA16EUCTILDE in
 * src/locales/CharacterSetJA16EUCTILDE.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

public final class CharacterSetJA16EUCTILDE extends CharacterSetJA16EUC {
    CharacterSetJA16EUCTILDE(
            long id, String encodedMap2, String encodedMap3) {
        super(id, "JA16EUCTILDE", encodedMap2, encodedMap3);
    }

    @Override
    protected int readMap2(int byte1, int byte2) {
        if (byte1 == 0xA1 && byte2 == 0xC1) {
            return 0xFF5E;
        }
        return super.readMap2(byte1, byte2);
    }
}
