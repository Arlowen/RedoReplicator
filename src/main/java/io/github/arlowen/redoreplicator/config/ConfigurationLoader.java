/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.arlowen.redoreplicator.error.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ConfigurationLoader {
    private static final Logger log = LoggerFactory.getLogger(
            ConfigurationLoader.class);
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;
    private static final Set<String> LOG_LEVELS = Set.of(
            "TRACE", "DEBUG", "INFO", "WARN", "ERROR");
    private static final Pattern EXACT_TABLE_NAME = Pattern.compile(
            "[A-Za-z0-9_$#]+(?:\\.[A-Za-z0-9_$#]+){1,2}");
    private static final Set<PosixFilePermission> SECURE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private final ObjectMapper objectMapper;

    public ConfigurationLoader() {
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        objectMapper = YAMLMapper.builder(yamlFactory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
        objectMapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.EmptyString,
                        CoercionAction.AsNull);
    }

    public ResolvedConfiguration load(
            Path installationDirectory, Path configurationFile) {
        Path installation = installationDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(installation)) {
            throw new ConfigurationException(10001,
                    "Installation directory does not exist: " + installation);
        }
        Path file = configurationFile;
        if (!file.isAbsolute()) {
            file = installation.resolve(file);
        }
        file = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ConfigurationException(10001,
                    "Configuration file is not readable: " + file);
        }

        RedoReplicatorConfiguration raw = readConfiguration(file);
        RedoReplicatorConfiguration configuration = normalize(raw);
        validate(configuration);
        Path outputDirectory = resolveInsideInstallation(
                installation, configuration.output().directory(), "output.directory");
        Path stateDirectory = resolveInsideInstallation(
                installation, configuration.state().directory(), "state.directory");
        long transactionMemoryBytes;
        try {
            transactionMemoryBytes = Math.multiplyExact(
                    configuration.state().transactionMemoryMb(),
                    BYTES_PER_MEBIBYTE);
        } catch (ArithmeticException e) {
            throw invalid("state.transactionMemoryMb is too large");
        }

        List<Pattern> includes = compilePatterns(
                configuration.capture().includeTables(), "capture.includeTables");
        List<Pattern> excludes = compilePatterns(
                configuration.capture().excludeTables(), "capture.excludeTables");
        List<String> warnings = permissionWarnings(file);
        return new ResolvedConfiguration(
                configuration,
                installation,
                file,
                outputDirectory,
                stateDirectory,
                stateDirectory.resolve("tmp"),
                transactionMemoryBytes,
                new TableFilter(includes, excludes),
                new RedoPathMapper(configuration.database().redoPathMappings()),
                warnings);
    }

    private RedoReplicatorConfiguration readConfiguration(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return objectMapper.readValue(
                    reader, RedoReplicatorConfiguration.class);
        } catch (JsonProcessingException e) {
            String msg = "Invalid YAML configuration " + file
                    + ": " + e.getOriginalMessage();
            log.error(msg, e);
            throw new ConfigurationException(30001, msg);
        } catch (IOException e) {
            String msg = "Failed to read configuration " + file;
            log.error(msg, e);
            throw new ConfigurationException(10001, msg);
        }
    }

    private static RedoReplicatorConfiguration normalize(
            RedoReplicatorConfiguration raw) {
        if (raw == null) {
            throw invalid("YAML document is empty");
        }
        CaptureConfiguration capture = raw.capture();
        if (capture != null && capture.excludeTables() == null) {
            capture = new CaptureConfiguration(
                    capture.startScn(), capture.includeTables(), List.of());
        }
        OutputConfiguration output = raw.output();
        if (output == null) {
            output = new OutputConfiguration("output", 256L, true);
        } else {
            output = new OutputConfiguration(
                    defaultText(output.directory(), "output"),
                    defaultLong(output.maxFileSizeMb(), 256),
                    defaultBoolean(output.checkpointHeartbeat(), true));
        }
        StateConfiguration state = raw.state();
        if (state == null) {
            state = new StateConfiguration("data", 1024L);
        } else {
            state = new StateConfiguration(
                    defaultText(state.directory(), "data"),
                    defaultLong(state.transactionMemoryMb(), 1024));
        }
        ArchiveConfiguration archive = raw.archive();
        if (archive == null) {
            archive = new ArchiveConfiguration(10L, 300L);
        } else {
            archive = new ArchiveConfiguration(
                    defaultLong(archive.pollIntervalSeconds(), 10),
                    defaultLong(archive.missingFileTimeoutSeconds(), 300));
        }
        LoggingConfiguration logging = raw.logging();
        if (logging == null) {
            logging = new LoggingConfiguration("INFO");
        } else {
            logging = new LoggingConfiguration(
                    defaultText(logging.level(), "INFO").toUpperCase(Locale.ROOT));
        }
        return new RedoReplicatorConfiguration(
                raw.database(), capture, output, state, archive, logging);
    }

    private static void validate(RedoReplicatorConfiguration configuration) {
        validateDatabase(configuration.database());
        validateCapture(configuration.capture());
        requireText(configuration.output().directory(), "output.directory");
        requireText(configuration.state().directory(), "state.directory");
        validatePositive(configuration.output().maxFileSizeMb(),
                "output.maxFileSizeMb");
        validatePositive(configuration.state().transactionMemoryMb(),
                "state.transactionMemoryMb");
        validatePositive(configuration.archive().pollIntervalSeconds(),
                "archive.pollIntervalSeconds");
        validatePositive(configuration.archive().missingFileTimeoutSeconds(),
                "archive.missingFileTimeoutSeconds");
        if (!LOG_LEVELS.contains(configuration.logging().level())) {
            throw invalid("logging.level must be TRACE, DEBUG, INFO, WARN or ERROR");
        }
    }

    private static void validateDatabase(DatabaseConfiguration database) {
        if (database == null) {
            throw invalid("database is required");
        }
        requireText(database.url(), "database.url");
        if (!database.url().startsWith("jdbc:oracle:")) {
            throw invalid("database.url must be an Oracle JDBC URL");
        }
        requireText(database.username(), "database.username");
        requireText(database.password(), "database.password");
        validateMappings(database.redoPathMappings());
    }

    private static void validateCapture(CaptureConfiguration capture) {
        if (capture == null) {
            throw invalid("capture is required");
        }
        if (capture.startScn() != null && capture.startScn() < 0) {
            throw invalid("capture.startScn must not be negative");
        }
        if (capture.includeTables() == null || capture.includeTables().isEmpty()) {
            throw invalid("capture.includeTables must contain at least one pattern");
        }
        for (String include : capture.includeTables()) {
            requireText(include, "capture.includeTables entry");
        }
        for (String exclude : capture.excludeTables()) {
            requireText(exclude, "capture.excludeTables entry");
        }
    }

    private static void validatePositive(long value, String field) {
        if (value <= 0) {
            throw invalid(field + " must be positive");
        }
    }

    private static void validateMappings(List<RedoPathMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            throw invalid("database.redoPathMappings must not be empty");
        }
        Set<Path> oraclePaths = new HashSet<>();
        for (RedoPathMapping mapping : mappings) {
            if (mapping == null) {
                throw invalid("database.redoPathMappings contains a null entry");
            }
            requireText(mapping.oracle(), "database.redoPathMappings.oracle");
            requireText(mapping.local(), "database.redoPathMappings.local");
            Path oracle = parsePath(
                    mapping.oracle(), "database.redoPathMappings.oracle");
            Path local = parsePath(
                    mapping.local(), "database.redoPathMappings.local");
            if (!oracle.isAbsolute() || !local.isAbsolute()) {
                throw invalid("redo path mappings must use absolute paths");
            }
            if (!oraclePaths.add(oracle)) {
                throw invalid("duplicate Oracle redo path mapping: " + oracle);
            }
        }
    }

    private static List<Pattern> compilePatterns(
            List<String> values, String field) {
        List<Pattern> patterns = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                if (EXACT_TABLE_NAME.matcher(value).matches()) {
                    patterns.add(Pattern.compile(Pattern.quote(value)));
                } else {
                    patterns.add(Pattern.compile(value));
                }
            } catch (PatternSyntaxException e) {
                throw invalid("invalid Java regular expression in "
                        + field + ": " + value);
            }
        }
        return List.copyOf(patterns);
    }

    private static Path resolveInsideInstallation(
            Path installation, String configuredPath, String field) {
        Path configured = parsePath(configuredPath, field);
        Path resolved = configured;
        if (!configured.isAbsolute()) {
            resolved = installation.resolve(configured);
        }
        resolved = resolved.toAbsolutePath().normalize();
        if (!resolved.startsWith(installation)) {
            throw invalid(field + " escapes installation directory: "
                    + configuredPath);
        }
        verifyRealPathInsideInstallation(
                installation, resolved, configuredPath, field);
        return resolved;
    }

    private static void verifyRealPathInsideInstallation(
            Path installation,
            Path resolved,
            String configuredPath,
            String field) {
        Path existing = resolved;
        while (existing != null && !Files.exists(
                existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return;
        }
        try {
            Path installationReal = installation.toRealPath();
            Path existingReal = existing.toRealPath();
            Path realCandidate = existingReal.resolve(
                    existing.relativize(resolved)).normalize();
            if (!realCandidate.startsWith(installationReal)) {
                throw invalid(field + " escapes installation directory through a symlink: "
                        + configuredPath);
            }
        } catch (IOException e) {
            String msg = "Failed to resolve configured directory " + resolved;
            log.error(msg, e);
            throw new ConfigurationException(10001, msg);
        }
    }

    private static Path parsePath(String value, String field) {
        try {
            return Path.of(value).normalize();
        } catch (InvalidPathException e) {
            throw invalid(field + " is not a valid path");
        }
    }

    private static List<String> permissionWarnings(Path file) {
        try {
            Set<PosixFilePermission> permissions =
                    Files.getPosixFilePermissions(file);
            if (!permissions.equals(SECURE_PERMISSIONS)) {
                return List.of("Configuration file permissions are "
                        + permissions + "; expected owner read/write only (0600): "
                        + file);
            }
        } catch (UnsupportedOperationException | IOException e) {
            return List.of("Could not verify configuration file permissions: "
                    + file);
        }
        return List.of();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is required");
        }
    }

    private static String defaultText(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private static long defaultLong(Long value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private static boolean defaultBoolean(Boolean value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private static ConfigurationException invalid(String message) {
        return new ConfigurationException(30001,
                "Invalid configuration: " + message);
    }
}
