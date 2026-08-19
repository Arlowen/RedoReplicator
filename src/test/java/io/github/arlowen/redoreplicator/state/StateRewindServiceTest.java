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
import io.github.arlowen.redoreplicator.source.OracleDatabaseContext;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateRewindServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC);
    private static final DatabaseIdentity IDENTITY =
            new DatabaseIdentity(10, 20, 30);

    @TempDir
    private Path installationDirectory;

    @Test
    void rewindsToVerifiedSourceAndStartsANewOutputFile() throws Exception {
        ResolvedConfiguration configuration = configuration();
        save(configuration, 900, 8, 4096);
        Files.createDirectories(configuration.outputDirectory());
        Path historical = configuration.outputDirectory()
                .resolve("redo-000010.jsonl");
        Files.writeString(historical, "historical\n");
        Path status = installationDirectory.resolve("data/status.json");
        Files.writeString(status, "{\"safeScn\":\"900\"}\n");
        RewindSourceValidator validator = (
                connection, resolved, context, targetScn) ->
                new RewindSourceValidation(
                        new RedoPosition(
                                targetScn, 1, Seq.of(6), FileOffset.zero()),
                        List.of());

        Path safety = new StateRewindService(validator, CLOCK).rewind(
                null, configuration, databaseContext(), Scn.of(500));

        try (StateDatabase database = StateDatabase.open(
                configuration.stateDirectory())) {
            RuntimeState rewound = database.store().loadRuntimeState()
                    .orElseThrow();
            assertEquals("500",
                    rewound.durablePosition().scn().toDecimalString());
            assertEquals(1, rewound.durablePosition().thread());
            assertEquals(Seq.of(6), rewound.durablePosition().sequence());
            assertEquals(FileOffset.zero(),
                    rewound.durablePosition().offset());
            assertEquals(Optional.empty(), rewound.lowWatermarkPosition());
            assertEquals(11, rewound.jsonlFileNumber());
            assertEquals(0, rewound.jsonlFsyncOffset());
        }
        assertTrue(Files.isRegularFile(
                safety.resolve("redo-replicator.mv.db")));
        assertTrue(Files.isRegularFile(historical));
        assertFalse(Files.exists(status));
    }

    @Test
    void rejectsForwardMoveBeforeSourceValidation() throws Exception {
        ResolvedConfiguration configuration = configuration();
        save(configuration, 500, 3, 2048);
        AtomicInteger validations = new AtomicInteger();
        RewindSourceValidator validator = (
                connection, resolved, context, targetScn) -> {
            validations.incrementAndGet();
            throw new AssertionError("source validation must not run");
        };

        RedoRuntimeException error = assertThrows(
                RedoRuntimeException.class,
                () -> new StateRewindService(validator, CLOCK).rewind(
                        null, configuration, databaseContext(), Scn.of(600)));

        assertEquals(10040, error.getErrorCode());
        assertEquals(0, validations.get());
        try (StateDatabase database = StateDatabase.open(
                configuration.stateDirectory())) {
            assertEquals("500", database.store().loadRuntimeState()
                    .orElseThrow().durablePosition().scn().toDecimalString());
        }
    }

    @Test
    void removesFutureSchemaBeforeReplayingTheSameDdl() throws Exception {
        ResolvedConfiguration configuration = configuration();
        save(configuration, 900, 8, 4096);
        TableSchemaVersion initial = schemaVersion(500, "INITIAL");
        TableSchemaVersion firstDdl = schemaVersion(700, "ALTER");
        try (StateDatabase database = StateDatabase.open(
                configuration.stateDirectory())) {
            RuntimeState state = database.store().loadRuntimeState()
                    .orElseThrow();
            database.store().commitLwn(
                    state, List.of(initial, firstDdl));
        }
        RewindSourceValidator validator = (
                connection, resolved, context, targetScn) ->
                new RewindSourceValidation(
                        new RedoPosition(
                                targetScn, 1, Seq.of(6), FileOffset.zero()),
                        List.of(initial));

        new StateRewindService(validator, CLOCK).rewind(
                null, configuration, databaseContext(), Scn.of(500));

        try (StateDatabase database = StateDatabase.open(
                configuration.stateDirectory())) {
            TableSchemaVersion afterRewind = database.store().findSchemaAt(
                    "FREEPDB1", "APP", "ORDERS", Scn.of(900))
                    .orElseThrow();
            assertEquals(Scn.of(500), afterRewind.effectiveScn());

            RuntimeState replayed = new RuntimeState(
                    IDENTITY.databaseId(), IDENTITY.incarnation(),
                    IDENTITY.resetlogsId(),
                    new RedoPosition(
                            Scn.of(700), 1, Seq.of(7), FileOffset.of(4096)),
                    Optional.empty(), 9, 1024, "fingerprint",
                    OffsetDateTime.now(CLOCK));
            database.store().commitLwn(replayed, List.of(firstDdl));

            assertEquals(Scn.of(700), database.store().findSchemaAt(
                    "FREEPDB1", "APP", "ORDERS", Scn.of(900))
                    .orElseThrow().effectiveScn());
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
        Files.writeString(conf.resolve("redo-replicator.yaml"), """
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
                """);
        return new ConfigurationLoader().load(
                installationDirectory,
                Path.of("conf/redo-replicator.yaml"));
    }

    private static OracleDatabaseContext databaseContext() {
        return new OracleDatabaseContext(
                IDENTITY, Scn.of(1_000), "FREE", "FREEPDB1",
                "19.0.0.0.0", true, "ARCHIVELOG", true, true);
    }

    private static TableSchemaVersion schemaVersion(
            long scn, String ddlType) {
        return new TableSchemaVersion(
                "FREEPDB1", "APP", "ORDERS", 100, 101,
                Scn.of(scn), "{}", ddlType,
                ddlType + " TABLE APP.ORDERS", SchemaSource.REDO, false);
    }
}
