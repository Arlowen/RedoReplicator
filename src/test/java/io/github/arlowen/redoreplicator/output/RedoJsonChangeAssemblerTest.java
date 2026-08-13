/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionEntry;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.SysCol;
import io.github.arlowen.redoreplicator.schema.SysObj;
import io.github.arlowen.redoreplicator.schema.SysTab;
import io.github.arlowen.redoreplicator.schema.SysUser;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryState;
import io.github.arlowen.redoreplicator.schema.SystemTransactionManager;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoJsonChangeAssemblerTest {
    private static final Xid XID = Xid.of(1, 2, 3);
    private static final long OBJECT_ID = 22;
    private static final long DATA_OBJECT_ID = 33;
    private static final long SYS_OBJECT_ID = 44;
    private static final long SYS_DATA_OBJECT_ID = 55;
    private static final long SYS_OBJ_TABLE_OBJECT_ID = 45;
    private static final long SYS_OBJ_TABLE_DATA_OBJECT_ID = 56;

    private final RedoJsonChangeAssembler assembler =
            new RedoJsonChangeAssembler(
                    ByteOrder.LITTLE_ENDIAN, StandardCharsets.UTF_8);

    @Test
    void assemblesCommittedDmlAndDdlInOriginalOrder() throws Exception {
        SchemaCatalog catalog = catalog(table("APP"));
        CommittedRedoTransaction transaction = transaction(List.of(
                insertEntry(),
                RedoTransactionEntry.single(ddlFragment(
                        1, 2, "APP", 1000)),
                RedoTransactionEntry.single(ddlFragment(
                        2, 2,
                        "ALTER TABLE APP.USERS ADD NOTE VARCHAR2(20)",
                        1100))));

        List<RedoJsonChange> changes = assembler.assemble(
                transaction, catalog);

        assertEquals(2, changes.size());
        RedoJsonDmlChange dml = (RedoJsonDmlChange) changes.get(0);
        assertEquals("USERS", dml.row().table().name());
        assertTrue(dml.row().after().get("ID").nullValue());
        RedoJsonDdlChange ddl = (RedoJsonDdlChange) changes.get(1);
        assertEquals("ALTER TABLE APP.USERS ADD NOTE VARCHAR2(20)",
                ddl.change().ddlText());
        assertEquals(Scn.of(200), ddl.change().commitScn());

        BuilderJson builder = new BuilderJson(
                new OracleJsonValueDecoder(
                        StandardCharsets.UTF_8, ZoneOffset.UTC),
                "FREEPDB1", 0);
        List<byte[]> messages = builder.buildTransaction(
                transaction, changes);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode dmlMessage = objectMapper.readTree(messages.get(1));
        JsonNode ddlMessage = objectMapper.readTree(messages.get(2));
        assertEquals("c", dmlMessage.at("/payload/0/op").textValue());
        assertEquals("ddl", ddlMessage.at("/payload/0/op").textValue());
        assertEquals("ALTER TABLE APP.USERS ADD NOTE VARCHAR2(20)",
                ddlMessage.at("/payload/0/sql").textValue());
    }

    @Test
    void stopsWhenTableSchemaIsMissingOrSystemOwned() {
        DataException missing = assertThrows(
                DataException.class,
                () -> assembler.assemble(
                        transaction(List.of(insertEntry())),
                        new SchemaCatalog()));
        assertEquals(50071, missing.getErrorCode());

        DataException system = assertThrows(
                DataException.class,
                () -> assembler.assemble(
                        transaction(List.of(insertEntry())),
                        catalog(table("SYS"))));
        assertEquals(50071, system.getErrorCode());
    }

    @Test
    void assemblesSingleRecordDdlFromTerminatedFieldEight() {
        List<RedoJsonChange> changes = assembler.assemble(
                transaction(List.of(RedoTransactionEntry.single(
                        singleRecordDdl(
                                "APP", "TRUNCATE TABLE APP.USERS")))),
                catalog(table("APP")));

        RedoJsonDdlChange ddl = (RedoJsonDdlChange) changes.get(0);
        assertEquals("TRUNCATE TABLE APP.USERS", ddl.change().ddlText());
    }

    @Test
    void resolvesDroppedDdlObjectFromPreviousScnCatalog() {
        RedoLogRecord drop = singleRecordDdl(
                "APP", "DROP TABLE APP.USERS");
        drop.ddlType = 12;

        List<RedoJsonChange> changes = assembler.assemble(
                transaction(List.of(RedoTransactionEntry.single(drop))),
                new SchemaCatalog(), catalog(table("APP")));

        RedoJsonDdlChange ddl = (RedoJsonDdlChange) changes.get(0);
        assertEquals("DROP TABLE APP.USERS", ddl.change().ddlText());
    }

    @Test
    void stopsForMultiRowAndIncompleteDdl() {
        RedoLogRecord undo = undo();
        RedoLogRecord multiple = redo(0x0B0B);
        RedoLogException multiRow = assertThrows(
                RedoLogException.class,
                () -> assembler.assemble(
                        transaction(List.of(RedoTransactionEntry.pair(
                                undo, multiple))),
                        catalog(table("APP"))));
        assertEquals(50057, multiRow.getErrorCode());

        RedoLogException incompleteDdl = assertThrows(
                RedoLogException.class,
                () -> assembler.assemble(
                        transaction(List.of(RedoTransactionEntry.single(
                                ddlFragment(1, 2, "APP", 1000)))),
                        catalog(table("APP"))));
        assertEquals(50057, incompleteDdl.getErrorCode());
    }

    @Test
    void routesCommittedSystemRowsWithoutWritingUserJson() throws Exception {
        RowId rowId = RowId.of(SYS_DATA_OBJECT_ID, 100, 3);
        SystemTransactionManager manager = systemManager(
                SystemDictionaryState.of(List.of(
                        new SysUser(rowId, 12, "APP", IntX.zero()))));

        AssembledRedoTransaction assembled = assembler.assembleCommitted(
                transaction(List.of(systemDeleteEntry())),
                catalog(systemTable()), catalog(systemTable()), manager);

        assertTrue(assembled.jsonChanges().isEmpty());
        assertTrue(assembled.schemaVersions().isEmpty());
        assertTrue(manager.dictionaryState().users().isEmpty());
        assertEquals(0, manager.openTransactionCount());
    }

    @Test
    void rejectsTransactionsMixingSystemAndUserRowsBeforeApplyingSchema() {
        SysUser user = new SysUser(
                RowId.of(SYS_DATA_OBJECT_ID, 100, 3),
                12, "APP", IntX.zero());
        SystemTransactionManager manager = systemManager(
                SystemDictionaryState.of(List.of(user)));
        SchemaCatalog catalog = catalog(table("APP"));
        catalog.add(systemTable());

        DataException error = assertThrows(DataException.class,
                () -> assembler.assembleCommitted(
                        transaction(List.of(
                                insertEntry(), systemDeleteEntry())),
                        catalog, catalog, manager));

        assertEquals(50071, error.getErrorCode());
        assertEquals(List.of(user), manager.dictionaryState().users());
        assertEquals(0, manager.openTransactionCount());
    }

    @Test
    void emitsDdlAndDropTombstoneForSystemDictionaryCommit() throws Exception {
        SystemDictionaryState state = SystemDictionaryState.of(List.of(
                new SysUser(RowId.of(70, 10, 1), 12, "APP", IntX.zero()),
                new SysObj(
                        RowId.of(SYS_OBJ_TABLE_DATA_OBJECT_ID, 100, 3),
                        12, OBJECT_ID, DATA_OBJECT_ID,
                        SysObj.TYPE_TABLE, "USERS", IntX.zero()),
                new SysTab(
                        RowId.of(71, 10, 2),
                        OBJECT_ID, DATA_OBJECT_ID, 0, 0,
                        IntX.zero(), IntX.zero()),
                new SysCol(
                        RowId.of(72, 10, 3),
                        OBJECT_ID, 1, 1, 1, "ID",
                        OracleColumnType.NUMBER.code(), 22,
                        0, 0, 0, 0, 0, IntX.zero())));
        SystemTransactionManager manager = systemManager(state);
        SchemaCatalog catalog = catalog(table("APP"));
        catalog.add(systemObjectTable());
        RedoLogRecord ddl = singleRecordDdl(
                "APP", "DROP TABLE APP.USERS");
        ddl.ddlType = 12;

        AssembledRedoTransaction assembled = assembler.assembleCommitted(
                transaction(List.of(
                        systemObjectDeleteEntry(),
                        RedoTransactionEntry.single(ddl))),
                catalog, catalog, manager);

        assertEquals(1, assembled.jsonChanges().size());
        RedoJsonDdlChange change =
                (RedoJsonDdlChange) assembled.jsonChanges().get(0);
        assertEquals("DROP TABLE APP.USERS", change.change().ddlText());
        assertEquals(1, assembled.schemaVersions().size());
        assertTrue(assembled.schemaVersions().get(0).dropTombstone());
        assertEquals(OBJECT_ID,
                assembled.schemaVersions().get(0).objectId());
    }

    private static RedoTransactionEntry insertEntry() {
        return RedoTransactionEntry.pair(undo(), redo(0x0B02));
    }

    private static RedoTransactionEntry systemDeleteEntry() {
        RedoLogRecord undo = undo();
        undo.obj = SYS_OBJECT_ID;
        undo.dataObj = SYS_DATA_OBJECT_ID;
        undo.suppLogBdba = 100;
        undo.suppLogSlot = 3;
        RedoLogRecord redo = redo(0x0B03);
        redo.obj = SYS_OBJECT_ID;
        redo.dataObj = SYS_DATA_OBJECT_ID;
        return RedoTransactionEntry.pair(undo, redo);
    }

    private static RedoTransactionEntry systemObjectDeleteEntry() {
        RedoLogRecord undo = undo();
        undo.obj = SYS_OBJ_TABLE_OBJECT_ID;
        undo.dataObj = SYS_OBJ_TABLE_DATA_OBJECT_ID;
        undo.suppLogBdba = 100;
        undo.suppLogSlot = 3;
        RedoLogRecord redo = redo(0x0B03);
        redo.obj = SYS_OBJ_TABLE_OBJECT_ID;
        redo.dataObj = SYS_OBJ_TABLE_DATA_OBJECT_ID;
        return RedoTransactionEntry.pair(undo, redo);
    }

    private static RedoLogRecord undo() {
        RedoLogRecord record = new RedoLogRecord();
        record.opCode = 0x0501;
        record.xid = XID;
        record.obj = OBJECT_ID;
        record.dataObj = DATA_OBJECT_ID;
        record.suppLogFb = RedoLogRecord.FB_L;
        record.fileOffset = FileOffset.of(512);
        return record;
    }

    private static RedoLogRecord redo(int opCode) {
        RedoLogRecord record = new RedoLogRecord();
        record.opCode = opCode;
        record.xid = XID;
        record.obj = OBJECT_ID;
        record.dataObj = DATA_OBJECT_ID;
        record.fb = RedoLogRecord.FB_F;
        record.bdba = 100;
        record.slot = 3;
        record.fileOffset = FileOffset.of(768);
        return record;
    }

    private static RedoLogRecord ddlFragment(
            int sequence, int count, String payload, long offset) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        RedoLogRecord record = new RedoLogRecord();
        record.attachData(data, 0, data.length);
        record.opCode = 0x1801;
        record.xid = XID;
        record.obj = OBJECT_ID;
        record.ddlType = 15;
        record.ddlSequence = sequence;
        record.ddlCount = count;
        record.ddlPayload1 = 0;
        record.ddlPayload1Size = data.length;
        record.fileOffset = FileOffset.of(offset);
        return record;
    }

    private static RedoLogRecord singleRecordDdl(
            String owner, String sql) {
        byte[] ownerBytes = owner.getBytes(StandardCharsets.UTF_8);
        byte[] sqlBytes = (sql + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[ownerBytes.length + sqlBytes.length];
        System.arraycopy(ownerBytes, 0, data, 0, ownerBytes.length);
        System.arraycopy(sqlBytes, 0, data, ownerBytes.length,
                sqlBytes.length);
        RedoLogRecord record = ddlFragment(1, 1, owner, 1200);
        record.attachData(data, 0, data.length);
        record.ddlPayload1 = 0;
        record.ddlPayload1Size = ownerBytes.length;
        record.ddlPayload2 = ownerBytes.length;
        record.ddlPayload2Size = sqlBytes.length;
        record.ddlType = 85;
        return record;
    }

    private static CommittedRedoTransaction transaction(
            List<RedoTransactionEntry> entries) {
        return new CommittedRedoTransaction(
                XID, 3, 1,
                position(100, 512), RedoTime.zero(),
                position(200, 2048), RedoTime.of(1),
                Map.of(), entries);
    }

    private static RedoPosition position(long scn, long offset) {
        return new RedoPosition(
                Scn.of(scn), 1, Seq.of(7), FileOffset.of(offset));
    }

    private static SchemaCatalog catalog(TableSchema table) {
        SchemaCatalog catalog = new SchemaCatalog();
        catalog.add(table);
        return catalog;
    }

    private static SystemTransactionManager systemManager(
            SystemDictionaryState state) {
        return new SystemTransactionManager(
                state, "FREEPDB1", 873, 2000,
                StandardCharsets.UTF_8, new TableSchemaJsonCodec());
    }

    private static TableSchema systemTable() {
        return new TableSchema(
                "FREEPDB1", "SYS", "USER$",
                SYS_OBJECT_ID, SYS_DATA_OBJECT_ID,
                10, 0, 0, List.of(), List.of(), List.of());
    }

    private static TableSchema systemObjectTable() {
        return new TableSchema(
                "FREEPDB1", "SYS", "OBJ$",
                SYS_OBJ_TABLE_OBJECT_ID, SYS_OBJ_TABLE_DATA_OBJECT_ID,
                10, 0, 0, List.of(), List.of(), List.of());
    }

    private static TableSchema table(String owner) {
        return new TableSchema(
                "FREEPDB1", owner, "USERS",
                OBJECT_ID, DATA_OBJECT_ID, 10, 0, 0,
                List.of(new ColumnSchema(
                        1, -1, 1, 1, "ID",
                        OracleColumnType.NUMBER,
                        22, 0, 0, 0, 1,
                        false, false, false, false,
                        false, false, false, false, false)),
                List.of(), List.of());
    }
}
