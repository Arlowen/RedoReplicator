/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

public enum SystemDictionaryTable {
    USER("SYS.USER$"),
    OBJECT("SYS.OBJ$"),
    TABLE("SYS.TAB$"),
    COLUMN("SYS.COL$"),
    CONSTRAINT("SYS.CDEF$"),
    CONSTRAINT_COLUMN("SYS.CCOL$");

    private final String qualifiedName;

    SystemDictionaryTable(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String qualifiedName() {
        return qualifiedName;
    }
}
