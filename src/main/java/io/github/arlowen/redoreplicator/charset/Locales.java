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
}
