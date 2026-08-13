/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.config;

import io.github.arlowen.redoreplicator.error.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationLoaderTest {
    @TempDir
    Path installationDirectory;

    @Test
    void loadsDefaultsFiltersMappingsAndRedactsPassword() throws Exception {
        Path configurationFile = writeConfiguration("""
                database:
                  url: jdbc:oracle:thin:@//oracle:1521/FREE
                  username: REDO_REPLICATOR
                  password: top-secret
                  redoPathMappings:
                    - oracle: /opt/oracle
                      local: /oracle
                    - oracle: /opt/oracle/oradata
                      local: /redo
                capture:
                  includeTables:
                    - FREEPDB1.APP.ORDERS
                    - FREEPDB1\\.APP\\.CUSTOMERS_.*
                  excludeTables:
                    - FREEPDB1.APP.CUSTOMERS_TMP
                state:
                  transactionMemoryMb: 32
                """);

        ResolvedConfiguration resolved = new ConfigurationLoader().load(
                installationDirectory, configurationFile);

        assertEquals(installationDirectory.resolve("output"),
                resolved.outputDirectory());
        assertEquals(installationDirectory.resolve("data"),
                resolved.stateDirectory());
        assertEquals(installationDirectory.resolve("data/tmp"),
                resolved.transactionSpillDirectory());
        assertEquals(32L * 1024 * 1024,
                resolved.transactionMemoryBytes());
        assertTrue(resolved.tableFilter().matches(
                "FREEPDB1.APP.ORDERS"));
        assertFalse(resolved.tableFilter().matches(
                "FREEPDB1XAPPXORDERS"));
        assertTrue(resolved.tableFilter().matches(
                "FREEPDB1.APP.CUSTOMERS_42"));
        assertFalse(resolved.tableFilter().matches(
                "FREEPDB1.APP.CUSTOMERS_TMP"));
        assertEquals(Path.of("/redo/redo01.log"),
                resolved.redoPathMapper().map(
                        "/opt/oracle/oradata/redo01.log").orElseThrow());
        assertFalse(resolved.configuration().toString()
                .contains("top-secret"));
        assertTrue(resolved.warnings().isEmpty());
    }

    @Test
    void rejectsUnknownAndDuplicateFields() throws Exception {
        Path unknown = writeConfiguration(validConfiguration()
                .replace("  transactionMemoryMb: 8",
                        "  transactionMemoryMb: 8\n  transctionMemoryMb: 4"));
        ConfigurationException unknownError = assertThrows(
                ConfigurationException.class,
                () -> new ConfigurationLoader().load(
                        installationDirectory, unknown));
        assertEquals(30001, unknownError.getErrorCode());

        Path duplicate = installationDirectory.resolve("duplicate.yaml");
        Files.writeString(duplicate, validConfiguration().replace(
                "  username: REDO_REPLICATOR",
                "  username: REDO_REPLICATOR\n  username: OTHER"));
        ConfigurationException duplicateError = assertThrows(
                ConfigurationException.class,
                () -> new ConfigurationLoader().load(
                        installationDirectory, duplicate));
        assertEquals(30001, duplicateError.getErrorCode());
    }

    @Test
    void rejectsInvalidRegexDuplicateMappingsAndDirectoryEscape()
            throws Exception {
        Path regex = writeConfiguration(validConfiguration().replace(
                "    - FREEPDB1.APP.ORDERS",
                "    - '[unclosed'"));
        assertEquals(30001, assertThrows(
                ConfigurationException.class,
                () -> new ConfigurationLoader().load(
                        installationDirectory, regex)).getErrorCode());

        Path duplicate = writeConfiguration(validConfiguration().replace(
                "capture:", "    - oracle: /opt/oracle/oradata\n"
                        + "      local: /redo2\n"
                        + "capture:"));
        assertEquals(30001, assertThrows(
                ConfigurationException.class,
                () -> new ConfigurationLoader().load(
                        installationDirectory, duplicate)).getErrorCode());

        Path escape = writeConfiguration(validConfiguration().replace(
                "  directory: data", "  directory: ../outside"));
        assertEquals(30001, assertThrows(
                ConfigurationException.class,
                () -> new ConfigurationLoader().load(
                        installationDirectory, escape)).getErrorCode());
    }

    @Test
    void rejectsScalarTypeCoercion() throws Exception {
        Path configurationFile = writeConfiguration(
                validConfiguration().replace(
                        "transactionMemoryMb: 8",
                        "transactionMemoryMb: '8'"));

        ConfigurationException error = assertThrows(
                ConfigurationException.class,
                () -> new ConfigurationLoader().load(
                        installationDirectory, configurationFile));

        assertEquals(30001, error.getErrorCode());
    }

    @Test
    void warnsWhenConfigurationPermissionsAreNotOwnerOnly()
            throws Exception {
        Path configurationFile = installationDirectory.resolve("warning.yaml");
        Files.writeString(configurationFile, validConfiguration());
        Files.setPosixFilePermissions(configurationFile,
                PosixFilePermissions.fromString("rw-r--r--"));

        ResolvedConfiguration resolved = new ConfigurationLoader().load(
                installationDirectory, configurationFile);

        assertEquals(1, resolved.warnings().size());
        assertTrue(resolved.warnings().get(0).contains("0600"));
    }

    @Test
    void distributedSampleConfigurationRemainsValid() {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();

        ResolvedConfiguration resolved = new ConfigurationLoader().load(
                projectDirectory,
                Path.of("conf/redo-replicator.yaml"));

        assertEquals(1024L * 1024 * 1024,
                resolved.transactionMemoryBytes());
        assertTrue(resolved.tableFilter().matches(
                "FREEPDB1.APP.ORDERS"));
    }

    @Test
    void rejectsDirectoryEscapeThroughSymlink() throws Exception {
        Path outside = Files.createTempDirectory("redo-config-outside-");
        try {
            Files.createSymbolicLink(
                    installationDirectory.resolve("state-link"), outside);
            Path configurationFile = writeConfiguration(
                    validConfiguration().replace(
                            "  directory: data", "  directory: state-link"));

            ConfigurationException error = assertThrows(
                    ConfigurationException.class,
                    () -> new ConfigurationLoader().load(
                            installationDirectory, configurationFile));

            assertEquals(30001, error.getErrorCode());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void fingerprintsTheNormalizedRuntimeConfiguration() throws Exception {
        ConfigurationLoader loader = new ConfigurationLoader();
        ResolvedConfiguration first = loader.load(
                installationDirectory,
                writeConfiguration(validConfiguration()));
        ResolvedConfiguration same = loader.load(
                installationDirectory,
                writeConfiguration(validConfiguration()));
        ResolvedConfiguration changed = loader.load(
                installationDirectory,
                writeConfiguration(validConfiguration().replace(
                        "transactionMemoryMb: 8",
                        "transactionMemoryMb: 16")));
        ConfigurationFingerprint fingerprint =
                new ConfigurationFingerprint();

        String firstValue = fingerprint.calculate(first);

        assertEquals(64, firstValue.length());
        assertEquals(firstValue, fingerprint.calculate(same));
        assertFalse(firstValue.equals(fingerprint.calculate(changed)));
    }

    private Path writeConfiguration(String yaml) throws IOException {
        Path configurationFile = installationDirectory.resolve("config.yaml");
        Files.writeString(configurationFile, yaml);
        Files.setPosixFilePermissions(configurationFile,
                PosixFilePermissions.fromString("rw-------"));
        return configurationFile;
    }

    private static String validConfiguration() {
        return """
                database:
                  url: jdbc:oracle:thin:@//oracle:1521/FREE
                  username: REDO_REPLICATOR
                  password: top-secret
                  redoPathMappings:
                    - oracle: /opt/oracle/oradata
                      local: /redo
                capture:
                  includeTables:
                    - FREEPDB1.APP.ORDERS
                state:
                  directory: data
                  transactionMemoryMb: 8
                """;
    }
}
