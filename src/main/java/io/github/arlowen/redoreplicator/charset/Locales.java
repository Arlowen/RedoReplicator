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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Locales {
    private final Map<Long, CharacterSet> characterSets;

    public Locales() {
        characterSets = new HashMap<>();
        register7BitCharacterSets();
        register8BitCharacterSets();
        register16BitCharacterSets();
        registerJapaneseAndKoreanCharacterSets();
        register(new CharacterSetAL32UTF8());
        register(new CharacterSetUTF8());
        register(new CharacterSetAL16UTF16());
        register(new CharacterSetZHS16GBK());
        registerZhs32Gb18030();
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

    private void register8BitCharacterSets() {
        InputStream input = Locales.class.getResourceAsStream(
                "oracle-8bit-catalog.tsv");
        if (input == null) {
            throw new IllegalStateException(
                    "Oracle 8-bit character-set catalog is missing");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", 3);
                register(new CharacterSet8bit(
                        Long.parseLong(fields[0]), fields[1], fields[2]));
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load Oracle 8-bit character-set catalog", e);
        }
    }

    private void register16BitCharacterSets() {
        InputStream input = Locales.class.getResourceAsStream(
                "oracle-16bit-catalog.tsv");
        if (input == null) {
            throw new IllegalStateException(
                    "Oracle 16-bit character-set catalog is missing");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", 7);
                register(new CharacterSet16bit(
                        Long.parseLong(fields[0]), fields[1],
                        Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]),
                        Integer.parseInt(fields[4]),
                        Integer.parseInt(fields[5]), fields[6]));
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load Oracle 16-bit character-set catalog", e);
        }
    }

    private void registerJapaneseAndKoreanCharacterSets() {
        Map<String, String> maps = loadCharacterSetMaps(
                "oracle-east-asian-catalog.tsv");
        String eucMap2 = maps.get("JA16EUC_2b");
        String eucMap3 = maps.get("JA16EUC_3b");
        String sjisMap = maps.get("JA16SJIS_2b");
        register(new CharacterSetJA16EUC(
                830, "JA16EUC", eucMap2, eucMap3));
        register(new CharacterSetJA16EUC(
                831, "JA16EUCYEN", eucMap2, eucMap3));
        register(new CharacterSetJA16SJIS(
                832, "JA16SJIS", sjisMap));
        register(new CharacterSetJA16SJIS(
                834, "JA16SJISYEN", sjisMap));
        register(new CharacterSetJA16EUCTILDE(
                837, eucMap2, eucMap3));
        register(new CharacterSetJA16SJISTILDE(838, sjisMap));
        register(new CharacterSetKO16KSCCS(
                845, maps.get("KO16KSCCS_2b")));
    }

    private void registerZhs32Gb18030() {
        Map<String, String> maps = loadCharacterSetMaps(
                "oracle-gb18030-catalog.tsv");
        register(new CharacterSetZHS32GB18030(
                maps.get("ZHS32GB18030_2b"),
                maps.get("ZHS32GB18030_4b1"),
                maps.get("ZHS32GB18030_4b2")));
    }

    private Map<String, String> loadCharacterSetMaps(String resourceName) {
        InputStream input = Locales.class.getResourceAsStream(resourceName);
        if (input == null) {
            throw new IllegalStateException(
                    "Oracle character-set map resource is missing: "
                            + resourceName);
        }
        Map<String, String> maps = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", 2);
                maps.put(fields[0], fields[1]);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load Oracle character-set maps: "
                            + resourceName, e);
        }
        return maps;
    }
}
