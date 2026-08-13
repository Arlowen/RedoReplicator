/*
 * Java translation derived from OpenLogReplicator Locales in
 * src/locales/Locales.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Locales {
    private final Map<Long, CharacterSet> characterSets;

    public Locales() {
        characterSets = new HashMap<>();
        register7BitCharacterSets();
        register(new CharacterSetAL32UTF8());
        register(new CharacterSetUTF8());
        register(new CharacterSetAL16UTF16());
        register(new CharacterSetZHS16GBK());
        register(new CharacterSetWE8MSWIN1252());
    }

    public CharacterSet require(long id) {
        CharacterSet characterSet = characterSets.get(id);
        if (characterSet == null) {
            throw new IllegalArgumentException(
                    "Oracle character set id is not translated: " + id);
        }
        return characterSet;
    }

    public CharacterSet require(long id, String name) {
        Objects.requireNonNull(name, "name");
        CharacterSet characterSet = require(id);
        if (!characterSet.name().equals(name.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Oracle character set identity mismatch: " + id + " is "
                            + name + ", expected " + characterSet.name());
        }
        return characterSet;
    }

    private void register(CharacterSet characterSet) {
        characterSets.put(characterSet.id(), characterSet);
    }

    private void register7BitCharacterSets() {
        register(new CharacterSet7bit(1, "US7ASCII"));
        register(new CharacterSet7bit(11, "D7DEC",
                0x40, 0xA7, 0x5B, 0xC4, 0x5C, 0xD6, 0x5D, 0xDC,
                0x7B, 0xE4, 0x7C, 0xF6, 0x7D, 0xFC, 0x7E, 0xDF));
        register(new CharacterSet7bit(13, "S7DEC",
                0x40, 0xC9, 0x5B, 0xC4, 0x5C, 0xD6, 0x5D, 0xC5,
                0x5E, 0xDC, 0x60, 0xE9, 0x7B, 0xE4, 0x7C, 0xF6,
                0x7D, 0xE5, 0x7E, 0xFC));
        register(new CharacterSet7bit(14, "E7DEC",
                0x23, 0xA3, 0x40, 0xA7, 0x5B, 0xA1, 0x5C, 0xD1,
                0x5D, 0xBF, 0x7B, 0xB0, 0x7C, 0xF1, 0x7D, 0xE7));
        register(new CharacterSet7bit(15, "SF7ASCII",
                0x5B, 0xC4, 0x5C, 0xD6, 0x5D, 0xC5,
                0x7B, 0xE4, 0x7C, 0xF6, 0x7D, 0xE5));
        register(new CharacterSet7bit(16, "NDK7DEC",
                0x40, 0xC4, 0x5B, 0xC6, 0x5C, 0xD8, 0x5D, 0xC5,
                0x5E, 0xDC, 0x60, 0xE4, 0x7B, 0xE6, 0x7C, 0xF8,
                0x7D, 0xE5, 0x7E, 0xFC));
        register(new CharacterSet7bit(17, "I7DEC",
                0x23, 0xA3, 0x40, 0xA7, 0x5B, 0xB0, 0x5C, 0xE7,
                0x5D, 0xE9, 0x60, 0xF9, 0x7B, 0xE0, 0x7C, 0xF2,
                0x7D, 0xE8, 0x7E, 0xEC));
        register(new CharacterSet7bit(21, "SF7DEC",
                0x5B, 0xC4, 0x5C, 0xD6, 0x5D, 0xC5, 0x5E, 0xDC,
                0x60, 0xE9, 0x7B, 0xE4, 0x7C, 0xF6, 0x7D, 0xE5,
                0x7E, 0xFC));
        register(new CharacterSet7bit(202, "E7SIEMENS9780X",
                0x5B, 0xA1, 0x5C, 0xD1, 0x5D, 0xBF, 0x7B, 0xB4,
                0x7C, 0xF1, 0x7D, 0xE7, 0x7E, 0xA8));
        register(new CharacterSet7bit(203, "S7SIEMENS9780X",
                0x24, 0xA4, 0x40, 0xC9, 0x5B, 0xC4, 0x5C, 0xD6,
                0x5D, 0xC5, 0x5E, 0xDC, 0x60, 0xE9, 0x7B, 0xE4,
                0x7C, 0xF6, 0x7D, 0xE5, 0x7E, 0xFC));
        register(new CharacterSet7bit(204, "DK7SIEMENS9780X",
                0x5B, 0xC6, 0x5C, 0xD8, 0x5D, 0xC5, 0x5E, 0xDC,
                0x7B, 0xE6, 0x7C, 0xF8, 0x7D, 0xE5, 0x7E, 0xFC));
        register(new CharacterSet7bit(206, "I7SIEMENS9780X",
                0x23, 0xA3, 0x40, 0xA7, 0x5B, 0xB0, 0x5C, 0xE7,
                0x5D, 0xE9, 0x60, 0xF9, 0x7B, 0xE0, 0x7C, 0xF2,
                0x7D, 0xE8, 0x7E, 0xEC));
        register(new CharacterSet7bit(205, "N7SIEMENS9780X",
                0x5B, 0xC6, 0x5C, 0xD8, 0x5D, 0xC5, 0x5E, 0xDC,
                0x7B, 0xE6, 0x7C, 0xF8, 0x7D, 0xE5, 0x7E, 0xFC));
        register(new CharacterSet7bit(207, "D7SIEMENS9780X",
                0x40, 0xA7, 0x5B, 0xC4, 0x5C, 0xD6, 0x5D, 0xDC,
                0x7B, 0xE4, 0x7C, 0xF6, 0x7D, 0xFC, 0x7E, 0xDF));
    }
}
