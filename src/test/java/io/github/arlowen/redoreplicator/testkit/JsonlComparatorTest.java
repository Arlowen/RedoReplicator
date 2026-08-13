/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.testkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlComparatorTest {
    @TempDir
    Path tempDir;

    @Test
    void ignoresObjectKeyOrderAndWhitespace() throws IOException {
        Path expected = write("expected.jsonl", """
                {"scn":100,"payload":[{"op":"c","after":{"ID":1,"NAME":"A"}}]}
                {"xid":"0x0001.001.00000001","payload":[{"op":"commit"}]}
                """);
        Path actual = write("actual.jsonl", """
                { "payload" : [ { "after" : { "NAME" : "A", "ID" : 1 }, "op" : "c" } ], "scn" : 100 }
                {"payload":[{"op":"commit"}],"xid":"0x0001.001.00000001"}
                """);

        assertTrue(JsonlComparator.compare(expected, actual).isEmpty());
    }

    @Test
    void detectsArrayOrderAndValueChanges() throws IOException {
        Path expected = write("expected.jsonl",
                "{\"payload\":[{\"op\":\"c\",\"after\":{\"ID\":1}},{\"op\":\"d\"}]}\n");
        Path actual = write("actual.jsonl",
                "{\"payload\":[{\"op\":\"d\"},{\"op\":\"c\",\"after\":{\"ID\":2}}]}\n");

        List<String> differences = JsonlComparator.compare(expected, actual);

        assertEquals(1, differences.size());
        assertTrue(differences.get(0).contains("line 1"));
    }

    @Test
    void detectsDifferentLineCounts() throws IOException {
        Path expected = write("expected.jsonl", "{\"payload\":[{\"op\":\"begin\"}]}\n"
                + "{\"payload\":[{\"op\":\"commit\"}]}\n");
        Path actual = write("actual.jsonl", "{\"payload\":[{\"op\":\"begin\"}]}\n");

        List<String> differences = JsonlComparator.compare(expected, actual);

        assertEquals(List.of("Actual output ended before expected line 2"), differences);
    }

    @Test
    void rejectsInvalidJsonAndBlankLines() throws IOException {
        Path valid = write("valid.jsonl", "{\"payload\":[]}\n");
        Path invalid = write("invalid.jsonl", "{not-json}\n");
        Path blank = write("blank.jsonl", "\n");
        Path trailing = write("trailing.jsonl", "{} {}\n");
        Path scalar = write("scalar.jsonl", "42\n");

        IOException invalidError = assertThrows(IOException.class,
                () -> JsonlComparator.compare(valid, invalid));
        IOException blankError = assertThrows(IOException.class,
                () -> JsonlComparator.compare(valid, blank));
        IOException trailingError = assertThrows(IOException.class,
                () -> JsonlComparator.compare(valid, trailing));
        IOException scalarError = assertThrows(IOException.class,
                () -> JsonlComparator.compare(valid, scalar));

        assertTrue(invalidError.getMessage().contains("invalid JSON at line 1"));
        assertTrue(blankError.getMessage().contains("blank line at 1"));
        assertTrue(trailingError.getMessage().contains("invalid JSON at line 1"));
        assertTrue(scalarError.getMessage().contains("JSON object at line 1"));
        assertFalse(invalidError.getMessage().contains("not-json"));
    }

    private Path write(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}
