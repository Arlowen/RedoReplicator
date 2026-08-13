/*
 * Java translation derived from the OpenLogReplicator WE8MSWIN1252 table in
 * src/locales/CharacterSet8bit.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

import java.nio.charset.Charset;
import java.util.Objects;

public final class CharacterSetWE8MSWIN1252 extends CharacterSet {
    public static final long ID = 178;

    private static final int[] UNICODE_MAP = buildMap();

    public CharacterSetWE8MSWIN1252() {
        super(ID, "WE8MSWIN1252");
    }

    @Override
    public String decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        StringBuilder result = new StringBuilder(data.length);
        for (byte value : data) {
            result.appendCodePoint(UNICODE_MAP[value & 0xFF]);
        }
        return result.toString();
    }

    private static int[] buildMap() {
        Charset charset = Charset.forName("windows-1252");
        int[] result = new int[256];
        byte[] encoded = new byte[1];
        for (int value = 0; value <= 0xFF; value++) {
            encoded[0] = (byte) value;
            result[value] = new String(encoded, charset).codePointAt(0);
        }
        result[0x81] = 0x0081;
        result[0x8D] = 0x008D;
        result[0x8F] = 0x008F;
        result[0x90] = 0x0090;
        result[0x9D] = 0x009D;
        return result;
    }
}
