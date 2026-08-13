/*
 * RedoReplicator capture-user template for one CDB and all of its PDBs.
 *
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 *
 * Run this file manually as SYSDBA in CDB$ROOT after changing the two DEFINE
 * values. The application and Docker Compose never create or grant accounts.
 */

WHENEVER SQLERROR EXIT SQL.SQLCODE
SET VERIFY OFF

DEFINE capture_user = C##REDO_REPLICATOR
DEFINE capture_password = "ChangeMe_123"

ALTER SESSION SET CONTAINER = CDB$ROOT;

CREATE USER &&capture_user IDENTIFIED BY "&&capture_password"
    CONTAINER = ALL;

GRANT CREATE SESSION, SET CONTAINER TO &&capture_user CONTAINER = ALL;

GRANT SELECT, FLASHBACK ON SYS.CCOL$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.CDEF$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.COL$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.DEFERRED_STG$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.ECOL$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.LOB$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.LOBCOMPPART$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.LOBFRAG$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.OBJ$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.TAB$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.TABCOMPART$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.TABPART$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.TABSUBPART$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.TS$ TO &&capture_user CONTAINER = ALL;
GRANT SELECT, FLASHBACK ON SYS.USER$ TO &&capture_user CONTAINER = ALL;

GRANT SELECT ON SYS.V_$ARCHIVED_LOG TO &&capture_user CONTAINER = ALL;
GRANT SELECT ON SYS.V_$DATABASE TO &&capture_user CONTAINER = ALL;
GRANT SELECT ON SYS.V_$DATABASE_INCARNATION TO &&capture_user CONTAINER = ALL;
GRANT SELECT ON SYS.V_$INSTANCE TO &&capture_user CONTAINER = ALL;
GRANT SELECT ON SYS.V_$LOG TO &&capture_user CONTAINER = ALL;
GRANT SELECT ON SYS.V_$LOGFILE TO &&capture_user CONTAINER = ALL;
GRANT SELECT ON SYS.V_$PARAMETER TO &&capture_user CONTAINER = ALL;
GRANT SELECT ON SYS.V_$PDBS TO &&capture_user CONTAINER = ALL;

ALTER USER &&capture_user
    SET CONTAINER_DATA = ALL
    CONTAINER = CURRENT;

PROMPT Common capture account created for this CDB.
PROMPT Connect to the CDB root service and put &&capture_user in the YAML.
