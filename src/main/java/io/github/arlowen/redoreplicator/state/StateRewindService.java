/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import io.github.arlowen.redoreplicator.config.ConfigurationFingerprint;
import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.runtime.RuntimeLock;
import io.github.arlowen.redoreplicator.source.OracleDatabaseContext;
import io.github.arlowen.redoreplicator.source.OracleRewindSourceValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class StateRewindService {
    private static final Pattern OUTPUT_FILE = Pattern.compile(
            "redo-(\\d+)\\.jsonl");

    private final RewindSourceValidator sourceValidator;
    private final ConfigurationFingerprint configurationFingerprint;
    private final Clock clock;

    public StateRewindService() {
        this(new OracleRewindSourceValidator(), Clock.systemUTC());
    }

    StateRewindService(
            RewindSourceValidator sourceValidator,
            Clock clock) {
        this.sourceValidator = sourceValidator;
        configurationFingerprint = new ConfigurationFingerprint();
        this.clock = clock;
    }

    public Path rewind(
            Connection connection,
            ResolvedConfiguration configuration,
            OracleDatabaseContext databaseContext,
            Scn targetScn) throws IOException, SQLException {
        try (RuntimeLock ignored = RuntimeLock.acquire(
                configuration.stateDirectory())) {
            RuntimeState current;
            try (StateDatabase database = StateDatabase.open(
                    configuration.stateDirectory())) {
                database.store().validateDatabaseIdentity(
                        databaseContext.identity());
                current = database.store().loadRuntimeState()
                        .orElseThrow(() -> new RedoRuntimeException(
                                10044, "State database has no runtime position"));
                if (targetScn.compareTo(
                        current.durablePosition().scn()) > 0) {
                    throw new RedoRuntimeException(10040,
                            "Rewind SCN " + targetScn
                                    + " is ahead of safe SCN "
                                    + current.durablePosition().scn());
                }
            }

            RewindSourceValidation source = sourceValidator.validate(
                    connection, configuration, databaseContext, targetScn);
            long nextOutputFile = Math.max(
                    current.jsonlFileNumber() + 1,
                    highestOutputFile(configuration.outputDirectory()) + 1);
            RuntimeState rewound = new RuntimeState(
                    current.databaseId(), current.incarnation(),
                    current.resetlogsId(), source.position(), Optional.empty(),
                    nextOutputFile, 0,
                    configurationFingerprint.calculate(configuration),
                    OffsetDateTime.now(clock));

            Path safetyDirectory = createSafetyCopy(configuration);
            try (StateDatabase database = StateDatabase.open(
                    configuration.stateDirectory())) {
                List<TableSchemaVersion> additions = new ArrayList<>();
                for (TableSchemaVersion version : source.schemaVersions()) {
                    Optional<TableSchemaVersion> existing =
                            database.store().findSchemaAt(
                                    version.container(), version.owner(),
                                    version.table(), version.effectiveScn());
                    if (existing.isEmpty()
                            || !existing.orElseThrow().effectiveScn().equals(
                            version.effectiveScn())) {
                        additions.add(version);
                    }
                }
                database.store().commitLwn(rewound, additions);
            }
            Files.deleteIfExists(configuration.installationDirectory()
                    .resolve("data/status.json"));
            return safetyDirectory;
        }
    }

    private Path createSafetyCopy(ResolvedConfiguration configuration)
            throws IOException {
        Path databaseFile = configuration.stateDirectory()
                .resolve("redo-replicator.mv.db");
        Path safetyDirectory = Files.createDirectories(
                configuration.installationDirectory()
                        .resolve("data/backups/rewind-safety-"
                                + clock.instant().toEpochMilli()));
        Files.copy(databaseFile,
                safetyDirectory.resolve("redo-replicator.mv.db"));
        return safetyDirectory;
    }

    private static long highestOutputFile(Path outputDirectory)
            throws IOException {
        if (!Files.isDirectory(outputDirectory)) {
            return 0;
        }
        long highest = 0;
        try (Stream<Path> paths = Files.list(outputDirectory)) {
            for (Path path : paths.toList()) {
                Matcher matcher = OUTPUT_FILE.matcher(
                        path.getFileName().toString());
                if (matcher.matches() && Files.isRegularFile(path)) {
                    try {
                        highest = Math.max(
                                highest, Long.parseLong(matcher.group(1)));
                    } catch (NumberFormatException ignored) {
                        // Not a valid RedoReplicator output file number.
                    }
                }
            }
        }
        return highest;
    }
}
