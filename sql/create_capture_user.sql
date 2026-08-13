/*
 * RedoReplicator capture-user template for one Oracle PDB.
 *
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 *
 * Run this file manually as SYSDBA after changing the three DEFINE values.
 * The application and Docker Compose never create or grant database accounts.
 */

WHENEVER SQLERROR EXIT SQL.SQLCODE
SET VERIFY OFF

DEFINE capture_pdb = FREEPDB1
DEFINE capture_user = REDO_REPLICATOR
DEFINE capture_password = "ChangeMe_123"

ALTER SESSION SET CONTAINER = &&capture_pdb;

CREATE USER &&capture_user IDENTIFIED BY "&&capture_password"
    DEFAULT TABLESPACE USERS
    TEMPORARY TABLESPACE TEMP;

GRANT CREATE SESSION TO &&capture_user;

GRANT SELECT, FLASHBACK ON SYS.CCOL$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.CDEF$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.COL$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.DEFERRED_STG$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.ECOL$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.LOB$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.LOBCOMPPART$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.LOBFRAG$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.OBJ$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.TAB$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.TABCOMPART$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.TABPART$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.TABSUBPART$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.TS$ TO &&capture_user;
GRANT SELECT, FLASHBACK ON SYS.USER$ TO &&capture_user;

GRANT SELECT ON SYS.V_$ARCHIVED_LOG TO &&capture_user;
GRANT SELECT ON SYS.V_$DATABASE TO &&capture_user;
GRANT SELECT ON SYS.V_$DATABASE_INCARNATION TO &&capture_user;
GRANT SELECT ON SYS.V_$INSTANCE TO &&capture_user;
GRANT SELECT ON SYS.V_$LOG TO &&capture_user;
GRANT SELECT ON SYS.V_$LOGFILE TO &&capture_user;
GRANT SELECT ON SYS.V_$PARAMETER TO &&capture_user;
GRANT SELECT ON SYS.V_$PDBS TO &&capture_user;

PROMPT Capture account created in &&capture_pdb.
PROMPT Put &&capture_user and its password in redo-replicator.yaml.
