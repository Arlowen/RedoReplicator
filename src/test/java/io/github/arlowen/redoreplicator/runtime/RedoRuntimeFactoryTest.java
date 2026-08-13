/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.config.ConfigurationLoader;
import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.output.JsonlFileWriter;
import io.github.arlowen.redoreplicator.output.JsonlPosition;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.StateDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoRuntimeFactoryTest {
    @TempDir
    Path installationDirectory;

    @Test
    void appliesConfiguredTransactionMemoryAndSpillDirectory()
            throws Exception {
        Path configurationFile = installationDirectory.resolve("config.yaml");
        Files.writeString(configurationFile, """
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
                  transactionMemoryMb: 1
                """);
        Files.setPosixFilePermissions(configurationFile,
                PosixFilePermissions.fromString("rw-------"));
        ResolvedConfiguration configuration = new ConfigurationLoader().load(
                installationDirectory, configurationFile);

        try (RedoTransactionBuffer buffer = new RedoRuntimeFactory()
                .openTransactionBuffer(configuration)) {
            Xid xid = Xid.of(1, 2, 3);
            RedoLogRecord begin = record(0x0502, xid, 100);
            buffer.begin(begin, new RedoPosition(
                    Scn.of(100), 1, Seq.of(10), FileOffset.of(512)));
            RedoLogRecord undo = record(0x0501, xid, 101);
            undo.obj = 10;
            undo.dataObj = 11;
            undo.attachData(new byte[600_000], 0, 600_000);
            RedoLogRecord redo = record(0x0B02, Xid.zero(), 101);
            redo.obj = 10;
            redo.dataObj = 11;
            redo.attachData(new byte[600_000], 0, 600_000);

            assertTrue(buffer.appendPair(undo, redo));
            assertEquals(1, buffer.spilledTransactionCount());
            assertEquals(0, buffer.bufferedMemoryBytes());
            assertEquals(1, spillFileCount(
                    configuration.transactionSpillDirectory()));
        }

        assertEquals(0, spillFileCount(
                configuration.transactionSpillDirectory()));
    }

    @Test
    void opensJsonlWriterFromConfigurationAndH2Position()
            throws Exception {
        Path configurationFile = installationDirectory.resolve("config.yaml");
        Files.writeString(configurationFile, """
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
                output:
                  directory: output
                  maxFileSizeMb: 1
                state:
                  directory: data
                """);
        Files.setPosixFilePermissions(configurationFile,
                PosixFilePermissions.fromString("rw-------"));
        ResolvedConfiguration configuration = new ConfigurationLoader().load(
                installationDirectory, configurationFile);
        Files.createDirectories(configuration.stateDirectory());

        try (StateDatabase state = StateDatabase.open(
                configuration.stateDirectory())) {
            state.store().commitLwn(new RuntimeState(
                    1, 2, 3,
                    new RedoPosition(Scn.of(100), 1, Seq.of(10),
                            FileOffset.of(1_024)),
                    Optional.empty(), 3, 0,
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    OffsetDateTime.now()), List.of());
            try (JsonlFileWriter writer = new RedoRuntimeFactory()
                    .openJsonlWriter(configuration, state.store())) {
                assertEquals(new JsonlPosition(3, 0), writer.position());
                assertEquals(configuration.outputDirectory()
                                .resolve("redo-000003.jsonl"),
                        writer.currentFile());
            }
        }
    }

    private static RedoLogRecord record(
            int opCode, Xid xid, long scn) {
        RedoLogRecord record = new RedoLogRecord();
        record.opCode = opCode;
        record.xid = xid;
        record.scn = Scn.of(scn);
        record.thread = 1;
        return record;
    }

    private static long spillFileCount(Path directory) throws Exception {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString()
                            .endsWith(".spill"))
                    .count();
        }
    }
}
