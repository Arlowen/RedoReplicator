/*
 * Java translation derived from OpenLogReplicator src/builder/Builder.cpp DDL mapping.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

public enum DdlOperation {
    CREATE,
    ALTER,
    DROP,
    TRUNCATE,
    PURGE,
    OTHER;

    public static DdlOperation fromOracleCode(int oracleCode) {
        if (oracleCode == 1 || oracleCode == 4 || oracleCode == 9) {
            return CREATE;
        }
        if (oracleCode == 11 || oracleCode == 15) {
            return ALTER;
        }
        if (oracleCode == 8 || oracleCode == 12) {
            return DROP;
        }
        if (oracleCode == 85) {
            return TRUNCATE;
        }
        if (oracleCode == 198) {
            return PURGE;
        }
        return OTHER;
    }
}
