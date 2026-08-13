/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.cli;

import io.github.arlowen.redoreplicator.config.ConfigurationLoader;
import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.RedoReplicatorException;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.runtime.RedoRuntimeFactory;
import io.github.arlowen.redoreplicator.runtime.RuntimeLock;
import io.github.arlowen.redoreplicator.source.OracleConnectionFactory;
import io.github.arlowen.redoreplicator.source.OracleSourceValidation;
import io.github.arlowen.redoreplicator.source.OracleSourceValidator;
import io.github.arlowen.redoreplicator.state.StateDatabase;
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
    private final RedoRuntimeFactory runtimeFactory;

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

    public RedoReplicatorCommand() {
        this(new ConfigurationLoader(), new OracleSourceValidator(),
                new RedoRuntimeFactory());
    }

    RedoReplicatorCommand(
            ConfigurationLoader configurationLoader,
            OracleSourceValidator sourceValidator,
            RedoRuntimeFactory runtimeFactory) {
        this.configurationLoader = configurationLoader;
        this.sourceValidator = sourceValidator;
        this.runtimeFactory = runtimeFactory;
    }

    @Override
    public Integer call() {
        try {
            ResolvedConfiguration configuration = configurationLoader.load(
                    installationDirectory, configurationFile);
            for (String warning : configuration.warnings()) {
                System.err.println("WARNING: " + warning);
            }
            OracleSourceValidation source = validateSource(configuration);
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
                 StateDatabase ignoredState = StateDatabase.open(
                    configuration.stateDirectory());
                 RedoTransactionBuffer transactionBuffer =
                         runtimeFactory.openTransactionBuffer(
                                 configuration)) {
                System.err.println("Redo capture loop is not implemented yet; "
                        + "use --validate to run startup checks");
                return EXIT_RUNTIME;
            }
        } catch (RedoReplicatorException e) {
            System.err.println("ERROR " + e.getErrorCode() + ": "
                    + e.getMessage());
            return EXIT_CONFIGURATION;
        } catch (IOException | SQLException e) {
            System.err.println("ERROR: " + e.getMessage());
            return EXIT_RUNTIME;
        }
    }

    private OracleSourceValidation validateSource(
            ResolvedConfiguration configuration) throws SQLException {
        OracleConnectionFactory connectionFactory = new OracleConnectionFactory(
                configuration.configuration().database());
        try (Connection connection = connectionFactory.open()) {
            return sourceValidator.validate(connection, configuration);
        }
    }

    private static void createRuntimeDirectories(
            ResolvedConfiguration configuration) throws IOException {
        Files.createDirectories(configuration.outputDirectory());
        Files.createDirectories(configuration.stateDirectory());
    }
}
