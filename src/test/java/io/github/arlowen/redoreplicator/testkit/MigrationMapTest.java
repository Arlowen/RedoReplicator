/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.testkit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationMapTest {
    private static final Path MAP_PATH = Path.of("migration/openlogreplicator-source-map.tsv");

    @Test
    void validatesMigrationMapContract() throws IOException {
        List<String> lines = Files.readAllLines(MAP_PATH, StandardCharsets.UTF_8);
        assertEquals("source_path\ttarget_java_type\tstatus\tnotes", lines.get(0));
        assertEquals(211, lines.size(), "The fixed baseline contains 210 mapped source files");

        Set<String> sourcePaths = new HashSet<>();
        Set<String> validStatuses = Set.of("pending", "translated", "excluded");
        for (int index = 1; index < lines.size(); index++) {
            String[] columns = lines.get(index).split("\t", -1);
            assertEquals(4, columns.length, "Invalid column count at line " + (index + 1));
            assertTrue(columns[0].startsWith("src/"), "Invalid source path at line " + (index + 1));
            assertTrue(sourcePaths.add(columns[0]), "Duplicate source path: " + columns[0]);
            assertTrue(validStatuses.contains(columns[2]), "Invalid status at line " + (index + 1));
            assertFalse(columns[3].isBlank(), "Missing notes at line " + (index + 1));
            if (!columns[2].equals("excluded")) {
                assertFalse(columns[1].isBlank(), "Missing Java target at line " + (index + 1));
                assertFalse(columns[1].equals("-"), "Missing Java target at line " + (index + 1));
            }
        }
    }
}
