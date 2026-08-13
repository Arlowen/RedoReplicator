/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

final class CharacterSetMap {
    private static final int HEX_DIGITS_PER_CHARACTER = 4;

    private CharacterSetMap() {
    }

    static int[] decode(String encodedMap) {
        int[] unicodeMap = new int[
                encodedMap.length() / HEX_DIGITS_PER_CHARACTER];
        for (int index = 0; index < unicodeMap.length; index++) {
            int start = index * HEX_DIGITS_PER_CHARACTER;
            unicodeMap[index] = Integer.parseInt(
                    encodedMap, start,
                    start + HEX_DIGITS_PER_CHARACTER, 16);
        }
        return unicodeMap;
    }
}
