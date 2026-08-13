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
    }
}
