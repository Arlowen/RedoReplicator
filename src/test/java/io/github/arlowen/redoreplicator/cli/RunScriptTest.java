/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunScriptTest {
    @TempDir
    Path installationDirectory;

    @Test
    void launchesMainClassWithPackagedJarAndLibraries() throws Exception {
        Path bin = Files.createDirectories(
                installationDirectory.resolve("bin"));
        Path lib = Files.createDirectories(
                installationDirectory.resolve("lib"));
        Path conf = Files.createDirectories(
                installationDirectory.resolve("conf"));
        Path run = bin.resolve("run.sh");
        Files.copy(
                Path.of("bin/run.sh"), run,
                StandardCopyOption.REPLACE_EXISTING);
        run.toFile().setExecutable(true);
        Files.write(lib.resolve("redo-replicator.jar"), new byte[]{1});
        Files.writeString(conf.resolve("logback.xml"), "<configuration/>");

        Path arguments = installationDirectory.resolve("java-args.txt");
        Path java = installationDirectory.resolve("openjdk-17");
        Files.writeString(java, """
                #!/bin/sh
                if [ "$1" = "-version" ]; then
                    echo 'openjdk version "17.0.12"' >&2
                    exit 0
                fi
                printf '%s\n' "$@" > "$JAVA_ARGS_FILE"
                """, StandardCharsets.UTF_8);
        java.toFile().setExecutable(true);

        ProcessBuilder builder = new ProcessBuilder(
                run.toString(), "--help");
        builder.environment().put(
                "REDO_REPLICATOR_JAVA", java.toString());
        builder.environment().put(
                "JAVA_ARGS_FILE", arguments.toString());
        Process process = builder.start();
        assertEquals(0, process.waitFor());

        assertEquals(List.of(
                        "-Dlogback.configurationFile="
                                + conf.resolve("logback.xml"),
                        "-Dredo.replicator.log.dir="
                                + installationDirectory.resolve("logs"),
                        "-cp",
                        lib.resolve("redo-replicator.jar")
                                + ":" + lib + "/*",
                        "io.github.arlowen.redoreplicator.cli.RedoReplicatorMain",
                        "--install-dir",
                        installationDirectory.toString(),
                        "--help"),
                Files.readAllLines(arguments));
    }
}
