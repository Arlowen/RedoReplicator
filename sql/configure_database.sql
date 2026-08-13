/*
 * Manual database-level settings required by RedoReplicator.
 *
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 *
 * Review and run as SYSDBA in CDB$ROOT. The application never changes these
 * settings. ARCHIVELOG mode must already be enabled before this script runs.
 */

WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER SESSION SET CONTAINER = CDB$ROOT;

DECLARE
    force_logging SYS.V_$DATABASE.FORCE_LOGGING%TYPE;
    supplemental_log_data_min SYS.V_$DATABASE.SUPPLEMENTAL_LOG_DATA_MIN%TYPE;
BEGIN
    SELECT D.FORCE_LOGGING, D.SUPPLEMENTAL_LOG_DATA_MIN
      INTO force_logging, supplemental_log_data_min
      FROM SYS.V_$DATABASE D;

    IF force_logging <> 'YES' THEN
        EXECUTE IMMEDIATE 'ALTER DATABASE FORCE LOGGING';
    END IF;
    IF supplemental_log_data_min <> 'YES' THEN
        EXECUTE IMMEDIATE 'ALTER DATABASE ADD SUPPLEMENTAL LOG DATA';
    END IF;
END;
/

SELECT LOG_MODE, FORCE_LOGGING, SUPPLEMENTAL_LOG_DATA_MIN
  FROM SYS.V_$DATABASE;
