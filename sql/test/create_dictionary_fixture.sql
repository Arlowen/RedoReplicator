/*
 * Manual Oracle dictionary integration-test fixture.
 * Run as PDBADMIN in the selected PDB, separately from the capture account.
 *
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */

WHENEVER SQLERROR EXIT SQL.SQLCODE
SET VERIFY OFF

DEFINE fixture_pdb = FREEPDB1
DEFINE fixture_user = APP
DEFINE fixture_password = "RedoFixture_26ai"

ALTER SESSION SET CONTAINER = &&fixture_pdb;

CREATE USER &&fixture_user IDENTIFIED BY "&&fixture_password"
    DEFAULT TABLESPACE USERS
    TEMPORARY TABLESPACE TEMP
    QUOTA 100M ON USERS;

GRANT CREATE SESSION, CREATE TABLE TO &&fixture_user;

CREATE TABLE &&fixture_user..REDO_DICTIONARY_TEST (
    ID NUMBER(10) NOT NULL,
    TENANT_ID NUMBER(10) NOT NULL,
    NAME VARCHAR2(100),
    CREATED_AT TIMESTAMP WITH TIME ZONE,
    PAYLOAD CLOB,
    SYS_VERSION NUMBER INVISIBLE,
    CONSTRAINT PK_REDO_DICTIONARY_TEST PRIMARY KEY (ID, TENANT_ID)
) LOB (PAYLOAD) STORE AS BASICFILE;

ALTER TABLE &&fixture_user..REDO_DICTIONARY_TEST
    ADD SUPPLEMENTAL LOG DATA (PRIMARY KEY) COLUMNS;
