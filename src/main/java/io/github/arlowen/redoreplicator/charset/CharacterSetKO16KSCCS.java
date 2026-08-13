/*
 * Java translation derived from OpenLogReplicator CharacterSetKO16KSCCS in
 * src/locales/CharacterSetKO16KSCCS.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

public final class CharacterSetKO16KSCCS extends CharacterSet16bit {
    CharacterSetKO16KSCCS(long id, String encodedMap) {
        super(id, "KO16KSCCS", 0x84, 0xF9, 0x31, 0xFE, encodedMap);
    }
}
