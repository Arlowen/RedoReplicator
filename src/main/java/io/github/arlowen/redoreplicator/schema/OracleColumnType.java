/*
 * Java translation derived from OpenLogReplicator src/common/table/SysCol.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

public enum OracleColumnType {
    NONE(0, false),
    VARCHAR(1, true),
    NUMBER(2, true),
    LONG(8, false),
    DATE(12, true),
    RAW(23, true),
    LONG_RAW(24, false),
    XMLTYPE(58, true),
    CHAR(96, true),
    BINARY_FLOAT(100, true),
    BINARY_DOUBLE(101, true),
    CLOB(112, true),
    BLOB(113, true),
    JSON(119, false),
    TIMESTAMP(180, true),
    TIMESTAMP_WITH_TIME_ZONE(181, true),
    INTERVAL_YEAR_TO_MONTH(182, true),
    INTERVAL_DAY_TO_SECOND(183, true),
    UROWID(208, true),
    TIMESTAMP_WITH_LOCAL_TIME_ZONE(231, true),
    BOOLEAN(252, true);

    private final int code;
    private final boolean supported;

    OracleColumnType(int code, boolean supported) {
        this.code = code;
        this.supported = supported;
    }

    public int code() {
        return code;
    }

    public boolean supported() {
        return supported;
    }

    public static OracleColumnType fromCode(int code) {
        for (OracleColumnType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return NONE;
    }
}
