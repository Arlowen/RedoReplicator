/*
 * Java translation derived from OpenLogReplicator CharacterSetZHT16HKSCS31 in
 * src/locales/CharacterSetZHT16HKSCS31.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

public final class CharacterSetZHT16HKSCS31 extends CharacterSet16bit {
    public static final long ID = 992;

    CharacterSetZHT16HKSCS31(String encodedMap) {
        super(ID, "ZHT16HKSCS31", 0x81, 0xFE, 0x40, 0xFE,
                CharacterSetMap.decode32(encodedMap));
    }
}
