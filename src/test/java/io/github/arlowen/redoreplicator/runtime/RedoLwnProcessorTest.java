/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arlowen.redoreplicator.output.BuilderJson;
import io.github.arlowen.redoreplicator.output.JsonlFileWriter;
import io.github.arlowen.redoreplicator.output.JsonlPosition;
import io.github.arlowen.redoreplicator.output.OracleJsonValueDecoder;
import io.github.arlowen.redoreplicator.output.RedoJsonChangeAssembler;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.redo.parser.ParsedLwn;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionEntry;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.SchemaCatalogLoader;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryState;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryChange;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryOperation;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryTable;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryValue;
import io.github.arlowen.redoreplicator.schema.SystemTransactionCommit;
import io.github.arlowen.redoreplicator.schema.SystemTransactionManager;
import io.github.arlowen.redoreplicator.schema.SysUser;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import io.github.arlowen.redoreplicator.state.RuntimeState;
import io.github.arlowen.redoreplicator.state.StateDatabase;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoLwnProcessorTest {
    private static final DatabaseIdentity IDENTITY =
            new DatabaseIdentity(1, 2, 3);
    private static final String FINGERPRINT = "test-fingerprint";
    private static final Xid XID = Xid.of(1, 2, 3);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-13T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void fsyncsCompleteLwnBeforeAdvancingH2State() throws Exception {
        Path output = temporaryDirectory.resolve("output");
        TableSchemaJsonCodec jsonCodec = new TableSchemaJsonCodec();
        try (StateDatabase database = StateDatabase.open(
                temporaryDirectory.resolve("state"))) {
            seed(database, jsonCodec);
            try (JsonlFileWriter writer = JsonlFileWriter.open(
                    output, 1_048_576,
                    Optional.of(new JsonlPosition(1, 0)))) {
                RedoLwnProcessor processor = processor(
                        database, writer, jsonCodec, true);

                RuntimeState committed = processor.process(lwn());

                RuntimeState stored = database.store().loadRuntimeState()
                        .orElseThrow();
                assertEquals(committed, stored);
                assertEquals(position(200, 4096), stored.durablePosition());
                assertEquals(Optional.of(position(100, 512)),
                        stored.lowWatermarkPosition());
                assertEquals(OffsetDateTime.now(CLOCK), stored.updatedAt());
                assertEquals(Files.size(writer.currentFile()),
                        stored.jsonlFsyncOffset());
                List<String> lines = Files.readAllLines(writer.currentFile());
                assertEquals(4, lines.size());
                ObjectMapper mapper = new ObjectMapper();
                assertEquals("begin", operation(mapper.readTree(lines.get(0))));
                assertEquals("c", operation(mapper.readTree(lines.get(1))));
                assertEquals("commit", operation(mapper.readTree(lines.get(2))));
                assertEquals("chkpt", operation(mapper.readTree(lines.get(3))));
            }
        }
    }

    @Test
    void doesNotAdvanceH2WhenJsonlCannotBeWritten() throws Exception {
        TableSchemaJsonCodec jsonCodec = new TableSchemaJsonCodec();
        try (StateDatabase database = StateDatabase.open(
                temporaryDirectory.resolve("state"))) {
            RuntimeState initial = seed(database, jsonCodec);
            JsonlFileWriter writer = JsonlFileWriter.open(
                    temporaryDirectory.resolve("output"), 1_048_576,
                    Optional.of(new JsonlPosition(1, 0)));
            RedoLwnProcessor processor = processor(
                    database, writer, jsonCodec, false);
            writer.close();

            assertThrows(IllegalStateException.class,
                    () -> processor.process(lwn()));

            assertEquals(Optional.of(initial),
                    database.store().loadRuntimeState());
        }
    }

    @Test
    void capturesDmlAfterFutureTableCreateIsPublished() throws Exception {
        Path output = temporaryDirectory.resolve("future-output");
        TableSchemaJsonCodec jsonCodec = new TableSchemaJsonCodec();
        SysUser user = new SysUser(
                RowId.of(700, 800, 1), 10, "APP", IntX.zero());
        SystemTransactionManager systemManager =
                new SystemTransactionManager(
                        SystemDictionaryState.of(List.of(user)), "FREEPDB1",
                        873, 2000, StandardCharsets.UTF_8, jsonCodec);
        for (SystemDictionaryChange change : futureTableChanges()) {
            systemManager.apply(XID, change);
        }
        SystemTransactionCommit create = systemManager.commit(
                XID, Scn.of(100));
        assertEquals(1, create.schemaVersions().size());

        try (StateDatabase database = StateDatabase.open(
                temporaryDirectory.resolve("future-state"))) {
            RuntimeState createState = new RuntimeState(
                    IDENTITY.databaseId(), IDENTITY.incarnation(),
                    IDENTITY.resetlogsId(), position(100, 1024),
                    Optional.empty(), 1, 0, FINGERPRINT,
                    OffsetDateTime.now(CLOCK));
            database.store().commitLwn(
                    createState, create.schemaVersions());
            try (JsonlFileWriter writer = JsonlFileWriter.open(
                    output, 1_048_576,
                    Optional.of(new JsonlPosition(1, 0)))) {
                RedoLwnProcessor processor = processor(
                        database, writer, jsonCodec, false, systemManager);

                processor.process(new ParsedLwn(
                        position(200, 4096), RedoTime.of(1),
                        List.of(transaction(100, 101)), Optional.empty()));

                List<String> lines = Files.readAllLines(writer.currentFile());
                assertEquals(3, lines.size());
                JsonNode dml = new ObjectMapper().readTree(lines.get(1));
                assertEquals("c", operation(dml));
                assertEquals("APP",
                        dml.at("/payload/0/schema/owner").textValue());
                assertEquals("FUTURE_ORDERS",
                        dml.at("/payload/0/schema/table").textValue());
            }
        }
    }

    private RedoLwnProcessor processor(
            StateDatabase database,
            JsonlFileWriter writer,
            TableSchemaJsonCodec jsonCodec,
            boolean checkpointHeartbeat) {
        SystemTransactionManager systemManager =
                new SystemTransactionManager(
                        SystemDictionaryState.empty(), "FREEPDB1",
                        873, 2000, StandardCharsets.UTF_8, jsonCodec);
        return processor(database, writer, jsonCodec,
                checkpointHeartbeat, systemManager);
    }

    private RedoLwnProcessor processor(
            StateDatabase database,
            JsonlFileWriter writer,
            TableSchemaJsonCodec jsonCodec,
            boolean checkpointHeartbeat,
            SystemTransactionManager systemManager) {
        return new RedoLwnProcessor(
                IDENTITY, FINGERPRINT, checkpointHeartbeat,
                database.store(),
                new SchemaCatalogLoader(database.store(), jsonCodec),
                new SchemaCatalog(), systemManager,
                new RedoJsonChangeAssembler(
                        ByteOrder.LITTLE_ENDIAN, StandardCharsets.UTF_8),
                new BuilderJson(
                        new OracleJsonValueDecoder(
                                StandardCharsets.UTF_8, ZoneOffset.UTC),
                        "FREEPDB1", 0),
                writer, jsonCodec, CLOCK);
    }

    private static RuntimeState seed(
            StateDatabase database,
            TableSchemaJsonCodec jsonCodec) throws Exception {
        RuntimeState initial = new RuntimeState(
                IDENTITY.databaseId(), IDENTITY.incarnation(),
                IDENTITY.resetlogsId(), position(50, 0), Optional.empty(),
                1, 0, FINGERPRINT, OffsetDateTime.now(CLOCK));
        TableSchemaVersion schema = TableSchemaVersion.initial(
                table(), Scn.of(50), jsonCodec);
        database.store().commitLwn(initial, List.of(schema));
        return initial;
    }

    private static ParsedLwn lwn() {
        return new ParsedLwn(
                position(200, 4096), RedoTime.of(1),
                List.of(transaction()), Optional.of(position(100, 512)));
    }

    private static CommittedRedoTransaction transaction() {
        return transaction(22, 33);
    }

    private static CommittedRedoTransaction transaction(
            long objectId, long dataObjectId) {
        RedoLogRecord undo = new RedoLogRecord();
        undo.opCode = 0x0501;
        undo.xid = XID;
        undo.obj = objectId;
        undo.dataObj = dataObjectId;
        undo.suppLogFb = RedoLogRecord.FB_L;
        undo.fileOffset = FileOffset.of(1024);
        RedoLogRecord redo = new RedoLogRecord();
        redo.opCode = 0x0B02;
        redo.xid = XID;
        redo.obj = objectId;
        redo.dataObj = dataObjectId;
        redo.fb = RedoLogRecord.FB_F;
        redo.bdba = 100;
        redo.slot = 3;
        redo.fileOffset = FileOffset.of(1280);
        return new CommittedRedoTransaction(
                XID, 3, 1,
                position(100, 512), RedoTime.zero(),
                position(200, 2048), RedoTime.of(1),
                Map.of(), List.of(RedoTransactionEntry.pair(undo, redo)));
    }

    private static RedoPosition position(long scn, long offset) {
        return new RedoPosition(
                Scn.of(scn), 1, Seq.of(7), FileOffset.of(offset));
    }

    private static TableSchema table() {
        return new TableSchema(
                "FREEPDB1", "APP", "USERS", 22, 33, 10, 0, 0,
                List.of(new ColumnSchema(
                        1, -1, 1, 1, "ID", OracleColumnType.NUMBER,
                        22, 0, 0, 0, 1,
                        false, false, false, false, false,
                        false, false, false, false)),
                List.of(), List.of());
    }

    private static List<SystemDictionaryChange> futureTableChanges() {
        List<SystemDictionaryChange> changes = new ArrayList<>();
        changes.add(insert(
                SystemDictionaryTable.OBJECT, RowId.of(700, 800, 2),
                Map.of(
                        "OWNER#", number(10),
                        "OBJ#", number(100),
                        "DATAOBJ#", number(101),
                        "TYPE#", number(2),
                        "NAME", text("FUTURE_ORDERS"),
                        "FLAGS", number(0))));
        changes.add(insert(
                SystemDictionaryTable.TABLE, RowId.of(700, 800, 3),
                Map.of(
                        "OBJ#", number(100),
                        "DATAOBJ#", number(101),
                        "TS#", number(7),
                        "CLUCOLS", number(0),
                        "FLAGS", number(0),
                        "PROPERTY", number(0))));
        changes.add(insert(
                SystemDictionaryTable.COLUMN, RowId.of(700, 800, 4),
                Map.ofEntries(
                        Map.entry("OBJ#", number(100)),
                        Map.entry("COL#", number(1)),
                        Map.entry("SEGCOL#", number(1)),
                        Map.entry("INTCOL#", number(1)),
                        Map.entry("NAME", text("ID")),
                        Map.entry("TYPE#", number(2)),
                        Map.entry("SIZE", number(22)),
                        Map.entry("PRECISION#", number(0)),
                        Map.entry("SCALE", number(0)),
                        Map.entry("CHARSETFORM", number(0)),
                        Map.entry("CHARSETID", number(0)),
                        Map.entry("NULL$", number(0)),
                        Map.entry("PROPERTY", number(0)))));
        return List.copyOf(changes);
    }

    private static SystemDictionaryChange insert(
            SystemDictionaryTable table,
            RowId rowId,
            Map<String, SystemDictionaryValue> values) {
        return new SystemDictionaryChange(
                SystemDictionaryOperation.INSERT, table, rowId, values);
    }

    private static SystemDictionaryValue number(long value) {
        if (value == 0) {
            return SystemDictionaryValue.of(
                    OracleColumnType.NUMBER, new byte[]{(byte) 0x80});
        }
        List<Integer> pairs = new ArrayList<>();
        long remaining = value;
        while (remaining > 0) {
            pairs.add(0, (int) (remaining % 100));
            remaining /= 100;
        }
        byte[] encoded = new byte[pairs.size() + 1];
        encoded[0] = (byte) (0xC0 + pairs.size());
        for (int index = 0; index < pairs.size(); index++) {
            encoded[index + 1] = (byte) (pairs.get(index) + 1);
        }
        return SystemDictionaryValue.of(OracleColumnType.NUMBER, encoded);
    }

    private static SystemDictionaryValue text(String value) {
        return SystemDictionaryValue.of(
                OracleColumnType.VARCHAR,
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static String operation(JsonNode message) {
        return message.at("/payload/0/op").textValue();
    }
}
