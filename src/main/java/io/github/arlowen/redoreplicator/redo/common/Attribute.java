/*
 * Java translation derived from OpenLogReplicator: src/common/Attribute.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum Attribute {
    VERSION("version"),
    AUDIT_SESSION_ID("audit session id"),
    SESSION_NUMBER("session number"),
    SERIAL_NUMBER("serial number"),
    CURRENT_USER_NAME("current user name"),
    LOGIN_USER_NAME("login username"),
    CLIENT_INFO("client info"),
    OS_USER_NAME("os username"),
    MACHINE_NAME("machine name"),
    OS_TERMINAL("os terminal"),
    OS_PROCESS_ID("os process id"),
    OS_PROGRAM_NAME("os program name"),
    TRANSACTION_NAME("transaction name"),
    CLIENT_ID("client id"),
    DDL_TRANSACTION("ddl transaction"),
    SPACE_MANAGEMENT_TRANSACTION("space management transaction"),
    RECURSIVE_TRANSACTION("recursive transaction"),
    LOGMINER_INTERNAL_TRANSACTION("logminer internal transaction"),
    DB_OPEN_IN_MIGRATE_MODE("db open in migrate mode"),
    LSBY_IGNORE("lsby ignore"),
    LOGMINER_NO_TX_CHUNKING("logminer no transaction chunking"),
    LOGMINER_STEALTH_TRANSACTION("logminer stealth transaction"),
    LSBY_PRESERVE("lsby preserve"),
    LOGMINER_MARKER_TRANSACTION("logminer marker transaction"),
    TRANSACTION_IN_PRAGMAED_PLSQL("transaction in pragma'ed plsql"),
    DISABLED_LOGICAL_REPLICATION_TRANSACTION("disabled logical replication transaction"),
    DATAPUMP_IMPORT_TRANSACTION("datapump import transaction"),
    TRANSACTION_AUDIT_CV_FLAGS_UNDEFINED("transaction audit CV flags undefined"),
    FEDERATION_PDB_REPLAY("federation pdb replay"),
    PDB_DDL_REPLAY("pdb ddl replay"),
    LOGMINER_SKIP_TRANSACTION("logminer skip transaction"),
    SEQ_UPDATE_TRANSACTION("seq$ update transaction");

    private static final Map<String, Attribute> BY_NAME;

    static {
        Map<String, Attribute> attributes = new LinkedHashMap<>();
        for (Attribute attribute : values()) {
            attributes.put(attribute.externalName, attribute);
        }
        BY_NAME = Collections.unmodifiableMap(attributes);
    }

    private final String externalName;

    Attribute(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }

    public static Map<String, Attribute> fromString() {
        return BY_NAME;
    }

    @Override
    public String toString() {
        return externalName;
    }
}
