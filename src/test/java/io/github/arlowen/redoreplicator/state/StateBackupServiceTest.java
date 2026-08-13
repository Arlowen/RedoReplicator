/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import io.github.arlowen.redoreplicator.config.ConfigurationLoader;
import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateBackupServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-13T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    private Path installationDirectory;

    @Test
    void backsUpOnlyStateConfigurationAndStatusWithIdentity() throws Exception {
        ResolvedConfiguration configuration = configuration();
        RuntimeState runtimeState = new RuntimeState(
                10, 20, 30,
                new RedoPosition(
                        Scn.of(500), 1, Seq.of(7), FileOffset.of(4096)),
                Optional.empty(), 3, 8192, "fingerprint",
                OffsetDateTime.now(CLOCK));
        try (StateDatabase database = StateDatabase.open(
                configuration.stateDirectory())) {
            database.store().commitLwn(runtimeState, List.of());
        }
        Files.createDirectories(
                installationDirectory.resolve("data/tmp"));
        Files.writeString(
                installationDirectory.resolve("data/tmp/spill.bin"),
                "transaction");
        Files.createDirectories(installationDirectory.resolve("output"));
        Files.writeString(
                installationDirectory.resolve("output/redo-000001.jsonl"),
                "output");
        Files.writeString(
                installationDirectory.resolve("data/status.json"),
                "{\"safeScn\":\"500\"}\n");

        Path backup = new StateBackupService(CLOCK).backup(configuration);

        assertEquals("redo-replicator-1786615200000.zip",
                backup.getFileName().toString());
        try (ZipFile zip = new ZipFile(
                backup.toFile(), StandardCharsets.UTF_8)) {
            assertTrue(zip.getEntry("state/redo-replicator.mv.db") != null);
            assertTrue(zip.getEntry(
                    "configuration/redo-replicator.yaml") != null);
            assertTrue(zip.getEntry("status/status.json") != null);
            assertTrue(zip.getEntry("manifest.properties") != null);
            assertFalse(zip.stream().anyMatch(entry ->
                    entry.getName().contains("spill")
                            || entry.getName().contains("jsonl")));

            Properties manifest = properties(zip, "manifest.properties");
            assertEquals("1", manifest.getProperty("formatVersion"));
            assertEquals("10", manifest.getProperty("databaseId"));
            assertEquals("20", manifest.getProperty("incarnation"));
            assertEquals("30", manifest.getProperty("resetlogsId"));
            assertEquals("500", manifest.getProperty("safeScn"));
            assertEquals(sha256(zip, "state/redo-replicator.mv.db"),
                    manifest.getProperty("stateSha256"));
            assertEquals(sha256(zip,
                            "configuration/redo-replicator.yaml"),
                    manifest.getProperty("configurationSha256"));
            assertEquals(sha256(zip, "status/status.json"),
                    manifest.getProperty("statusSha256"));
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

    private static Properties properties(ZipFile zip, String name)
            throws Exception {
        Properties properties = new Properties();
        try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
            properties.load(input);
        }
        return properties;
    }

    private static String sha256(ZipFile zip, String name) throws Exception {
        java.security.MessageDigest digest =
                java.security.MessageDigest.getInstance("SHA-256");
        ZipEntry entry = zip.getEntry(name);
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, length);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
