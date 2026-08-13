/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import io.github.arlowen.redoreplicator.config.ConfigurationLoader;
import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateRestoreServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-13T10:00:00Z"), ZoneOffset.UTC);
    private static final DatabaseIdentity IDENTITY =
            new DatabaseIdentity(10, 20, 30);

    @TempDir
    private Path installationDirectory;

    @Test
    void restoresIdentityCheckedStateIntoANewOutputFile() throws Exception {
        ResolvedConfiguration configuration = configuration();
        save(configuration, 500, 3, 8192);
        Files.writeString(
                installationDirectory.resolve("data/status.json"),
                "{\"safeScn\":\"500\"}\n");
        Path backup = new StateBackupService(CLOCK).backup(configuration);

        save(configuration, 900, 8, 4096);
        Files.writeString(
                installationDirectory.resolve("data/status.json"),
                "{\"safeScn\":\"900\"}\n");
        Files.writeString(
                installationDirectory.resolve("conf/redo-replicator.yaml"),
                yaml().replace("password: secret", "password: changed"));
        Files.createDirectories(configuration.outputDirectory());
        Files.writeString(
                configuration.outputDirectory().resolve("redo-000010.jsonl"),
                "historical\n");

        Path safety = new StateRestoreService(CLOCK).restore(
                configuration, backup, IDENTITY);

        try (StateDatabase database = StateDatabase.open(
                configuration.stateDirectory())) {
            RuntimeState restored = database.store().loadRuntimeState()
                    .orElseThrow();
            assertEquals("500",
                    restored.durablePosition().scn().toDecimalString());
            assertEquals(11, restored.jsonlFileNumber());
            assertEquals(0, restored.jsonlFsyncOffset());
        }
        assertTrue(Files.readString(configuration.configurationFile())
                .contains("password: secret"));
        assertTrue(Files.isRegularFile(
                safety.resolve("redo-replicator.mv.db")));
        assertTrue(Files.isRegularFile(
                safety.resolve("redo-replicator.yaml")));
        assertTrue(Files.readString(
                        installationDirectory.resolve("data/status.json"))
                .contains("\"500\""));
        assertTrue(Files.isRegularFile(
                configuration.outputDirectory().resolve(
                        "redo-000010.jsonl")));
    }

    @Test
    void rejectsAnotherOracleIdentityBeforeReplacingState() throws Exception {
        ResolvedConfiguration configuration = configuration();
        save(configuration, 500, 3, 8192);
        Path backup = new StateBackupService(CLOCK).backup(configuration);
        save(configuration, 900, 8, 4096);

        RedoRuntimeException error = assertThrows(
                RedoRuntimeException.class,
                () -> new StateRestoreService(CLOCK).restore(
                        configuration, backup,
                        new DatabaseIdentity(99, 20, 30)));

        assertEquals(10043, error.getErrorCode());
        try (StateDatabase database = StateDatabase.open(
                configuration.stateDirectory())) {
            assertEquals("900", database.store().loadRuntimeState()
                    .orElseThrow().durablePosition().scn().toDecimalString());
        }
    }

    private void save(
            ResolvedConfiguration configuration,
            long scn,
            long fileNumber,
            long offset) throws Exception {
        try (StateDatabase database = StateDatabase.open(
                configuration.stateDirectory())) {
            RuntimeState state = new RuntimeState(
                    IDENTITY.databaseId(), IDENTITY.incarnation(),
                    IDENTITY.resetlogsId(),
                    new RedoPosition(
                            Scn.of(scn), 1, Seq.of(7), FileOffset.of(4096)),
                    Optional.empty(), fileNumber, offset, "fingerprint",
                    OffsetDateTime.now(CLOCK));
            database.store().commitLwn(state, List.of());
        }
    }

    private ResolvedConfiguration configuration() throws Exception {
        Path conf = Files.createDirectories(
                installationDirectory.resolve("conf"));
        Files.createDirectories(installationDirectory.resolve("data"));
        Files.writeString(conf.resolve("redo-replicator.yaml"), yaml());
        return new ConfigurationLoader().load(
                installationDirectory,
                Path.of("conf/redo-replicator.yaml"));
    }

    private static String yaml() {
        return """
                database:
                  url: jdbc:oracle:thin:@//oracle:1521/FREE
                  username: REDO_REPLICATOR
                  password: secret
                  redoPathMappings:
                    - oracle: /opt/oracle/oradata
                      local: /oracle/oradata
                capture:
                  includeTables:
                    - FREEPDB1.APP.USERS
                """;
    }
}
