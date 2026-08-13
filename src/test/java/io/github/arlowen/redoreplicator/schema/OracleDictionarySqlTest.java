/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleDictionarySqlTest {
    @Test
    void readsEveryMutableDictionaryAtOneFlashbackScn() {
        assertTrue(OracleDictionarySql.TABLE.contains("SYS.USER$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.TABLE.contains("SYS.OBJ$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.TABLE.contains("SYS.TAB$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.TABLE.contains(
                "SYS.DEFERRED_STG$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.COLUMNS.contains("SYS.COL$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.PRIMARY_KEY_MEMBERSHIP.contains(
                "SYS.CDEF$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.PRIMARY_KEY_MEMBERSHIP.contains(
                "SYS.CCOL$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.GUARD_SEGMENTS.contains("SYS.ECOL$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.LOBS.contains("SYS.LOB$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.LOB_PARTITIONS.contains(
                "SYS.LOBCOMPPART$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.LOB_PARTITIONS.contains(
                "SYS.LOBFRAG$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_USER_ROW.contains(
                "SYS.USER$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_USER_ROW.contains("ROWIDTOCHAR(U.ROWID)"));
        assertTrue(OracleDictionarySql.SYSTEM_OBJECT_ROWS.contains(
                "SYS.OBJ$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_TABLE_ROWS.contains(
                "SYS.TAB$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_COLUMN_ROWS.contains(
                "SYS.COL$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_DEFERRED_STORAGE_ROWS.contains(
                "SYS.DEFERRED_STG$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_EXTENDED_COLUMN_ROWS.contains(
                "SYS.ECOL$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_LOB_ROWS.contains(
                "SYS.LOB$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_LOB_COMPOSITE_PARTITION_ROWS.contains(
                "SYS.LOBCOMPPART$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_LOB_FRAGMENT_ROWS.contains(
                "SYS.LOBFRAG$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_CONSTRAINT_ROWS.contains(
                "SYS.CDEF$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_CONSTRAINT_COLUMN_ROWS.contains(
                "SYS.CCOL$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_TABLE_COMPOSITE_PARTITION_ROWS.contains(
                "SYS.TABCOMPART$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_TABLE_PARTITION_ROWS.contains(
                "SYS.TABPART$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_TABLE_SUBPARTITION_ROWS.contains(
                "SYS.TABSUBPART$ AS OF SCN ?"));
        assertTrue(OracleDictionarySql.SYSTEM_TABLESPACE_ROWS.contains(
                "SYS.TS$ AS OF SCN ?"));
    }
}
