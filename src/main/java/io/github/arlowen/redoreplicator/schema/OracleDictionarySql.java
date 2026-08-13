/*
 * Java translation derived from OpenLogReplicator
 * src/replicator/ReplicatorOnline.h dictionary queries.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

final class OracleDictionarySql {
    static final String CONTAINER_NAME = """
            SELECT NVL(SYS_CONTEXT('USERENV', 'CON_NAME'),
                       SYS_CONTEXT('USERENV', 'DB_NAME'))
              FROM DUAL
            """;

    static final String CHARACTER_SET = """
            SELECT NLS_CHARSET_ID(PROPERTY_VALUE)
              FROM DATABASE_PROPERTIES
             WHERE PROPERTY_NAME = ?
            """;

    static final String TABLE = """
            SELECT U.USER#, O.OBJ#, NVL(T.DATAOBJ#, 0), NVL(T.CLUCOLS, 0),
                   MOD(NVL(O.FLAGS, 0), 18446744073709551616),
                   MOD(NVL(T.FLAGS, 0), 18446744073709551616),
                   MOD(NVL(T.PROPERTY, 0), 18446744073709551616),
                   MOD(NVL(DS.FLAGS_STG, 0), 18446744073709551616)
              FROM SYS.USER$ AS OF SCN ? U
              JOIN SYS.OBJ$ AS OF SCN ? O ON O.OWNER# = U.USER#
              JOIN SYS.TAB$ AS OF SCN ? T ON T.OBJ# = O.OBJ#
              LEFT JOIN SYS.DEFERRED_STG$ AS OF SCN ? DS ON DS.OBJ# = O.OBJ#
             WHERE U.NAME = ?
               AND O.NAME = ?
               AND O.TYPE# = 2
               AND BITAND(MOD(O.FLAGS, 18446744073709551616), 128) = 0
            """;

    static final String COLUMNS = """
            SELECT C.COL#, C.SEGCOL#, C.INTCOL#, C.NAME, C.TYPE#, C.LENGTH,
                   NVL(C.PRECISION#, -1), NVL(C.SCALE, -1),
                   NVL(C.CHARSETFORM, 0), NVL(C.CHARSETID, 0), C.NULL$,
                   MOD(NVL(C.PROPERTY, 0), 18446744073709551616)
              FROM SYS.COL$ AS OF SCN ? C
             WHERE C.OBJ# = ?
             ORDER BY C.SEGCOL#, C.INTCOL#, C.COL#
            """;

    static final String PRIMARY_KEY_MEMBERSHIP = """
            SELECT C.INTCOL#, COUNT(*)
              FROM SYS.CDEF$ AS OF SCN ? D
              JOIN SYS.CCOL$ AS OF SCN ? C ON C.CON# = D.CON# AND C.OBJ# = D.OBJ#
             WHERE D.OBJ# = ?
               AND D.TYPE# = 2
             GROUP BY C.INTCOL#
            """;

    static final String GUARD_SEGMENTS = """
            SELECT E.COLNUM, NVL(E.GUARD_ID, -1)
              FROM SYS.ECOL$ AS OF SCN ? E
             WHERE E.TABOBJ# = ?
            """;

    static final String TABLE_PARTITIONS = """
            SELECT TP.OBJ#, NVL(TP.DATAOBJ#, 0)
              FROM SYS.TABPART$ AS OF SCN ? TP
             WHERE TP.BO# = ?
             UNION ALL
            SELECT TSP.OBJ#, NVL(TSP.DATAOBJ#, 0)
              FROM SYS.TABCOMPART$ AS OF SCN ? TCP
              JOIN SYS.TABSUBPART$ AS OF SCN ? TSP ON TSP.POBJ# = TCP.OBJ#
             WHERE TCP.BO# = ?
            """;

    static final String LOBS = """
            SELECT L.OBJ#, NVL(O.DATAOBJ#, 0), L.LOBJ#, L.COL#, L.INTCOL#,
                   NVL(TS.BLOCKSIZE, 0)
              FROM SYS.LOB$ AS OF SCN ? L
              JOIN SYS.OBJ$ AS OF SCN ? O ON O.OBJ# = L.LOBJ#
              LEFT JOIN SYS.TS$ AS OF SCN ? TS ON TS.TS# = L.TS#
             WHERE L.OBJ# = ?
             ORDER BY L.INTCOL#
            """;

    static final String OBJECT_DATA_BY_NAME = """
            SELECT NVL(O.DATAOBJ#, 0)
              FROM SYS.OBJ$ AS OF SCN ? O
             WHERE O.OWNER# = ?
               AND O.NAME = ?
               AND BITAND(MOD(O.FLAGS, 18446744073709551616), 128) = 0
             ORDER BY O.OBJ#
            """;

    static final String LOB_PARTITIONS = """
            SELECT NVL(O.DATAOBJ#, 0), NVL(TS.BLOCKSIZE, 0)
              FROM SYS.LOBFRAG$ AS OF SCN ? LF
              JOIN SYS.OBJ$ AS OF SCN ? O ON O.OBJ# = LF.FRAGOBJ#
              LEFT JOIN SYS.TS$ AS OF SCN ? TS ON TS.TS# = LF.TS#
             WHERE LF.PARENTOBJ# = ?
             UNION ALL
            SELECT NVL(O.DATAOBJ#, 0), NVL(TS.BLOCKSIZE, 0)
              FROM SYS.LOBCOMPPART$ AS OF SCN ? LCP
              JOIN SYS.LOBFRAG$ AS OF SCN ? LF ON LF.PARENTOBJ# = LCP.PARTOBJ#
              JOIN SYS.OBJ$ AS OF SCN ? O ON O.OBJ# = LF.FRAGOBJ#
              LEFT JOIN SYS.TS$ AS OF SCN ? TS ON TS.TS# = LF.TS#
             WHERE LCP.LOBJ# = ?
            """;

    static final String SYSTEM_TABLE_IDENTITY = """
            SELECT U.USER#, O.OBJ#
              FROM SYS.USER$ AS OF SCN ? U
              JOIN SYS.OBJ$ AS OF SCN ? O ON O.OWNER# = U.USER#
             WHERE U.NAME = ?
               AND O.NAME = ?
               AND O.TYPE# = 2
               AND BITAND(MOD(O.FLAGS, 18446744073709551616), 128) = 0
            """;

    static final String SYSTEM_USER_ROW = """
            SELECT ROWIDTOCHAR(U.ROWID), U.USER#, U.NAME, NVL(U.SPARE1, 0)
              FROM SYS.USER$ AS OF SCN ? U
             WHERE U.USER# = ?
            """;

    static final String SYSTEM_OBJECT_ROWS = """
            SELECT ROWIDTOCHAR(O.ROWID), O.OWNER#, O.OBJ#,
                   NVL(O.DATAOBJ#, 0), O.TYPE#, O.NAME, NVL(O.FLAGS, 0)
              FROM SYS.OBJ$ AS OF SCN ? O
             WHERE O.OBJ# = ?
            """;

    static final String SYSTEM_OBJECT_ROWS_BY_NAME = """
            SELECT ROWIDTOCHAR(O.ROWID), O.OWNER#, O.OBJ#,
                   NVL(O.DATAOBJ#, 0), O.TYPE#, O.NAME, NVL(O.FLAGS, 0)
              FROM SYS.OBJ$ AS OF SCN ? O
             WHERE O.OWNER# = ?
               AND O.NAME = ?
            """;

    static final String SYSTEM_TABLE_ROWS = """
            SELECT ROWIDTOCHAR(T.ROWID), T.OBJ#, NVL(T.DATAOBJ#, 0),
                   NVL(T.TS#, 0), NVL(T.CLUCOLS, 0),
                   NVL(T.FLAGS, 0), NVL(T.PROPERTY, 0)
              FROM SYS.TAB$ AS OF SCN ? T
             WHERE T.OBJ# = ?
            """;

    static final String SYSTEM_COLUMN_ROWS = """
            SELECT ROWIDTOCHAR(C.ROWID), C.OBJ#, C.COL#, C.SEGCOL#, C.INTCOL#,
                   C.NAME, C.TYPE#, C.LENGTH, NVL(C.PRECISION#, -1),
                   NVL(C.SCALE, -1), NVL(C.CHARSETFORM, 0),
                   NVL(C.CHARSETID, 0), C.NULL$, NVL(C.PROPERTY, 0)
              FROM SYS.COL$ AS OF SCN ? C
             WHERE C.OBJ# = ?
             ORDER BY C.SEGCOL#, C.INTCOL#, C.COL#
            """;

    static final String SYSTEM_DEFERRED_STORAGE_ROWS = """
            SELECT ROWIDTOCHAR(D.ROWID), D.OBJ#, NVL(D.FLAGS_STG, 0)
              FROM SYS.DEFERRED_STG$ AS OF SCN ? D
             WHERE D.OBJ# = ?
            """;

    static final String SYSTEM_EXTENDED_COLUMN_ROWS = """
            SELECT ROWIDTOCHAR(E.ROWID), E.TABOBJ#, NVL(E.COLNUM, 0),
                   NVL(E.GUARD_ID, -1)
              FROM SYS.ECOL$ AS OF SCN ? E
             WHERE E.TABOBJ# = ?
            """;

    static final String SYSTEM_LOB_ROWS = """
            SELECT ROWIDTOCHAR(L.ROWID), L.OBJ#, L.COL#, L.INTCOL#,
                   L.LOBJ#, L.TS#
              FROM SYS.LOB$ AS OF SCN ? L
             WHERE L.OBJ# = ?
             ORDER BY L.INTCOL#
            """;

    static final String SYSTEM_LOB_COMPOSITE_PARTITION_ROWS = """
            SELECT ROWIDTOCHAR(P.ROWID), P.PARTOBJ#, P.LOBJ#
              FROM SYS.LOBCOMPPART$ AS OF SCN ? P
             WHERE P.LOBJ# = ?
            """;

    static final String SYSTEM_LOB_FRAGMENT_ROWS = """
            SELECT ROWIDTOCHAR(F.ROWID), F.FRAGOBJ#, F.PARENTOBJ#, F.TS#
              FROM SYS.LOBFRAG$ AS OF SCN ? F
             WHERE F.PARENTOBJ# = ?
            """;

    static final String SYSTEM_CONSTRAINT_ROWS = """
            SELECT ROWIDTOCHAR(D.ROWID), D.CON#, D.OBJ#, D.TYPE#
              FROM SYS.CDEF$ AS OF SCN ? D
             WHERE D.OBJ# = ?
            """;

    static final String SYSTEM_CONSTRAINT_COLUMN_ROWS = """
            SELECT ROWIDTOCHAR(C.ROWID), C.CON#, C.INTCOL#, C.OBJ#,
                   NVL(C.SPARE1, 0)
              FROM SYS.CCOL$ AS OF SCN ? C
             WHERE C.OBJ# = ?
            """;

    static final String SYSTEM_TABLE_COMPOSITE_PARTITION_ROWS = """
            SELECT ROWIDTOCHAR(P.ROWID), P.OBJ#, NVL(P.DATAOBJ#, 0), P.BO#
              FROM SYS.TABCOMPART$ AS OF SCN ? P
             WHERE P.BO# = ?
             ORDER BY P.OBJ#
            """;

    static final String SYSTEM_TABLE_PARTITION_ROWS = """
            SELECT ROWIDTOCHAR(P.ROWID), P.OBJ#, NVL(P.DATAOBJ#, 0), P.BO#
              FROM SYS.TABPART$ AS OF SCN ? P
             WHERE P.BO# = ?
             ORDER BY P.OBJ#
            """;

    static final String SYSTEM_TABLE_SUBPARTITION_ROWS = """
            SELECT ROWIDTOCHAR(P.ROWID), P.OBJ#, NVL(P.DATAOBJ#, 0), P.POBJ#
              FROM SYS.TABSUBPART$ AS OF SCN ? P
             WHERE P.POBJ# = ?
             ORDER BY P.OBJ#
            """;

    static final String SYSTEM_TABLESPACE_ROWS = """
            SELECT ROWIDTOCHAR(T.ROWID), T.TS#, T.NAME, T.BLOCKSIZE
              FROM SYS.TS$ AS OF SCN ? T
             WHERE T.TS# = ?
            """;

    private OracleDictionarySql() {
    }
}
