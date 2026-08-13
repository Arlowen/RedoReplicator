/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.runtime.RuntimeLock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class StateBackupService {
    private static final String STATE_ENTRY =
            "state/redo-replicator.mv.db";
    private static final String CONFIGURATION_ENTRY =
            "configuration/redo-replicator.yaml";
    private static final String STATUS_ENTRY = "status/status.json";
    private static final String MANIFEST_ENTRY = "manifest.properties";
    private static final Set<PosixFilePermission> PRIVATE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final Clock clock;

    public StateBackupService() {
        clock = Clock.systemUTC();
    }

    StateBackupService(Clock clock) {
        this.clock = clock;
    }

    public Path backup(ResolvedConfiguration configuration)
            throws IOException, SQLException {
        try (RuntimeLock ignored = RuntimeLock.acquire(
                configuration.stateDirectory())) {
            return createBackup(configuration);
        }
    }

    private Path createBackup(ResolvedConfiguration configuration)
            throws IOException, SQLException {
        Path databaseFile = configuration.stateDirectory()
                .resolve("redo-replicator.mv.db");
        if (!Files.isRegularFile(databaseFile)) {
            throw new RedoRuntimeException(
                    10044, "State database does not exist: " + databaseFile);
        }
        RuntimeState runtimeState;
        int schemaVersion;
        try (StateDatabase database = StateDatabase.open(
                configuration.stateDirectory())) {
            runtimeState = database.store().loadRuntimeState()
                    .orElseThrow(() -> new RedoRuntimeException(
                            10044, "State database has no runtime position"));
            schemaVersion = database.schemaVersion();
        }

        Path backupDirectory = configuration.installationDirectory()
                .resolve("data/backups");
        Files.createDirectories(backupDirectory);
        String fileName = "redo-replicator-"
                + clock.instant().toEpochMilli() + ".zip";
        Path backupFile = backupDirectory.resolve(fileName);
        Path temporaryFile = backupFile.resolveSibling(fileName + ".tmp");

        Properties manifest = new Properties();
        manifest.setProperty("formatVersion", "1");
        manifest.setProperty("databaseId",
                Long.toString(runtimeState.databaseId()));
        manifest.setProperty("incarnation",
                Long.toString(runtimeState.incarnation()));
        manifest.setProperty("resetlogsId",
                Long.toString(runtimeState.resetlogsId()));
        manifest.setProperty("safeScn",
                runtimeState.durablePosition().scn().toDecimalString());
        manifest.setProperty("schemaVersion",
                Integer.toString(schemaVersion));

        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(temporaryFile),
                StandardCharsets.UTF_8)) {
            manifest.setProperty("stateSha256",
                    writeFile(zip, STATE_ENTRY, databaseFile));
            manifest.setProperty("configurationSha256",
                    writeFile(zip, CONFIGURATION_ENTRY,
                            configuration.configurationFile()));
            Path statusFile = configuration.installationDirectory()
                    .resolve("data/status.json");
            if (Files.isRegularFile(statusFile)) {
                manifest.setProperty("statusSha256",
                        writeFile(zip, STATUS_ENTRY, statusFile));
            }
            ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
            manifest.store(manifestBytes, "RedoReplicator state backup");
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zip.write(manifestBytes.toByteArray());
            zip.closeEntry();
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporaryFile);
            throw e;
        }
        Files.move(temporaryFile, backupFile,
                StandardCopyOption.ATOMIC_MOVE);
        Files.setPosixFilePermissions(backupFile, PRIVATE_PERMISSIONS);
        return backupFile;
    }

    private static String writeFile(
            ZipOutputStream zip,
            String entryName,
            Path source) throws IOException {
        MessageDigest digest = sha256();
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                zip.write(buffer, 0, length);
                digest.update(buffer, 0, length);
            }
        }
        zip.closeEntry();
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 17 does not provide SHA-256", e);
        }
    }
}
