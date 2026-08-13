/*
 * Java translation derived from OpenLogReplicator CharacterSet in
 * src/locales/CharacterSet.{h,cpp}.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.util.Objects;

public abstract class CharacterSet {
    protected static final int UNKNOWN_CHARACTER = 0xFFFD;

    private final long id;
    private final String name;

    protected CharacterSet(long id, String name) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public abstract String decode(byte[] data);
}
