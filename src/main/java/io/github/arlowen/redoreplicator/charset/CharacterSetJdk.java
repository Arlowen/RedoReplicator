/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.nio.charset.Charset;
import java.util.Objects;

public final class CharacterSetJdk extends CharacterSet {
    private final Charset charset;

    public CharacterSetJdk(long id, String name, Charset charset) {
        super(id, name);
        this.charset = Objects.requireNonNull(charset, "charset");
    }

    @Override
    public String decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        return new String(data, charset);
    }
}
