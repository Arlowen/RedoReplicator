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
    DEFERRED_STORAGE("SYS.DEFERRED_STG$"),
    EXTENDED_COLUMN("SYS.ECOL$"),
    LOB("SYS.LOB$"),
    LOB_COMPOSITE_PARTITION("SYS.LOBCOMPPART$"),
    LOB_FRAGMENT("SYS.LOBFRAG$"),
    CONSTRAINT("SYS.CDEF$"),
    CONSTRAINT_COLUMN("SYS.CCOL$"),
    TABLE_COMPOSITE_PARTITION("SYS.TABCOMPART$"),
    TABLE_PARTITION("SYS.TABPART$"),
    TABLE_SUBPARTITION("SYS.TABSUBPART$"),
    TABLESPACE("SYS.TS$");

    private final String qualifiedName;

    SystemDictionaryTable(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String qualifiedName() {
        return qualifiedName;
    }
}
