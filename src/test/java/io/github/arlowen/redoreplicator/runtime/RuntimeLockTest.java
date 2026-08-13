/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.error.BootException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeLockTest {
    @TempDir
    Path stateDirectory;

    @Test
    void preventsConcurrentRuntimeAndReleasesForRestart() throws Exception {
        Path lockFile = stateDirectory.resolve("redo-replicator.lock");

        try (RuntimeLock ignored = RuntimeLock.acquire(stateDirectory)) {
            assertTrue(Files.readString(lockFile).matches("[0-9]+\\R"));
            BootException error = assertThrows(
                    BootException.class,
                    () -> RuntimeLock.acquire(stateDirectory));
            assertEquals(10010, error.getErrorCode());
        }

        assertEquals("", Files.readString(lockFile));
        try (RuntimeLock ignored = RuntimeLock.acquire(stateDirectory)) {
            assertFalse(Files.readString(lockFile).isBlank());
        }
    }

    @Test
    void refusesSymbolicLinkLockFile() throws Exception {
        Path victim = stateDirectory.resolve("victim.txt");
        Path lockFile = stateDirectory.resolve("redo-replicator.lock");
        Files.writeString(victim, "keep");
        Files.createSymbolicLink(lockFile, victim);

        BootException error = assertThrows(
                BootException.class,
                () -> RuntimeLock.acquire(stateDirectory));

        assertEquals(10010, error.getErrorCode());
        assertEquals("keep", Files.readString(victim));
    }
}
