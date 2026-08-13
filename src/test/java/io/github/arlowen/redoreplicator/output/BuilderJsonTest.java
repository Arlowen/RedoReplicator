/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.DecodedRedoRow;
import io.github.arlowen.redoreplicator.redo.transaction.RedoColumnValue;
import io.github.arlowen.redoreplicator.redo.transaction.RedoRowOperation;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.DdlSchemaChange;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderJsonTest {
    @TempDir
    Path outputDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BuilderJson builder = new BuilderJson(
            new OracleJsonValueDecoder(
                    StandardCharsets.UTF_8, ZoneOffset.UTC),
            "FREEPDB1", 0);

    @Test
    void buildsNativeBeginDmlAndCommitMessagesInOrder() throws Exception {
        CommittedRedoTransaction transaction = transaction(
                100, redoTime(2024, 4, 5, 19, 34, 38), 200);
        TableSchema table = table();
        List<RedoJsonChange> changes = List.of(
                new RedoJsonDmlChange(row(
                        RedoRowOperation.INSERT, table, Map.of(),
                        values(number(100), text("A\nB")))),
                new RedoJsonDmlChange(row(
                        RedoRowOperation.UPDATE, table,
                        values(number(100), text("before")),
                        values(number(100), text("after")))),
                new RedoJsonDmlChange(row(
                        RedoRowOperation.DELETE, table,
                        values(number(100), text("after")), Map.of())));

        List<byte[]> messages = builder.buildTransaction(
                transaction, changes);

        assertEquals(5, messages.size());
        JsonNode begin = json(messages.get(0));
        assertEquals(100, begin.get("scn").longValue());
        assertEquals("1712345678000000000", begin.get("tm").asText());
        assertHeader(begin, 100, 1, true);
        assertEquals("begin", begin.at("/payload/0/op").textValue());

        JsonNode insert = json(messages.get(1));
        assertHeader(insert, 100, 2, true);
        assertFalse(insert.has("scn"));
        assertFalse(insert.has("tm"));
        assertEquals("c", insert.at("/payload/0/op").textValue());
        assertEquals("APP", insert.at("/payload/0/schema/owner").textValue());
        assertEquals("ORDERS", insert.at("/payload/0/schema/table").textValue());
        assertEquals("100", insert.at("/payload/0/after/ID").asText());
        assertEquals("A\nB", insert.at("/payload/0/after/NAME").textValue());
        assertFalse(insert.at("/payload/0").has("before"));

        JsonNode update = json(messages.get(2));
        assertEquals("u", update.at("/payload/0/op").textValue());
        assertEquals("before", update.at(
                "/payload/0/before/NAME").textValue());
        assertEquals("after", update.at(
                "/payload/0/after/NAME").textValue());

        JsonNode delete = json(messages.get(3));
        assertEquals("d", delete.at("/payload/0/op").textValue());
        assertTrue(delete.at("/payload/0").has("before"));
        assertFalse(delete.at("/payload/0").has("after"));

        JsonNode commit = json(messages.get(4));
        assertHeader(commit, 100, 5, true);
        assertEquals("commit", commit.at("/payload/0/op").textValue());
        assertFalse(commit.has("scn"));
        assertFalse(commit.has("tm"));

        assertFalse(new String(messages.get(1), StandardCharsets.UTF_8)
                .contains("1E+2"));
    }

    @Test
    void preservesDmlAndDdlChangeOrder() throws Exception {
        CommittedRedoTransaction transaction = transaction(300, 0, 400);
        DdlSchemaChange ddl = new DdlSchemaChange(
                "FREEPDB1", "APP", "ORDERS", 15,
                "ALTER TABLE APP.ORDERS ADD NOTE VARCHAR2(20)",
                Scn.of(400));

        List<byte[]> messages = builder.buildTransaction(
                transaction, List.of(
                        new RedoJsonDmlChange(row(
                                RedoRowOperation.INSERT, table(), Map.of(),
                                values(number(1), text("first")))),
                        new RedoJsonDdlChange(ddl)));

        assertEquals("c", json(messages.get(1))
                .at("/payload/0/op").textValue());
        JsonNode ddlMessage = json(messages.get(2));
        assertEquals("ddl", ddlMessage.at("/payload/0/op").textValue());
        assertEquals("APP", ddlMessage.at(
                "/payload/0/schema/owner").textValue());
        assertEquals("ORDERS", ddlMessage.at(
                "/payload/0/schema/table").textValue());
        assertEquals(ddl.ddlText(), ddlMessage.at(
                "/payload/0/sql").textValue());
    }

    @Test
    void skipsEmptyTransactionsAndBuildsCheckpoint() throws Exception {
        assertTrue(builder.buildTransaction(
                transaction(500, 0, 600), List.of()).isEmpty());

        JsonNode checkpoint = json(builder.buildCheckpoint(
                Scn.of(700), RedoTime.zero(), Seq.of(8),
                FileOffset.of(4096), true));

        assertEquals(700, checkpoint.get("scn").longValue());
        assertEquals("567993600000000000",
                checkpoint.get("tm").asText());
        assertEquals(700, checkpoint.get("c_scn").longValue());
        assertEquals(1, checkpoint.get("c_idx").longValue());
        assertEquals("FREEPDB1", checkpoint.get("db").textValue());
        assertFalse(checkpoint.has("xid"));
        assertEquals("chkpt", checkpoint.at("/payload/0/op").textValue());
        assertEquals(8, checkpoint.at("/payload/0/seq").longValue());
        assertEquals(4096, checkpoint.at("/payload/0/offset").longValue());
        assertTrue(checkpoint.at("/payload/0/redo").booleanValue());
    }

    @Test
    void writesOneCompleteJsonlLinePerNativeMessage() throws Exception {
        List<byte[]> messages = builder.buildTransaction(
                transaction(800, 0, 900),
                List.of(new RedoJsonDmlChange(row(
                        RedoRowOperation.INSERT, table(), Map.of(),
                        values(number(1), text("line 1\nline 2"))))));

        try (JsonlFileWriter writer = JsonlFileWriter.open(
                outputDirectory, 1024, Optional.empty())) {
            JsonlPosition position = writer.writeAndSync(messages);
            List<String> lines = Files.readAllLines(writer.currentFile());

            assertEquals(3, lines.size());
            assertEquals(position.fsyncOffset(), Files.size(
                    writer.currentFile()));
            assertEquals("begin", objectMapper.readTree(lines.get(0))
                    .at("/payload/0/op").textValue());
            assertEquals("line 1\nline 2", objectMapper.readTree(lines.get(1))
                    .at("/payload/0/after/NAME").textValue());
            assertEquals("commit", objectMapper.readTree(lines.get(2))
                    .at("/payload/0/op").textValue());
        }
    }

    private void assertHeader(
            JsonNode message, long scn, long index, boolean xid) {
        assertEquals(scn, message.get("c_scn").longValue());
        assertEquals(index, message.get("c_idx").longValue());
        assertEquals("FREEPDB1", message.get("db").textValue());
        assertEquals(xid, message.has("xid"));
        if (xid) {
            assertEquals("0x0001.002.00000003",
                    message.get("xid").textValue());
        }
    }

    private JsonNode json(byte[] message) throws Exception {
        return objectMapper.readTree(message);
    }

    private static CommittedRedoTransaction transaction(
            long beginScn, long beginTimestamp, long commitScn) {
        return new CommittedRedoTransaction(
                Xid.of(1, 2, 3), 3, 1,
                position(beginScn, 4096), RedoTime.of(beginTimestamp),
                position(commitScn, 8192), RedoTime.of(beginTimestamp + 1),
                Map.of(Attribute.LOGIN_USER_NAME, "APP"), List.of());
    }

    private static RedoPosition position(long scn, long offset) {
        return new RedoPosition(
                Scn.of(scn), 1, Seq.of(7), FileOffset.of(offset));
    }

    private static DecodedRedoRow row(
            RedoRowOperation operation,
            TableSchema table,
            Map<String, RedoColumnValue> before,
            Map<String, RedoColumnValue> after) {
        return new DecodedRedoRow(
                operation, table, RowId.of(100, 200, 3),
                FileOffset.of(4096), before, after);
    }

    private static Map<String, RedoColumnValue> values(
            RedoColumnValue id, RedoColumnValue name) {
        Map<String, RedoColumnValue> values = new LinkedHashMap<>();
        values.put("ID", id);
        values.put("NAME", name);
        return values;
    }

    private static RedoColumnValue number(int value) {
        if (value == 100) {
            return RedoColumnValue.of(
                    OracleColumnType.NUMBER,
                    new byte[]{(byte) 0xC2, 2});
        }
        return RedoColumnValue.of(
                OracleColumnType.NUMBER,
                new byte[]{(byte) 0xC1, (byte) (value + 1)});
    }

    private static RedoColumnValue text(String value) {
        return RedoColumnValue.of(
                OracleColumnType.VARCHAR,
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static TableSchema table() {
        return new TableSchema(
                "FREEPDB1", "APP", "ORDERS", 100, 101, 10,
                0, 0,
                List.of(
                        column(1, "ID", OracleColumnType.NUMBER, 1),
                        column(2, "NAME", OracleColumnType.VARCHAR, 0)),
                List.of(), List.of());
    }

    private static ColumnSchema column(
            int position,
            String name,
            OracleColumnType type,
            int primaryKey) {
        return new ColumnSchema(
                position, -1, position, position, name, type,
                128, 0, 0, 873, primaryKey,
                true, false, false, false, false,
                false, false, false, false);
    }

    private static long redoTime(
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second) {
        long value = year - 1988L;
        value = value * 12 + month - 1;
        value = value * 31 + day - 1;
        value = value * 24 + hour;
        value = value * 60 + minute;
        return value * 60 + second;
    }
}
