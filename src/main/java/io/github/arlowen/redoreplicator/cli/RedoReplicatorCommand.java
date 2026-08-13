/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.cli;

import io.github.arlowen.redoreplicator.config.ConfigurationLoader;
import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.error.RedoReplicatorException;
import io.github.arlowen.redoreplicator.runtime.OracleCaptureRunner;
import io.github.arlowen.redoreplicator.runtime.RuntimeLock;
import io.github.arlowen.redoreplicator.runtime.ShutdownCoordinator;
import io.github.arlowen.redoreplicator.source.OracleConnectionFactory;
import io.github.arlowen.redoreplicator.source.OracleSourceValidation;
import io.github.arlowen.redoreplicator.source.OracleSourceValidator;
import io.github.arlowen.redoreplicator.state.StateDatabase;
import io.github.arlowen.redoreplicator.state.StateBackupService;
import io.github.arlowen.redoreplicator.state.StateRestoreService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;

@Command(
        name = "redo-replicator",
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        versionProvider = RedoReplicatorVersionProvider.class,
        description = "Validates and runs pure-Java Oracle redo replication")
public final class RedoReplicatorCommand implements Callable<Integer> {
    public static final int EXIT_CONFIGURATION = 2;
    public static final int EXIT_RUNTIME = 3;

    private final ConfigurationLoader configurationLoader;
    private final OracleSourceValidator sourceValidator;
    private final OracleCaptureRunner captureRunner;
    private final StateBackupService backupService;
    private final StateRestoreService restoreService;

    @Option(
            names = {"-f", "--file"},
            defaultValue = "conf/redo-replicator.yaml",
            description = "YAML configuration file")
    private Path configurationFile;

    @Option(
            names = "--install-dir",
            defaultValue = ".",
            description = "RedoReplicator installation directory")
    private Path installationDirectory;

    @Option(
            names = "--validate",
            description = "Validate configuration, Oracle and redo file access, then exit")
    private boolean validateOnly;

    @Option(
            names = "--backup",
            description = "Back up stopped H2 state, YAML and runtime status, then exit")
    private boolean backup;

    @Option(
            names = "--restore",
            paramLabel = "<backup-file>",
            description = "Restore a stopped capture after Oracle identity validation")
    private Path restoreFile;

    public RedoReplicatorCommand() {
        this(new ConfigurationLoader(), new OracleSourceValidator(),
                new OracleCaptureRunner());
    }

    RedoReplicatorCommand(
            ConfigurationLoader configurationLoader,
            OracleSourceValidator sourceValidator,
            OracleCaptureRunner captureRunner) {
        this.configurationLoader = configurationLoader;
        this.sourceValidator = sourceValidator;
        this.captureRunner = captureRunner;
        backupService = new StateBackupService();
        restoreService = new StateRestoreService();
    }

    @Override
    public Integer call() {
        try {
            ResolvedConfiguration configuration = configurationLoader.load(
                    installationDirectory, configurationFile);
            for (String warning : configuration.warnings()) {
                System.err.println("WARNING: " + warning);
            }
            int maintenanceOperations = 0;
            if (backup) {
                maintenanceOperations++;
            }
            if (restoreFile != null) {
                maintenanceOperations++;
            }
            if (validateOnly) {
                maintenanceOperations++;
            }
            if (maintenanceOperations > 1) {
                throw new ConfigurationException(
                        30001, "--validate, --backup and --restore are mutually exclusive");
            }
            if (backup) {
                Path backupFile = backupService.backup(configuration);
                System.out.println("Backup created: " + backupFile);
                return 0;
            }
            OracleConnectionFactory connectionFactory =
                    new OracleConnectionFactory(
                            configuration.configuration().database());
            try (Connection connection = connectionFactory.open()) {
                OracleSourceValidation source = sourceValidator.validate(
                        connection, configuration);
                if (restoreFile != null) {
                    Path safety = restoreService.restore(
                            configuration, restoreFile,
                            source.databaseContext().identity());
                    System.out.println("Restore successful; previous files: "
                            + safety);
                    return 0;
                }
                if (validateOnly) {
                    System.out.println("Validation successful: Oracle "
                            + source.databaseContext().version() + ", "
                            + source.redoFiles().size()
                            + " readable redo/archive file(s)");
                    return 0;
                }
                createRuntimeDirectories(configuration);
                try (RuntimeLock ignoredLock = RuntimeLock.acquire(
                        configuration.stateDirectory());
                     StateDatabase state = StateDatabase.open(
                             configuration.stateDirectory());
                     ShutdownCoordinator shutdown =
                             ShutdownCoordinator.install()) {
                    captureRunner.run(
                            connection, configuration, source, state,
                            shutdown::stopRequested);
                }
            }
            return 0;
        } catch (ConfigurationException e) {
            System.err.println("ERROR " + e.getErrorCode() + ": "
                    + e.getMessage());
            return EXIT_CONFIGURATION;
        } catch (RedoReplicatorException e) {
            System.err.println("ERROR " + e.getErrorCode() + ": "
                    + e.getMessage());
            return EXIT_RUNTIME;
        } catch (IOException | SQLException e) {
            System.err.println("ERROR: " + e.getMessage());
            return EXIT_RUNTIME;
        }
    }

    private static void createRuntimeDirectories(
            ResolvedConfiguration configuration) throws IOException {
        Files.createDirectories(configuration.outputDirectory());
        Files.createDirectories(configuration.stateDirectory());
    }
}
