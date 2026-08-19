/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledOnOs(OS.LINUX)
class JsonlFileWriterDiskFullTest {
    @TempDir
    Path outputDirectory;

    @Test
    void propagatesNoSpaceLeftOnDevice() throws Exception {
        Files.createSymbolicLink(
                outputDirectory.resolve("redo-000001.jsonl"),
                Path.of("/dev/full"));

        try (JsonlFileWriter writer = JsonlFileWriter.open(
                outputDirectory, 1_024, Optional.empty())) {
            assertThrows(IOException.class,
                    () -> writer.writeAndSync(List.of(
                            "{\"op\":\"commit\"}".getBytes(
                                    StandardCharsets.UTF_8))));
        }
    }
}
