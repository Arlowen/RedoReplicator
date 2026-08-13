/*
 * Java translation derived from OpenLogReplicator
 * src/builder/SystemTransaction.cpp updateValues.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.common.RowId;

import java.nio.charset.Charset;
import java.util.Map;

final class SystemDictionaryRowPatcher {
    private final SystemDictionaryValueDecoder decoder;

    SystemDictionaryRowPatcher(Charset characterSet) {
        decoder = new SystemDictionaryValueDecoder(characterSet);
    }

    SystemDictionaryRow apply(SystemDictionaryTable table, RowId rowId,
                              SystemDictionaryRow current,
                              Map<String, SystemDictionaryValue> values) {
        if (current != null && current.dictionaryTable() != table) {
            throw new DataException(50020,
                    "Dictionary row type does not match " + table.qualifiedName());
        }
        return switch (table) {
            case USER -> patchUser(rowId, (SysUser) current, values);
            case OBJECT -> patchObject(rowId, (SysObj) current, values);
            case TABLE -> patchTable(rowId, (SysTab) current, values);
            case COLUMN -> patchColumn(rowId, (SysCol) current, values);
            case CONSTRAINT -> patchConstraint(rowId, (SysCDef) current, values);
            case CONSTRAINT_COLUMN -> patchConstraintColumn(
                    rowId, (SysCCol) current, values);
        };
    }

    private SysUser patchUser(RowId rowId, SysUser current,
                              Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysUser(rowId, 0, "", IntX.zero());
        }
        return new SysUser(
                rowId,
                unsignedLong(values, "USER#", current.userId(), 0),
                text(values, "NAME", current.name(), SysUser.NAME_LENGTH),
                intX(values, "SPARE1", current.spare1()));
    }

    private SysObj patchObject(RowId rowId, SysObj current,
                               Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysObj(rowId, 0, 0, 0, 0, "", IntX.zero());
        }
        return new SysObj(
                rowId,
                unsignedLong(values, "OWNER#", current.ownerId(), 0),
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                unsignedLong(values, "DATAOBJ#", current.dataObjectId(), 0),
                integer(values, "TYPE#", current.typeCode(), 0),
                text(values, "NAME", current.name(), SysObj.NAME_LENGTH),
                intX(values, "FLAGS", current.flags()));
    }

    private SysTab patchTable(RowId rowId, SysTab current,
                              Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysTab(
                    rowId, 0, 0, 0, 0, IntX.zero(), IntX.zero());
        }
        return new SysTab(
                rowId,
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                unsignedLong(values, "DATAOBJ#", current.dataObjectId(), 0),
                unsignedLong(values, "TS#", current.tablespaceId(), 0),
                integer(values, "CLUCOLS", current.clusterColumns(), 0),
                intX(values, "FLAGS", current.flags()),
                intX(values, "PROPERTY", current.properties()));
    }

    private SysCol patchColumn(RowId rowId, SysCol current,
                               Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysCol(
                    rowId, 0, 0, 0, 0, "", 0, 0,
                    -1, -1, 0, 0, 0, IntX.zero());
        }
        return new SysCol(
                rowId,
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                integer(values, "COL#", current.columnNumber(), 0),
                integer(values, "SEGCOL#", current.segmentColumn(), 0),
                integer(values, "INTCOL#", current.internalColumn(), 0),
                text(values, "NAME", current.name(), SysCol.NAME_LENGTH),
                integer(values, "TYPE#", current.typeCode(), 0),
                integer(values, "SIZE", current.length(), 0),
                integer(values, "PRECISION#", current.precision(), -1),
                integer(values, "SCALE", current.scale(), -1),
                integer(values, "CHARSETFORM", current.charsetForm(), 0),
                unsignedLong(values, "CHARSETID", current.charsetId(), 0),
                integer(values, "NULL$", current.nullFlag(), 0),
                intX(values, "PROPERTY", current.properties()));
    }

    private SysCDef patchConstraint(RowId rowId, SysCDef current,
                                    Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysCDef(rowId, 0, 0, 0);
        }
        return new SysCDef(
                rowId,
                unsignedLong(values, "CON#", current.constraintId(), 0),
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                integer(values, "TYPE#", current.typeCode(), 0));
    }

    private SysCCol patchConstraintColumn(
            RowId rowId, SysCCol current,
            Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysCCol(rowId, 0, 0, 0, IntX.zero());
        }
        return new SysCCol(
                rowId,
                unsignedLong(values, "CON#", current.constraintId(), 0),
                integer(values, "INTCOL#", current.internalColumn(), 0),
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                intX(values, "SPARE1", current.spare1()));
    }

    private long unsignedLong(Map<String, SystemDictionaryValue> values,
                              String column, long current, long nullDefault) {
        SystemDictionaryValue value = values.get(column);
        if (value == null) {
            return current;
        }
        return decoder.unsignedLong(value, column, nullDefault);
    }

    private int integer(Map<String, SystemDictionaryValue> values,
                        String column, int current, int nullDefault) {
        SystemDictionaryValue value = values.get(column);
        if (value == null) {
            return current;
        }
        return decoder.integer(value, column, nullDefault);
    }

    private IntX intX(Map<String, SystemDictionaryValue> values,
                      String column, IntX current) {
        SystemDictionaryValue value = values.get(column);
        if (value == null) {
            return current;
        }
        return decoder.intX(value, column);
    }

    private String text(Map<String, SystemDictionaryValue> values,
                        String column, String current, int maxLength) {
        SystemDictionaryValue value = values.get(column);
        if (value == null) {
            return current;
        }
        return decoder.text(value, column, maxLength);
    }
}
