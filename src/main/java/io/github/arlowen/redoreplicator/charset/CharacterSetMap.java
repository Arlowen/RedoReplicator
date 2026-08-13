/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.charset;

final class CharacterSetMap {
    private CharacterSetMap() {
    }

    static int[] decode(String encodedMap) {
        return decode(encodedMap, 4);
    }

    static int[] decode32(String encodedMap) {
        return decode(encodedMap, 8);
    }

    private static int[] decode(
            String encodedMap, int hexDigitsPerCharacter) {
        int[] unicodeMap = new int[
                encodedMap.length() / hexDigitsPerCharacter];
        for (int index = 0; index < unicodeMap.length; index++) {
            int start = index * hexDigitsPerCharacter;
            unicodeMap[index] = Integer.parseInt(
                    encodedMap, start,
                    start + hexDigitsPerCharacter, 16);
        }
        return unicodeMap;
    }
}
