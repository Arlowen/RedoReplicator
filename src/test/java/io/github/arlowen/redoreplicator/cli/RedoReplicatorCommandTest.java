/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoReplicatorCommandTest {
    @TempDir
    Path installationDirectory;

    @Test
    void exposesValidateModeAndDefaultConfigurationPath() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(new RedoReplicatorCommand());
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        assertEquals(0, commandLine.execute("--help"));
        String help = output.toString(StandardCharsets.UTF_8);
        assertTrue(help.contains("--validate"));
        assertTrue(help.contains("--backup"));
        assertTrue(help.contains("--restore"));
        assertTrue(help.contains("--rewind"));
        assertTrue(help.contains("conf/redo-replicator.yaml"));

        output.reset();
        assertEquals(0, commandLine.execute("--version"));
        assertTrue(output.toString(StandardCharsets.UTF_8)
                .contains("RedoReplicator development"));
    }

    @Test
    void reportsConfigurationFailureWithoutPrintingPassword()
            throws Exception {
        Path configurationFile = installationDirectory.resolve("invalid.yaml");
        Files.writeString(configurationFile, """
                database:
                  url: jdbc:oracle:thin:@//oracle:1521/FREE
                  username: REDO_REPLICATOR
                  password: top-secret
                  typo: value
                """);
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        PrintStream originalError = System.err;
        int exitCode;
        try {
            System.setErr(new PrintStream(
                    error, true, StandardCharsets.UTF_8));
            exitCode = new CommandLine(new RedoReplicatorCommand()).execute(
                    "--install-dir", installationDirectory.toString(),
                    "--file", configurationFile.toString(),
                    "--validate");
        } finally {
            System.setErr(originalError);
        }

        assertEquals(RedoReplicatorCommand.EXIT_CONFIGURATION, exitCode);
        String message = error.toString(StandardCharsets.UTF_8);
        assertTrue(message.contains("ERROR 30001"));
        assertFalse(message.contains("top-secret"));
    }
}
