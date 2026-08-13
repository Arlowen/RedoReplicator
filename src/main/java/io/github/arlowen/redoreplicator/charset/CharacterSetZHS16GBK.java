/*
 * Java translation derived from OpenLogReplicator CharacterSetZHS16GBK in
 * src/locales/CharacterSetZHS16GBK.{h,cpp}.
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

public final class CharacterSetZHS16GBK extends CharacterSet {
    public static final long ID = 852;

    private static final int BYTE1_MIN = 0x81;
    private static final int BYTE1_MAX = 0xFE;
    private static final int BYTE2_MIN = 0x40;
    private static final int BYTE2_MAX = 0xFE;
    private static final int BYTE2_RANGE = BYTE2_MAX - BYTE2_MIN + 1;
    private static final int[] UNICODE_MAP = buildMap();

    public CharacterSetZHS16GBK() {
        super(ID, "ZHS16GBK");
    }

    @Override
    public String decode(byte[] data) {
        Objects.requireNonNull(data, "data");
        StringBuilder result = new StringBuilder();
        int offset = 0;
        while (offset < data.length) {
            int byte1 = data[offset] & 0xFF;
            offset++;
            if (byte1 <= 0x7F) {
                result.appendCodePoint(byte1);
                continue;
            }
            if (byte1 == 0x80) {
                result.append('\u20AC');
                continue;
            }
            if (offset == data.length) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }

            int byte2 = data[offset] & 0xFF;
            offset++;
            if (byte1 < BYTE1_MIN || byte1 > BYTE1_MAX
                    || byte2 < BYTE2_MIN || byte2 > BYTE2_MAX) {
                result.appendCodePoint(UNKNOWN_CHARACTER);
                continue;
            }
            int index = (byte1 - BYTE1_MIN) * BYTE2_RANGE
                    + byte2 - BYTE2_MIN;
            result.appendCodePoint(UNICODE_MAP[index]);
        }
        return result.toString();
    }

    private static int[] buildMap() {
        Charset charset = Charset.forName("GBK");
        int[] result = new int[
                (BYTE1_MAX - BYTE1_MIN + 1) * BYTE2_RANGE];
        byte[] encoded = new byte[2];
        int index = 0;
        for (int byte1 = BYTE1_MIN; byte1 <= BYTE1_MAX; byte1++) {
            encoded[0] = (byte) byte1;
            for (int byte2 = BYTE2_MIN; byte2 <= BYTE2_MAX; byte2++) {
                encoded[1] = (byte) byte2;
                String decoded = new String(encoded, charset);
                int codePoint = UNKNOWN_CHARACTER;
                if (decoded.codePointCount(0, decoded.length()) == 1) {
                    codePoint = decoded.codePointAt(0);
                }
                result[index] = codePoint;
                index++;
            }
        }
        // These are the only JDK 17 GBK entries that differ from the complete
        // OpenLogReplicator ZHS16GBK table at the pinned upstream commit.
        result[(0xA2 - BYTE1_MIN) * BYTE2_RANGE + 0xE3 - BYTE2_MIN] =
                0xE76C;
        result[(0xA8 - BYTE1_MIN) * BYTE2_RANGE + 0x92 - BYTE2_MIN] =
                0x2295;
        return result;
    }
}
