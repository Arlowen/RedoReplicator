/*
 * Run as PDBADMIN after the dictionary integration test.
 *
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */

WHENEVER SQLERROR EXIT SQL.SQLCODE

DEFINE fixture_pdb = '&1'
DEFINE fixture_user = '&2'

ALTER SESSION SET CONTAINER = &&fixture_pdb;

DROP USER &&fixture_user CASCADE;
