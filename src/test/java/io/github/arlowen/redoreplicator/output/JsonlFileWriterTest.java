/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlFileWriterTest {
    @TempDir
    Path outputDirectory;

    @Test
    void writesMessagesAndReturnsFsyncedPosition() throws Exception {
        try (JsonlFileWriter writer = JsonlFileWriter.open(
                outputDirectory, 1_024, Optional.empty())) {
            JsonlPosition position = writer.writeAndSync(List.of(
                    json("{\"op\":\"begin\"}"),
                    json("{\"op\":\"commit\"}")));

            assertEquals(new JsonlPosition(1, 31), position);
            assertEquals("""
                    {"op":"begin"}
                    {"op":"commit"}
                    """, Files.readString(writer.currentFile()));
        }
    }

    @Test
    void rollsOnlyBetweenCompleteMessages() throws Exception {
        try (JsonlFileWriter writer = JsonlFileWriter.open(
                outputDirectory, 20, Optional.empty())) {
            JsonlPosition position = writer.writeAndSync(List.of(
                    json("{\"message\":1}"),
                    json("{\"message\":2}"),
                    json("{\"oversized_message\":3}")));

            assertEquals(new JsonlPosition(3, 24), position);
            assertEquals("{\"message\":1}\n", Files.readString(
                    outputDirectory.resolve("redo-000001.jsonl")));
            assertEquals("{\"message\":2}\n", Files.readString(
                    outputDirectory.resolve("redo-000002.jsonl")));
            assertEquals("{\"oversized_message\":3}\n", Files.readString(
                    outputDirectory.resolve("redo-000003.jsonl")));
        }
    }

    @Test
    void truncatesUncommittedTailToDurableOffset() throws Exception {
        Path file = outputDirectory.resolve("redo-000007.jsonl");
        Files.writeString(file, "safe\nuncommitted\n");

        try (JsonlFileWriter writer = JsonlFileWriter.open(
                outputDirectory, 1_024,
                Optional.of(new JsonlPosition(7, 5)))) {
            assertEquals(new JsonlPosition(7, 5), writer.position());
            assertEquals("safe\n", Files.readString(file));

            assertEquals(new JsonlPosition(7, 10),
                    writer.writeAndSync(List.of(json("next"))));
            assertEquals("safe\nnext\n", Files.readString(file));
        }
    }

    @Test
    void preservesLaterFilesAndContinuesWithNewNumber() throws Exception {
        Path durableFile = outputDirectory.resolve("redo-000004.jsonl");
        Path laterFile = outputDirectory.resolve("redo-000005.jsonl");
        Files.writeString(durableFile, "safe\ntail\n");
        Files.writeString(laterFile, "complete but not in H2\n");

        try (JsonlFileWriter writer = JsonlFileWriter.open(
                outputDirectory, 1_024,
                Optional.of(new JsonlPosition(4, 5)))) {
            assertEquals(new JsonlPosition(6, 0), writer.position());
            assertEquals("safe\n", Files.readString(durableFile));
            assertEquals("complete but not in H2\n",
                    Files.readString(laterFile));
            assertTrue(Files.exists(
                    outputDirectory.resolve("redo-000006.jsonl")));
        }
    }

    @Test
    void startsAfterExistingFilesWithoutH2State() throws Exception {
        Files.writeString(
                outputDirectory.resolve("redo-000003.jsonl"), "old\n");
        Files.writeString(
                outputDirectory.resolve("unrelated.txt"), "ignored");

        try (JsonlFileWriter writer = JsonlFileWriter.open(
                outputDirectory, 1_024, Optional.empty())) {
            assertEquals(new JsonlPosition(4, 0), writer.position());
            assertFalse(Files.exists(
                    outputDirectory.resolve("redo-000002.jsonl")));
        }
    }

    @Test
    void rejectsMissingOrShortDurableOutputAndRawLineBreaks()
            throws Exception {
        assertThrows(IOException.class,
                () -> JsonlFileWriter.open(
                        outputDirectory, 1_024,
                        Optional.of(new JsonlPosition(1, 10))));

        Path file = outputDirectory.resolve("redo-000001.jsonl");
        Files.writeString(file, "short");
        assertThrows(IOException.class,
                () -> JsonlFileWriter.open(
                        outputDirectory, 1_024,
                        Optional.of(new JsonlPosition(1, 10))));

        Files.delete(file);
        try (JsonlFileWriter writer = JsonlFileWriter.open(
                outputDirectory, 1_024, Optional.empty())) {
            assertThrows(IllegalArgumentException.class,
                    () -> writer.writeAndSync(List.of(json("bad\nline"))));
            assertThrows(IllegalArgumentException.class,
                    () -> writer.writeAndSync(List.of(new byte[0])));
        }
    }

    private static byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
