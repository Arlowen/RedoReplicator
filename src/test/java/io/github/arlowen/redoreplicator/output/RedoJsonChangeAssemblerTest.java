/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arlowen.redoreplicator.charset.CharacterSetZHS16GBK;
import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionEntry;
import io.github.arlowen.redoreplicator.redo.parser.RedoOpCodeDispatcher;
import io.github.arlowen.redoreplicator.redo.parser.RedoOpCodeTestSupport;
import io.github.arlowen.redoreplicator.redo.parser.RedoKdliDecoder;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.LobPartition;
import io.github.arlowen.redoreplicator.schema.LobSchema;
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

import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private static final long LOB_DATA_OBJECT_ID = 66;
    private static final LobId LOB_ID = LobId.of(
            new byte[]{0, 0, 0, 1, 2, 3, 4, 5, 6, 7});

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
    void resolvesDuplicateObjectIdsWithinTheTransactionPdb() {
        SchemaCatalog catalog = new SchemaCatalog();
        catalog.add(table("FREEPDB1", "USERS"));
        catalog.add(table("REPORTING", "REPORT_USERS"));
        RedoJsonChangeAssembler containerAssembler =
                new RedoJsonChangeAssembler(
                        ByteOrder.LITTLE_ENDIAN,
                        StandardCharsets.UTF_8,
                        ignored -> true,
                        containerId -> {
                            assertEquals(3, containerId);
                            return "REPORTING";
                        });

        List<RedoJsonChange> changes = containerAssembler.assemble(
                transaction(List.of(insertEntry())), catalog);

        RedoJsonDmlChange change = (RedoJsonDmlChange) changes.get(0);
        assertEquals("REPORTING", change.row().table().container());
        assertEquals("REPORT_USERS", change.row().table().name());
    }

    @Test
    void ignoresControlTransactionsFromTheCdbRoot() {
        RedoJsonChangeAssembler rootAssembler =
                new RedoJsonChangeAssembler(
                        ByteOrder.LITTLE_ENDIAN,
                        StandardCharsets.UTF_8,
                        ignored -> true,
                        containerId -> "CDB$ROOT");

        assertTrue(rootAssembler.assemble(
                transaction(List.of(insertEntry())),
                new SchemaCatalog()).isEmpty());
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
    void decodesDdlWithTheOracleCharacterSet() {
        RedoJsonChangeAssembler zhsAssembler = new RedoJsonChangeAssembler(
                ByteOrder.LITTLE_ENDIAN,
                new CharacterSetZHS16GBK(), ignored -> true);
        byte[] data = {
                0x41, 0x50, 0x50,
                (byte) 0xA2, (byte) 0xE3, 0};
        RedoLogRecord ddl = ddlFragment(1, 1, "APP", 1200);
        ddl.attachData(data, 0, data.length);
        ddl.ddlPayload1 = 0;
        ddl.ddlPayload1Size = 3;
        ddl.ddlPayload2 = 3;
        ddl.ddlPayload2Size = 3;

        List<RedoJsonChange> changes = zhsAssembler.assemble(
                transaction(List.of(RedoTransactionEntry.single(ddl))),
                catalog(table("APP")));

        RedoJsonDdlChange change = (RedoJsonDdlChange) changes.get(0);
        assertEquals("\uE76C", change.change().ddlText());
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
    void stopsForIncompleteDdl() {
        RedoLogException incompleteDdl = assertThrows(
                RedoLogException.class,
                () -> assembler.assemble(
                        transaction(List.of(RedoTransactionEntry.single(
                                ddlFragment(1, 2, "APP", 1000)))),
                        catalog(table("APP"))));
        assertEquals(50057, incompleteDdl.getErrorCode());
    }

    @Test
    void assemblesEveryQuickMultiRowInsertInOrder() throws Exception {
        CommittedRedoTransaction transaction =
                transaction(List.of(multiInsertEntry()));
        List<RedoJsonChange> changes = assembler.assemble(
                transaction,
                catalog(table("APP")));

        assertEquals(2, changes.size());
        RedoJsonDmlChange first =
                (RedoJsonDmlChange) changes.get(0);
        RedoJsonDmlChange second =
                (RedoJsonDmlChange) changes.get(1);
        assertEquals(RowId.of(DATA_OBJECT_ID, 100, 7),
                first.row().rowId());
        assertEquals(RowId.of(DATA_OBJECT_ID, 100, 9),
                second.row().rowId());
        assertEquals(2, first.row().after().get("ID").data()[1]);
        assertEquals(3, second.row().after().get("ID").data()[1]);

        BuilderJson builder = new BuilderJson(
                new OracleJsonValueDecoder(
                        StandardCharsets.UTF_8, ZoneOffset.UTC),
                "FREEPDB1", 0);
        List<byte[]> messages = builder.buildTransaction(
                transaction, changes);
        assertEquals(4, messages.size());
        ObjectMapper objectMapper = new ObjectMapper();
        assertEquals(1, objectMapper.readTree(messages.get(1))
                .at("/payload/0/after/ID").intValue());
        assertEquals(2, objectMapper.readTree(messages.get(2))
                .at("/payload/0/after/ID").intValue());
    }

    @Test
    void preservesCompressedRowPayloadThroughJson() throws Exception {
        CommittedRedoTransaction transaction = transaction(
                List.of(compressedInsertEntry()));

        List<RedoJsonChange> changes = assembler.assemble(
                transaction, catalog(table("APP")));
        BuilderJson builder = new BuilderJson(
                new OracleJsonValueDecoder(
                        StandardCharsets.UTF_8, ZoneOffset.UTC),
                "FREEPDB1", 0);
        JsonNode message = new ObjectMapper().readTree(
                builder.buildTransaction(transaction, changes).get(1));

        assertEquals("010203", message.at(
                "/payload/0/after/COMPRESSED").textValue());
        assertFalse(message.at("/payload/0/after").has("ID"));
    }

    @Test
    void emitsInlineBlobFromRedoThroughJson() throws Exception {
        CommittedRedoTransaction transaction = transaction(
                List.of(inlineLobInsertEntry()));

        List<RedoJsonChange> changes = assembler.assemble(
                transaction, catalog(lobTable()));
        BuilderJson builder = new BuilderJson(
                new OracleJsonValueDecoder(
                        StandardCharsets.UTF_8, ZoneOffset.UTC),
                "FREEPDB1", 0);
        JsonNode message = new ObjectMapper().readTree(
                builder.buildTransaction(transaction, changes).get(1));

        assertEquals("010203", message.at(
                "/payload/0/after/DATA").textValue());
    }

    @Test
    void reconstructsDirectLoaderLobPageThroughJson() throws Exception {
        CommittedRedoTransaction transaction;
        try (RedoTransactionBuffer buffer = new RedoTransactionBuffer()) {
            RedoLogRecord begin = new RedoLogRecord();
            begin.opCode = 0x0502;
            begin.xid = XID;
            begin.scn = Scn.of(100);
            begin.sequence = Seq.of(7);
            buffer.begin(begin, position(100, 512));

            RedoLogRecord page = directLoaderLobPage(
                    new byte[]{1, 2, 3}).first();
            page.xid = Xid.zero();
            buffer.accept(List.of(page), position(110, 768));
            RedoLogRecord index = new RedoLogRecord();
            index.opCode = 0x1A02;
            index.lobId = LOB_ID;
            buffer.appendPair(undo(), index);
            RedoTransactionEntry row = externalLobInsertEntry();
            buffer.appendPair(row.first(), row.second().orElseThrow());

            RedoLogRecord commit = new RedoLogRecord();
            commit.opCode = 0x0504;
            commit.xid = XID;
            commit.scn = Scn.of(200);
            commit.sequence = Seq.of(7);
            commit.fileOffset = FileOffset.of(2048);
            transaction = buffer.commit(commit).orElseThrow();
        }

        List<RedoJsonChange> changes = assembler.assemble(
                transaction, catalog(lobTable()));
        BuilderJson builder = new BuilderJson(
                new OracleJsonValueDecoder(
                        StandardCharsets.UTF_8, ZoneOffset.UTC),
                "FREEPDB1", 0);
        JsonNode message = new ObjectMapper().readTree(
                builder.buildTransaction(transaction, changes).get(1));

        assertEquals("010203", message.at(
                "/payload/0/after/DATA").textValue());
    }

    @Test
    void reconstructsClassicOutOfRowLobThroughJson() throws Exception {
        CommittedRedoTransaction transaction = transaction(List.of(
                directLoaderLobPage(
                        100, 0, new byte[]{1, 2, 3, 4}),
                directLoaderLobPage(
                        101, 1, new byte[]{5, 6, 7}),
                classicLobIndexEntry(),
                outOfRowLobInsertEntry()));

        List<RedoJsonChange> changes = assembler.assemble(
                transaction, catalog(lobTable()));
        BuilderJson builder = new BuilderJson(
                new OracleJsonValueDecoder(
                        StandardCharsets.UTF_8, ZoneOffset.UTC),
                "FREEPDB1", 0);
        JsonNode message = new ObjectMapper().readTree(
                builder.buildTransaction(transaction, changes).get(1));

        assertEquals("01020304050607", message.at(
                "/payload/0/after/DATA").textValue());
    }

    @Test
    void reconstructsTwelvePlusLobLocatorStylesThroughJson()
            throws Exception {
        CommittedRedoTransaction transaction = transaction(List.of(
                directLoaderLobPage(new byte[]{1, 2, 3}),
                lobListEntry(),
                lobInsertEntry(styleOneLobLocator()),
                lobInsertEntry(styleTwoLobLocator()),
                lobInsertEntry(legacyExtentLobLocator())));

        List<RedoJsonChange> changes = assembler.assemble(
                transaction, catalog(lobTable()));
        BuilderJson builder = new BuilderJson(
                new OracleJsonValueDecoder(
                        StandardCharsets.UTF_8, ZoneOffset.UTC),
                "FREEPDB1", 0);
        List<byte[]> messages = builder.buildTransaction(
                transaction, changes);
        ObjectMapper objectMapper = new ObjectMapper();
        Properties expected = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/lob-locator/"
                        + "openlogreplicator-6bc92bc1.properties")) {
            assertNotNull(input);
            expected.load(input);
        }

        assertEquals("true", expected.getProperty("style1.ok"));
        assertEquals(expected.getProperty("style1.hex"),
                objectMapper.readTree(messages.get(1)).at(
                        "/payload/0/after/DATA").textValue());
        assertEquals("true", expected.getProperty("style2.ok"));
        assertEquals(expected.getProperty("style2.hex"),
                objectMapper.readTree(messages.get(2)).at(
                        "/payload/0/after/DATA").textValue());
        assertEquals("true", expected.getProperty("legacy.ok"));
        assertEquals(expected.getProperty("legacy.hex"),
                objectMapper.readTree(messages.get(3)).at(
                        "/payload/0/after/DATA").textValue());
    }

    @Test
    void skipsResolvedTablesOutsideTheConfiguredOutputFilter() {
        RedoJsonChangeAssembler filtered = new RedoJsonChangeAssembler(
                ByteOrder.LITTLE_ENDIAN, StandardCharsets.UTF_8,
                qualifiedName -> qualifiedName.endsWith(".ORDERS"));

        List<RedoJsonChange> changes = filtered.assemble(
                transaction(List.of(
                        insertEntry(),
                        RedoTransactionEntry.single(singleRecordDdl(
                                "APP", "TRUNCATE TABLE APP.USERS")))),
                catalog(table("APP")));

        assertTrue(changes.isEmpty());
    }

    @Test
    void skipsUnselectedQuickMultiRowsWithoutTheirFullSchema()
            throws Exception {
        RedoJsonChangeAssembler filtered = new RedoJsonChangeAssembler(
                ByteOrder.LITTLE_ENDIAN, StandardCharsets.UTF_8,
                qualifiedName -> qualifiedName.endsWith(".ORDERS"));
        TableSchema identity = new TableSchema(
                "FREEPDB1", "APP", "USERS",
                OBJECT_ID, DATA_OBJECT_ID, 10, 0, 0,
                List.of(), List.of(), List.of());

        AssembledRedoTransaction assembled = filtered.assembleCommitted(
                transaction(List.of(multiInsertEntry())),
                catalog(identity), catalog(identity),
                systemManager(SystemDictionaryState.empty()));

        assertTrue(assembled.jsonChanges().isEmpty());
        assertTrue(assembled.schemaVersions().isEmpty());
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
    void routesEveryQuickMultiRowSystemDelete() throws Exception {
        SystemTransactionManager manager = systemManager(
                SystemDictionaryState.of(List.of(
                        new SysUser(
                                RowId.of(SYS_DATA_OBJECT_ID, 100, 7),
                                12, "APP", IntX.zero()),
                        new SysUser(
                                RowId.of(SYS_DATA_OBJECT_ID, 100, 9),
                                13, "AUDIT", IntX.zero()))));

        AssembledRedoTransaction assembled = assembler.assembleCommitted(
                transaction(List.of(multiSystemDeleteEntry())),
                catalog(systemTable()), catalog(systemTable()), manager);

        assertTrue(assembled.jsonChanges().isEmpty());
        assertTrue(assembled.schemaVersions().isEmpty());
        assertTrue(manager.dictionaryState().users().isEmpty());
    }

    @Test
    void routesEveryQuickMultiRowSystemInsert() throws Exception {
        SystemTransactionManager manager = systemManager(
                SystemDictionaryState.empty());

        AssembledRedoTransaction assembled = assembler.assembleCommitted(
                transaction(List.of(multiSystemInsertEntry())),
                catalog(systemUserTable()), catalog(systemUserTable()),
                manager);

        assertTrue(assembled.jsonChanges().isEmpty());
        assertEquals(2, manager.dictionaryState().users().size());
        assertTrue(manager.dictionaryState().users().stream()
                .anyMatch(user -> user.userId() == 12
                        && "APP".equals(user.name())));
        assertTrue(manager.dictionaryState().users().stream()
                .anyMatch(user -> user.userId() == 13
                        && "AUDIT".equals(user.name())));
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

    private static RedoTransactionEntry compressedInsertEntry() {
        byte[] ktb = RedoOpCodeTestSupport.field(8);
        ktb[0] = 0x06;
        byte[] kdo = RedoOpCodeTestSupport.field(48);
        RedoBinaryTestSupport.writeUnsignedInt(
                kdo, 0, 100, ByteOrder.LITTLE_ENDIAN);
        kdo[10] = RedoLogRecord.OP_IRP;
        kdo[16] = (byte) RedoLogRecord.FB_F;
        kdo[18] = 2;
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 40, 3, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 42, 3, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord redo = RedoOpCodeTestSupport.record(
                0x0B02, 0, ktb, kdo, new byte[]{1, 2, 3});
        new RedoOpCodeDispatcher(
                ByteOrder.LITTLE_ENDIAN,
                RedoLogRecord.REDO_VERSION_19_0).dispatch(redo);
        redo.xid = XID;
        redo.obj = OBJECT_ID;
        redo.dataObj = DATA_OBJECT_ID;
        return RedoTransactionEntry.pair(undo(), redo);
    }

    private static RedoTransactionEntry inlineLobInsertEntry() {
        return lobInsertEntry(fixedLobLocator(new byte[]{1, 2, 3}));
    }

    private static RedoTransactionEntry lobInsertEntry(byte[] locator) {
        byte[] ktb = RedoOpCodeTestSupport.field(8);
        ktb[0] = 0x06;
        byte[] kdo = RedoOpCodeTestSupport.field(48);
        RedoBinaryTestSupport.writeUnsignedInt(
                kdo, 0, 100, ByteOrder.LITTLE_ENDIAN);
        kdo[10] = RedoLogRecord.OP_IRP;
        kdo[16] = (byte) RedoLogRecord.FB_F;
        kdo[18] = 1;
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 40, 3, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 42, 3, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord redo = RedoOpCodeTestSupport.record(
                0x0B02, 0, ktb, kdo, locator);
        new RedoOpCodeDispatcher(
                ByteOrder.LITTLE_ENDIAN,
                RedoLogRecord.REDO_VERSION_19_0).dispatch(redo);
        redo.xid = XID;
        redo.obj = OBJECT_ID;
        redo.dataObj = DATA_OBJECT_ID;
        return RedoTransactionEntry.pair(undo(), redo);
    }

    private static RedoTransactionEntry externalLobInsertEntry() {
        byte[] locator = new byte[40];
        locator[5] = 0x04;
        System.arraycopy(LOB_ID.bytes(), 0, locator, 10, LobId.LENGTH);
        locator[20] = 0;
        locator[21] = 20;
        locator[22] = 0x04;
        locator[28] = 0;
        locator[29] = 3;
        RedoBinaryTestSupport.writeUnsignedInt(
                locator, 36, 100, ByteOrder.BIG_ENDIAN);
        return lobInsertEntry(locator);
    }

    private static RedoTransactionEntry outOfRowLobInsertEntry() {
        byte[] locator = new byte[20];
        System.arraycopy(LOB_ID.bytes(), 0, locator, 10, LobId.LENGTH);
        return lobInsertEntry(locator);
    }

    private static RedoTransactionEntry directLoaderLobPage(byte[] payload) {
        return directLoaderLobPage(100, 0, payload);
    }

    private static RedoTransactionEntry directLoaderLobPage(
            long dba, long pageNumber, byte[] payload) {
        RedoLogRecord record = new RedoLogRecord();
        record.attachData(payload, 0, payload.length);
        record.opCode = 0x1301;
        record.xid = XID;
        record.dataObj = LOB_DATA_OBJECT_ID;
        record.dba = dba;
        record.lobId = LOB_ID;
        record.lobPageNo = pageNumber;
        record.lobData = 0;
        record.lobDataSize = payload.length;
        return RedoTransactionEntry.single(record);
    }

    private static RedoTransactionEntry classicLobIndexEntry() {
        byte[] data = new byte[24];
        RedoBinaryTestSupport.writeUnsignedInt(
                data, 4, 1, ByteOrder.BIG_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                data, 8, 3, ByteOrder.BIG_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                data, 16, 100, ByteOrder.BIG_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                data, 20, 101, ByteOrder.BIG_ENDIAN);
        RedoLogRecord record = redo(0x0A12);
        record.attachData(data, 0, data.length);
        record.lobId = LOB_ID;
        record.lobPageNo = 0;
        record.lobSizePages = 1;
        record.lobSizeRest = 3;
        record.indKeyData = 0;
        record.indKeyDataSize = data.length;
        return RedoTransactionEntry.pair(undo(), record);
    }

    private static RedoTransactionEntry lobListEntry() {
        byte[] data = new byte[16];
        data[0] = RedoKdliDecoder.CODE_LMAP;
        RedoBinaryTestSupport.writeUnsignedInt(
                data, 4, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                data, 10, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                data, 12, 100, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = redo(0x1A02);
        record.attachData(data, 0, data.length);
        record.dba = 200;
        record.lobId = LOB_ID;
        record.indKeyDataCode = RedoKdliDecoder.CODE_LMAP;
        record.indKeyDataSize = data.length;
        return RedoTransactionEntry.pair(undo(), record);
    }

    private static byte[] styleOneLobLocator() {
        byte[] locator = variablePagedLobLocator(37, 0x20);
        locator[30] = 0;
        locator[31] = 0;
        RedoBinaryTestSupport.writeUnsignedInt(
                locator, 32, 100, ByteOrder.BIG_ENDIAN);
        locator[36] = 1;
        return locator;
    }

    private static byte[] styleTwoLobLocator() {
        byte[] locator = variablePagedLobLocator(34, 0x40);
        RedoBinaryTestSupport.writeUnsignedInt(
                locator, 30, 200, ByteOrder.BIG_ENDIAN);
        return locator;
    }

    private static byte[] legacyExtentLobLocator() {
        byte[] locator = styleOneLobLocator();
        locator[22] = 0;
        locator[23] = 0;
        locator[26] = 0;
        return locator;
    }

    private static byte[] variablePagedLobLocator(
            int size, int style) {
        byte[] locator = new byte[size];
        locator[5] = 0x04;
        System.arraycopy(LOB_ID.bytes(), 0, locator, 10, LobId.LENGTH);
        RedoBinaryTestSupport.writeUnsignedShort(
                locator, 20, size - 20, ByteOrder.BIG_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                locator, 22, 0x4000, ByteOrder.BIG_ENDIAN);
        locator[26] = (byte) style;
        locator[28] = 3;
        return locator;
    }

    private static RedoTransactionEntry multiInsertEntry() {
        byte[] first = {0, 0, 1, 2, (byte) 0xC1, 2};
        byte[] second = {0, 0, 1, 2, (byte) 0xC1, 3};
        return multiInsertEntry(
                OBJECT_ID, DATA_OBJECT_ID, first, second);
    }

    private static RedoTransactionEntry multiSystemInsertEntry() {
        byte[] first = {
                0, 0, 2,
                2, (byte) 0xC1, 13,
                3, 'A', 'P', 'P'};
        byte[] second = {
                0, 0, 2,
                2, (byte) 0xC1, 14,
                5, 'A', 'U', 'D', 'I', 'T'};
        return multiInsertEntry(
                SYS_OBJECT_ID, SYS_DATA_OBJECT_ID, first, second);
    }

    private static RedoTransactionEntry multiInsertEntry(
            long objectId,
            long dataObjectId,
            byte[] first,
            byte[] second) {
        byte[] rows = new byte[first.length + second.length];
        System.arraycopy(first, 0, rows, 0, first.length);
        System.arraycopy(second, 0, rows, first.length, second.length);
        byte[] rowSizes = new byte[4];
        RedoBinaryTestSupport.writeUnsignedShort(
                rowSizes, 0, first.length, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                rowSizes, 2, second.length, ByteOrder.LITTLE_ENDIAN);
        byte[] kdo = RedoOpCodeTestSupport.field(26);
        kdo[10] = RedoLogRecord.OP_QMI;
        kdo[18] = 2;
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 20, 7, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 22, 9, ByteOrder.LITTLE_ENDIAN);
        byte[] ktb = RedoOpCodeTestSupport.field(8);
        ktb[0] = 0x06;
        RedoLogRecord redo = RedoOpCodeTestSupport.record(
                0x0B0B, 0, ktb, kdo, rowSizes, rows);
        new RedoOpCodeDispatcher(
                ByteOrder.LITTLE_ENDIAN,
                RedoLogRecord.REDO_VERSION_19_0).dispatch(redo);
        redo.xid = XID;
        redo.obj = objectId;
        redo.dataObj = dataObjectId;
        redo.bdba = 100;
        RedoLogRecord undo = undo();
        undo.obj = objectId;
        undo.dataObj = dataObjectId;
        return RedoTransactionEntry.pair(undo, redo);
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

    private static RedoTransactionEntry multiSystemDeleteEntry() {
        byte[] rows = {0, 0, 0, 0, 0, 0};
        byte[] rowSizes = new byte[4];
        RedoBinaryTestSupport.writeUnsignedShort(
                rowSizes, 0, 3, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                rowSizes, 2, 3, ByteOrder.LITTLE_ENDIAN);
        byte[] kdo = RedoOpCodeTestSupport.field(26);
        kdo[10] = RedoLogRecord.OP_QMI;
        kdo[18] = 2;
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 20, 7, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 22, 9, ByteOrder.LITTLE_ENDIAN);
        byte[] ktb = RedoOpCodeTestSupport.field(8);
        ktb[0] = 0x06;
        RedoLogRecord undo = RedoOpCodeTestSupport.record(
                0x0B0B, 0, ktb, kdo, rowSizes, rows);
        new RedoOpCodeDispatcher(
                ByteOrder.LITTLE_ENDIAN,
                RedoLogRecord.REDO_VERSION_19_0).dispatch(undo);
        undo.opCode = 0x0501;
        undo.xid = XID;
        undo.obj = SYS_OBJECT_ID;
        undo.dataObj = SYS_DATA_OBJECT_ID;

        RedoLogRecord redo = redo(0x0B0C);
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

    private static TableSchema systemUserTable() {
        return new TableSchema(
                "FREEPDB1", "SYS", "USER$",
                SYS_OBJECT_ID, SYS_DATA_OBJECT_ID,
                10, 0, 0,
                List.of(
                        column(1, "USER#", OracleColumnType.NUMBER),
                        column(2, "NAME", OracleColumnType.VARCHAR),
                        column(3, "SPARE1", OracleColumnType.NUMBER)),
                List.of(), List.of());
    }

    private static TableSchema systemObjectTable() {
        return new TableSchema(
                "FREEPDB1", "SYS", "OBJ$",
                SYS_OBJ_TABLE_OBJECT_ID, SYS_OBJ_TABLE_DATA_OBJECT_ID,
                10, 0, 0, List.of(), List.of(), List.of());
    }

    private static TableSchema table(String owner) {
        return table("FREEPDB1", "USERS", owner);
    }

    private static TableSchema table(String container, String name) {
        return table(container, name, "APP");
    }

    private static TableSchema table(
            String container, String name, String owner) {
        return new TableSchema(
                container, owner, name,
                OBJECT_ID, DATA_OBJECT_ID, 10, 0, 0,
                List.of(new ColumnSchema(
                        1, -1, 1, 1, "ID",
                        OracleColumnType.NUMBER,
                        22, 0, 0, 0, 1,
                        false, false, false, false,
                        false, false, false, false, false)),
                List.of(), List.of());
    }

    private static TableSchema lobTable() {
        LobSchema lob = new LobSchema(
                OBJECT_ID, LOB_DATA_OBJECT_ID, 77, 1, 1,
                List.of(), List.of(new LobPartition(
                        LOB_DATA_OBJECT_ID, 4)));
        return new TableSchema(
                "FREEPDB1", "APP", "LOB_DATA",
                OBJECT_ID, DATA_OBJECT_ID, 10, 0, 0,
                List.of(column(1, "DATA", OracleColumnType.BLOB)),
                List.of(lob), List.of());
    }

    private static byte[] fixedLobLocator(byte[] value) {
        byte[] locator = new byte[36 + value.length];
        locator[5] = 0x04;
        locator[20] = (byte) ((value.length + 16) >>> 8);
        locator[21] = (byte) (value.length + 16);
        locator[22] = 0x01;
        locator[28] = (byte) (value.length >>> 8);
        locator[29] = (byte) value.length;
        System.arraycopy(value, 0, locator, 36, value.length);
        return locator;
    }

    private static ColumnSchema column(
            int segmentColumn,
            String name,
            OracleColumnType type) {
        return new ColumnSchema(
                segmentColumn, -1, segmentColumn, segmentColumn,
                name, type, 128, -1, -1, 0, 0,
                true, false, false, false, false,
                false, false, false, false);
    }
}
