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
import io.github.arlowen.redoreplicator.runtime.RuntimeLock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class StateRestoreService {
    private static final Pattern OUTPUT_FILE = Pattern.compile(
            "redo-(\\d+)\\.jsonl");
    private static final String STATE_ENTRY =
            "state/redo-replicator.mv.db";
    private static final String CONFIGURATION_ENTRY =
            "configuration/redo-replicator.yaml";
    private static final String STATUS_ENTRY = "status/status.json";
    private static final String MANIFEST_ENTRY = "manifest.properties";

    private final Clock clock;

    public StateRestoreService() {
        clock = Clock.systemUTC();
    }

    StateRestoreService(Clock clock) {
        this.clock = clock;
    }

    public Path restore(
            ResolvedConfiguration configuration,
            Path backupFile,
            DatabaseIdentity oracleIdentity) throws IOException, SQLException {
        Path source = backupFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new RedoRuntimeException(
                    10044, "Backup file does not exist: " + source);
        }
        try (RuntimeLock ignored = RuntimeLock.acquire(
                configuration.stateDirectory())) {
            return restoreLocked(configuration, source, oracleIdentity);
        }
    }

    private Path restoreLocked(
            ResolvedConfiguration configuration,
            Path backupFile,
            DatabaseIdentity oracleIdentity) throws IOException, SQLException {
        Path workRoot = configuration.installationDirectory().resolve("data");
        Files.createDirectories(workRoot);
        Path work = Files.createTempDirectory(workRoot, "restore-");
        try {
            Path restoredStateDirectory = Files.createDirectories(
                    work.resolve("state"));
            Path restoredDatabase = restoredStateDirectory.resolve(
                    "redo-replicator.mv.db");
            Path restoredConfiguration = work.resolve(
                    "redo-replicator.yaml");
            Path restoredStatus = work.resolve("status.json");
            Properties manifest;
            try (ZipFile zip = new ZipFile(
                    backupFile.toFile(), StandardCharsets.UTF_8)) {
                manifest = readManifest(zip);
                validateIdentity(manifest, oracleIdentity);
                extract(zip, STATE_ENTRY, restoredDatabase,
                        manifest.getProperty("stateSha256"));
                extract(zip, CONFIGURATION_ENTRY, restoredConfiguration,
                        manifest.getProperty("configurationSha256"));
                String statusSha256 = manifest.getProperty("statusSha256");
                if (statusSha256 != null) {
                    extract(zip, STATUS_ENTRY, restoredStatus, statusSha256);
                }
            }

            ResolvedConfiguration restoredConfig =
                    new ConfigurationLoader().load(
                            configuration.installationDirectory(),
                            restoredConfiguration);
            if (!restoredConfig.stateDirectory().equals(
                    configuration.stateDirectory())) {
                throw new RedoRuntimeException(10044,
                        "Backup state.directory differs from the current configuration");
            }

            RuntimeState restoredState;
            try (StateDatabase database = StateDatabase.open(
                    restoredStateDirectory)) {
                restoredState = database.store().loadRuntimeState()
                        .orElseThrow(() -> new RedoRuntimeException(
                                10044, "Backup H2 has no runtime position"));
                validateIdentity(restoredState, oracleIdentity);
                if (!restoredState.durablePosition().scn().toDecimalString()
                        .equals(manifest.getProperty("safeScn"))) {
                    throw new RedoRuntimeException(
                            10044, "Backup manifest SCN differs from H2 state");
                }
                long nextOutputFile = Math.max(
                        restoredState.jsonlFileNumber() + 1,
                        highestOutputFile(configuration.outputDirectory()) + 1);
                RuntimeState ready = new RuntimeState(
                        restoredState.databaseId(),
                        restoredState.incarnation(),
                        restoredState.resetlogsId(),
                        restoredState.durablePosition(),
                        restoredState.lowWatermarkPosition(),
                        nextOutputFile, 0,
                        restoredState.configFingerprint(),
                        OffsetDateTime.now(clock));
                database.store().commitLwn(ready, List.of());
            }

            return replaceActiveFiles(
                    configuration, restoredDatabase,
                    restoredConfiguration,
                    Files.exists(restoredStatus)
                            ? Optional.of(restoredStatus)
                            : Optional.empty());
        } finally {
            deleteTree(work);
        }
    }

    private Path replaceActiveFiles(
            ResolvedConfiguration configuration,
            Path restoredDatabase,
            Path restoredConfiguration,
            Optional<Path> restoredStatus) throws IOException {
        long timestamp = clock.instant().toEpochMilli();
        Path safety = Files.createDirectories(
                configuration.installationDirectory()
                        .resolve("data/backups/restore-safety-" + timestamp));
        Path activeDatabase = configuration.stateDirectory()
                .resolve("redo-replicator.mv.db");
        Path activeConfiguration = configuration.configurationFile();
        Path activeStatus = configuration.installationDirectory()
                .resolve("data/status.json");
        boolean hadDatabase = Files.isRegularFile(activeDatabase);
        boolean hadConfiguration = Files.isRegularFile(activeConfiguration);
        boolean hadStatus = Files.isRegularFile(activeStatus);
        if (hadDatabase) {
            Files.copy(activeDatabase, safety.resolve("redo-replicator.mv.db"));
        }
        if (hadConfiguration) {
            Files.copy(activeConfiguration,
                    safety.resolve("redo-replicator.yaml"));
        }
        if (hadStatus) {
            Files.copy(activeStatus, safety.resolve("status.json"));
        }

        try {
            Files.move(restoredDatabase, activeDatabase,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            Files.move(restoredConfiguration, activeConfiguration,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            if (restoredStatus.isPresent()) {
                Files.move(restoredStatus.orElseThrow(), activeStatus,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(activeStatus);
            }
        } catch (IOException e) {
            rollback(activeDatabase, safety.resolve("redo-replicator.mv.db"),
                    hadDatabase, e);
            rollback(activeConfiguration,
                    safety.resolve("redo-replicator.yaml"),
                    hadConfiguration, e);
            rollback(activeStatus, safety.resolve("status.json"),
                    hadStatus, e);
            throw e;
        }
        return safety;
    }

    private static Properties readManifest(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry(MANIFEST_ENTRY);
        if (entry == null) {
            throw new RedoRuntimeException(10044,
                    "Backup manifest is missing");
        }
        Properties manifest = new Properties();
        try (InputStream input = zip.getInputStream(entry)) {
            manifest.load(input);
        }
        if (!"1".equals(manifest.getProperty("formatVersion"))) {
            throw new RedoRuntimeException(10044,
                    "Unsupported backup format version");
        }
        return manifest;
    }

    private static void extract(
            ZipFile zip,
            String entryName,
            Path target,
            String expectedSha256) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null || expectedSha256 == null) {
            throw new RedoRuntimeException(
                    10044, "Backup entry is missing: " + entryName);
        }
        MessageDigest digest = sha256();
        try (InputStream input = zip.getInputStream(entry);
             var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                output.write(buffer, 0, length);
                digest.update(buffer, 0, length);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(expectedSha256)) {
            throw new RedoRuntimeException(
                    10044, "Backup SHA-256 mismatch: " + entryName);
        }
    }

    private static void validateIdentity(
            Properties manifest,
            DatabaseIdentity identity) {
        try {
            DatabaseIdentity backupIdentity = new DatabaseIdentity(
                    Long.parseLong(manifest.getProperty("databaseId")),
                    Long.parseLong(manifest.getProperty("incarnation")),
                    Long.parseLong(manifest.getProperty("resetlogsId")));
            if (!backupIdentity.equals(identity)) {
                throw new RedoRuntimeException(10043,
                        "Backup belongs to a different Oracle database incarnation");
            }
        } catch (NullPointerException | NumberFormatException e) {
            throw new RedoRuntimeException(
                    10044, "Backup manifest identity is invalid");
        }
    }

    private static void validateIdentity(
            RuntimeState state,
            DatabaseIdentity identity) {
        DatabaseIdentity stateIdentity = new DatabaseIdentity(
                state.databaseId(), state.incarnation(), state.resetlogsId());
        if (!stateIdentity.equals(identity)) {
            throw new RedoRuntimeException(10043,
                    "Backup H2 belongs to a different Oracle database incarnation");
        }
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

    private static void rollback(
            Path active,
            Path safety,
            boolean existed,
            IOException failure) {
        try {
            if (existed) {
                Files.copy(safety, active,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(active);
            }
        } catch (IOException rollbackError) {
            failure.addSuppressed(rollbackError);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 17 does not provide SHA-256", e);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
