/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.state.SchemaSource;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemTransactionManagerTest {
    private static final String CONTAINER = "FREEPDB1";
    private static final long OBJECT_ID = 100;
    private static final long DATA_OBJECT_ID = 101;
    private static final long USER_ID = 12;
    private static final Xid XID_1 = Xid.of(1, 2, 3);
    private static final Xid XID_2 = Xid.of(1, 2, 4);

    private final TableSchemaJsonCodec jsonCodec = new TableSchemaJsonCodec();

    @Test
    void insertsDictionaryRowsAndBuildsSchemaOnlyAtCommit() throws Exception {
        SystemTransactionManager manager = manager(SystemDictionaryState.empty());

        for (SystemDictionaryChange change : createTableChanges()) {
            manager.apply(XID_1, change);
        }

        assertTrue(manager.dictionaryState().rows().isEmpty());
        assertEquals(1, manager.openTransactionCount());

        SystemTransactionCommit commit = manager.commit(XID_1, Scn.of(500));

        assertEquals(0, manager.openTransactionCount());
        assertEquals(7, commit.dictionaryState().rows().size());
        assertEquals(1, commit.schemaVersions().size());
        TableSchemaVersion version = commit.schemaVersions().get(0);
        TableSchema schema = version.decode(jsonCodec);
        assertEquals(SchemaSource.REDO, version.source());
        assertEquals("SYSTEM_TRANSACTION", version.ddlType());
        assertEquals(Scn.of(500), version.effectiveScn());
        assertEquals("APP", schema.owner());
        assertEquals("ORDERS", schema.name());
        assertEquals(List.of(0), schema.primaryKeyColumnIndexes());
        assertEquals(List.of("ID", "DESCRIPTION"),
                schema.columns().stream().map(ColumnSchema::name).toList());
        assertEquals(-1, schema.columns().get(1).precision());
        assertEquals(-1, schema.columns().get(1).scale());
    }

    @Test
    void updatesExistingColumnAndPublishesNewCompleteVersion() throws Exception {
        SystemTransactionManager manager = manager(initialTableState());
        RowId descriptionRowId = rowId(4);
        manager.apply(XID_1, update(
                SystemDictionaryTable.COLUMN,
                descriptionRowId,
                Map.of(
                        "NAME", text("DETAILS"),
                        "SIZE", number(200))));

        assertEquals("DESCRIPTION", manager.dictionaryState().columns().stream()
                .filter(column -> column.rowId().equals(descriptionRowId))
                .findFirst().orElseThrow().name());

        TableSchemaVersion version = manager.commit(
                XID_1, Scn.of(600)).schemaVersions().get(0);
        TableSchema schema = version.decode(jsonCodec);

        assertEquals("DETAILS", schema.columns().get(1).name());
        assertEquals(200, schema.columns().get(1).length());
        assertEquals("DESCRIPTION", initialTableState().columns().stream()
                .filter(column -> column.rowId().equals(descriptionRowId))
                .findFirst().orElseThrow().name());
    }

    @Test
    void deletingTableObjectProducesDropTombstone() throws Exception {
        SystemTransactionManager manager = manager(initialTableState());
        manager.apply(XID_1, SystemDictionaryChange.delete(
                SystemDictionaryTable.OBJECT, rowId(1)));

        SystemTransactionCommit commit = manager.commit(XID_1, Scn.of(700));

        assertEquals(1, commit.schemaVersions().size());
        TableSchemaVersion version = commit.schemaVersions().get(0);
        assertTrue(version.dropTombstone());
        assertEquals("SYSTEM_TRANSACTION_DROP", version.ddlType());
        assertEquals(OBJECT_ID, version.objectId());
        assertFalse(commit.dictionaryState().objects().stream()
                .anyMatch(object -> object.objectId() == OBJECT_ID));
    }

    @Test
    void rollbackDiscardsAllOverlayChanges() {
        SystemDictionaryState initial = initialTableState();
        SystemTransactionManager manager = manager(initial);
        manager.apply(XID_1, update(
                SystemDictionaryTable.COLUMN,
                rowId(4),
                Map.of("NAME", text("DISCARDED"))));

        manager.rollback(XID_1);

        assertEquals(0, manager.openTransactionCount());
        assertEquals(initial.rows(), manager.dictionaryState().rows());
    }

    @Test
    void mergesInterleavedTransactionsThatTouchDifferentRows() throws Exception {
        SysUser first = new SysUser(rowId(10), 12, "APP", IntX.zero());
        SysUser second = new SysUser(rowId(11), 13, "REPORT", IntX.zero());
        SystemTransactionManager manager = manager(
                SystemDictionaryState.of(List.of(first, second)));
        manager.apply(XID_1, update(
                SystemDictionaryTable.USER, first.rowId(),
                Map.of("NAME", text("APP_NEW"))));
        manager.apply(XID_2, update(
                SystemDictionaryTable.USER, second.rowId(),
                Map.of("NAME", text("REPORT_NEW"))));

        manager.commit(XID_1, Scn.of(800));
        manager.commit(XID_2, Scn.of(801));

        assertEquals(List.of("APP_NEW", "REPORT_NEW"),
                manager.dictionaryState().users().stream()
                        .map(SysUser::name).sorted().toList());
    }

    @Test
    void stopsOnConflictingInterleavedRowChanges() throws Exception {
        SysUser user = new SysUser(rowId(10), 12, "APP", IntX.zero());
        SystemTransactionManager manager = manager(
                SystemDictionaryState.of(List.of(user)));
        manager.apply(XID_1, update(
                SystemDictionaryTable.USER, user.rowId(),
                Map.of("NAME", text("FIRST"))));
        manager.apply(XID_2, update(
                SystemDictionaryTable.USER, user.rowId(),
                Map.of("NAME", text("SECOND"))));
        manager.commit(XID_1, Scn.of(900));

        DataException exception = assertThrows(DataException.class,
                () -> manager.commit(XID_2, Scn.of(901)));

        assertEquals(50071, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Conflicting committed"));
        assertEquals("FIRST", manager.dictionaryState().users().get(0).name());
        assertEquals(1, manager.openTransactionCount());
    }

    @Test
    void rejectsSchemaWhenRequiredDictionaryFamiliesAreMissing() {
        List<SystemDictionaryRow> rows = new ArrayList<>(initialTableState().rows());
        rows.removeIf(row -> row.dictionaryTable() == SystemDictionaryTable.COLUMN);
        SystemTransactionManager manager = manager(SystemDictionaryState.of(rows));
        manager.apply(XID_1, update(
                SystemDictionaryTable.TABLE, rowId(2),
                Map.of("DATAOBJ#", number(102))));

        DataException exception = assertThrows(DataException.class,
                () -> manager.commit(XID_1, Scn.of(1000)));

        assertEquals(50071, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("SYS.COL$"));
        assertEquals(DATA_OBJECT_ID,
                manager.dictionaryState().tables().get(0).dataObjectId());
    }

    @Test
    void rejectsIncompleteInsertedRows() {
        SystemTransactionManager manager = manager(SystemDictionaryState.empty());

        DataException exception = assertThrows(DataException.class,
                () -> manager.apply(XID_1, insert(
                        SystemDictionaryTable.OBJECT,
                        rowId(1),
                        Map.of("OBJ#", number(OBJECT_ID)))));

        assertEquals(50020, exception.getErrorCode());
        assertEquals(0, manager.openTransactionCount());
    }

    @Test
    void rejectsDictionaryColumnTypeMismatch() {
        SystemTransactionManager manager = manager(initialTableState());

        DataException exception = assertThrows(DataException.class,
                () -> manager.apply(XID_1, update(
                        SystemDictionaryTable.COLUMN,
                        rowId(4),
                        Map.of("SIZE", text("100")))));

        assertEquals(50019, exception.getErrorCode());
        assertEquals(0, manager.openTransactionCount());
        assertEquals(100, manager.dictionaryState().columns().stream()
                .filter(column -> column.rowId().equals(rowId(4)))
                .findFirst().orElseThrow().length());
    }

    private SystemTransactionManager manager(SystemDictionaryState state) {
        return new SystemTransactionManager(
                state,
                CONTAINER,
                873,
                2000,
                StandardCharsets.UTF_8,
                jsonCodec);
    }

    private static SystemDictionaryState initialTableState() {
        List<SystemDictionaryRow> rows = new ArrayList<>();
        for (SystemDictionaryChange change : createTableChanges()) {
            SystemTransaction transaction = new SystemTransaction(
                    XID_1,
                    SystemDictionaryState.of(rows),
                    StandardCharsets.UTF_8);
            transaction.apply(change);
            rows = new ArrayList<>(transaction.commitAgainst(
                    SystemDictionaryState.of(rows)).rows());
        }
        return SystemDictionaryState.of(rows);
    }

    private static List<SystemDictionaryChange> createTableChanges() {
        return List.of(
                insert(SystemDictionaryTable.USER, rowId(0), Map.of(
                        "USER#", number(USER_ID),
                        "NAME", text("APP"),
                        "SPARE1", number(0))),
                insert(SystemDictionaryTable.OBJECT, rowId(1), Map.of(
                        "OWNER#", number(USER_ID),
                        "OBJ#", number(OBJECT_ID),
                        "DATAOBJ#", number(DATA_OBJECT_ID),
                        "TYPE#", number(SysObj.TYPE_TABLE),
                        "NAME", text("ORDERS"),
                        "FLAGS", number(0))),
                insert(SystemDictionaryTable.TABLE, rowId(2), Map.of(
                        "OBJ#", number(OBJECT_ID),
                        "DATAOBJ#", number(DATA_OBJECT_ID),
                        "TS#", number(4),
                        "CLUCOLS", number(0),
                        "FLAGS", number(0),
                        "PROPERTY", number(0))),
                insert(SystemDictionaryTable.COLUMN, rowId(3), Map.ofEntries(
                        Map.entry("OBJ#", number(OBJECT_ID)),
                        Map.entry("COL#", number(1)),
                        Map.entry("SEGCOL#", number(1)),
                        Map.entry("INTCOL#", number(1)),
                        Map.entry("NAME", text("ID")),
                        Map.entry("TYPE#", number(OracleColumnType.NUMBER.code())),
                        Map.entry("SIZE", number(22)),
                        Map.entry("PRECISION#", number(10)),
                        Map.entry("SCALE", number(0)),
                        Map.entry("CHARSETFORM", number(0)),
                        Map.entry("CHARSETID", number(0)),
                        Map.entry("NULL$", number(1)),
                        Map.entry("PROPERTY", number(0)))),
                insert(SystemDictionaryTable.COLUMN, rowId(4), Map.ofEntries(
                        Map.entry("OBJ#", number(OBJECT_ID)),
                        Map.entry("COL#", number(2)),
                        Map.entry("SEGCOL#", number(2)),
                        Map.entry("INTCOL#", number(2)),
                        Map.entry("NAME", text("DESCRIPTION")),
                        Map.entry("TYPE#", number(OracleColumnType.VARCHAR.code())),
                        Map.entry("SIZE", number(100)),
                        Map.entry("PRECISION#", nullNumber()),
                        Map.entry("SCALE", nullNumber()),
                        Map.entry("CHARSETFORM", number(1)),
                        Map.entry("CHARSETID", number(873)),
                        Map.entry("NULL$", number(0)),
                        Map.entry("PROPERTY", number(0)))),
                insert(SystemDictionaryTable.CONSTRAINT, rowId(5), Map.of(
                        "CON#", number(500),
                        "OBJ#", number(OBJECT_ID),
                        "TYPE#", number(SysCDef.TYPE_PRIMARY_KEY))),
                insert(SystemDictionaryTable.CONSTRAINT_COLUMN, rowId(6), Map.of(
                        "CON#", number(500),
                        "INTCOL#", number(1),
                        "OBJ#", number(OBJECT_ID),
                        "SPARE1", number(0))));
    }

    private static SystemDictionaryChange insert(
            SystemDictionaryTable table, RowId rowId,
            Map<String, SystemDictionaryValue> values) {
        return new SystemDictionaryChange(
                SystemDictionaryOperation.INSERT, table, rowId, values);
    }

    private static SystemDictionaryChange update(
            SystemDictionaryTable table, RowId rowId,
            Map<String, SystemDictionaryValue> values) {
        return new SystemDictionaryChange(
                SystemDictionaryOperation.UPDATE, table, rowId, values);
    }

    private static RowId rowId(int slot) {
        return RowId.of(700, 800, slot);
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

    private static SystemDictionaryValue nullNumber() {
        return SystemDictionaryValue.nullValue(OracleColumnType.NUMBER);
    }

    private static SystemDictionaryValue text(String value) {
        return SystemDictionaryValue.of(
                OracleColumnType.VARCHAR,
                value.getBytes(StandardCharsets.UTF_8));
    }
}
