/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeScriptsTest {
    private static final List<String> SCRIPTS = List.of(
            "start.sh", "stop.sh", "restart.sh", "status.sh");

    @TempDir
    private Path installationDirectory;

    private Path binDirectory;
    private Path pidFile;

    @BeforeEach
    void prepareInstallation() throws IOException {
        binDirectory = Files.createDirectories(
                installationDirectory.resolve("bin"));
        pidFile = installationDirectory.resolve(
                "data/redo-replicator.pid");
        Path project = Path.of("").toAbsolutePath();
        for (String script : SCRIPTS) {
            Path installed = binDirectory.resolve(script);
            Files.copy(project.resolve("bin").resolve(script), installed,
                    StandardCopyOption.REPLACE_EXISTING);
            installed.toFile().setExecutable(true);
        }
        Path run = binDirectory.resolve("run.sh");
        Files.writeString(run, """
                #!/bin/sh
                trap 'exit 0' TERM
                while :; do
                    sleep 1
                done
                """, StandardCharsets.UTF_8);
        run.toFile().setExecutable(true);
    }

    @AfterEach
    void stopProcess() throws Exception {
        if (!Files.exists(pidFile)) {
            return;
        }
        run("stop.sh", "5");
    }

    @Test
    void startsReportsAndStopsOneBackgroundProcess() throws Exception {
        String started = run("start.sh", null);
        long pid = readPid();

        assertTrue(started.contains("started with PID " + pid));
        assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive());

        String duplicate = runExpecting("start.sh", null, 3);
        assertTrue(duplicate.contains("already running"));

        String status = run("status.sh", null);
        assertTrue(status.contains("RUNNING (PID " + pid + ")"));

        String stopped = run("stop.sh", "5");
        assertTrue(stopped.contains("stopped"));
        assertFalse(Files.exists(pidFile));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive)
                .orElse(false));

        runExpecting("status.sh", null, 1);
    }

    @Test
    void restartsAnExistingProcess() throws Exception {
        run("start.sh", null);
        long firstPid = readPid();

        String restarted = run("restart.sh", "5");
        long secondPid = readPid();

        assertTrue(restarted.contains("stopped"));
        assertTrue(restarted.contains("started with PID " + secondPid));
        assertNotEquals(firstPid, secondPid);
    }

    private long readPid() throws IOException {
        return Long.parseLong(Files.readString(pidFile).trim());
    }

    private String run(String script, String timeout) throws Exception {
        return runExpecting(script, timeout, 0);
    }

    private String runExpecting(
            String script, String timeout, int expectedStatus)
            throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                binDirectory.resolve(script).toString());
        builder.redirectErrorStream(true);
        if (timeout != null) {
            builder.environment().put(
                    "REDO_REPLICATOR_STOP_TIMEOUT_SECONDS", timeout);
        }
        Process process = builder.start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS),
                script + " did not finish");
        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertEquals(expectedStatus, process.exitValue(), output);
        return output;
    }
}
