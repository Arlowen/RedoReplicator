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
            case DEFERRED_STORAGE -> patchDeferredStorage(
                    rowId, (SysDeferredStg) current, values);
            case EXTENDED_COLUMN -> patchExtendedColumn(
                    rowId, (SysECol) current, values);
            case LOB -> patchLob(rowId, (SysLob) current, values);
            case LOB_COMPOSITE_PARTITION -> patchLobCompositePartition(
                    rowId, (SysLobCompPart) current, values);
            case LOB_FRAGMENT -> patchLobFragment(
                    rowId, (SysLobFrag) current, values);
            case CONSTRAINT -> patchConstraint(rowId, (SysCDef) current, values);
            case CONSTRAINT_COLUMN -> patchConstraintColumn(
                    rowId, (SysCCol) current, values);
            case TABLE_COMPOSITE_PARTITION -> patchTableCompositePartition(
                    rowId, (SysTabComPart) current, values);
            case TABLE_PARTITION -> patchTablePartition(
                    rowId, (SysTabPart) current, values);
            case TABLE_SUBPARTITION -> patchTableSubpartition(
                    rowId, (SysTabSubPart) current, values);
            case TABLESPACE -> patchTablespace(rowId, (SysTs) current, values);
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

    private SysDeferredStg patchDeferredStorage(
            RowId rowId, SysDeferredStg current,
            Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysDeferredStg(rowId, 0, IntX.zero());
        }
        return new SysDeferredStg(
                rowId,
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                intX(values, "FLAGS_STG", current.flagsStg()));
    }

    private SysECol patchExtendedColumn(
            RowId rowId, SysECol current,
            Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysECol(rowId, 0, 0, -1);
        }
        return new SysECol(
                rowId,
                unsignedLong(values, "TABOBJ#", current.tableObjectId(), 0),
                integer(values, "COLNUM", current.columnNumber(), 0),
                integer(values, "GUARD_ID", current.guardId(), -1));
    }

    private SysLob patchLob(RowId rowId, SysLob current,
                            Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysLob(rowId, 0, 0, 0, 0, 0);
        }
        return new SysLob(
                rowId,
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                integer(values, "COL#", current.columnNumber(), 0),
                integer(values, "INTCOL#", current.internalColumn(), 0),
                unsignedLong(values, "LOBJ#", current.lobObjectId(), 0),
                unsignedLong(values, "TS#", current.tablespaceId(), 0));
    }

    private SysLobCompPart patchLobCompositePartition(
            RowId rowId, SysLobCompPart current,
            Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysLobCompPart(rowId, 0, 0);
        }
        return new SysLobCompPart(
                rowId,
                unsignedLong(values, "PARTOBJ#", current.partitionObjectId(), 0),
                unsignedLong(values, "LOBJ#", current.lobObjectId(), 0));
    }

    private SysLobFrag patchLobFragment(
            RowId rowId, SysLobFrag current,
            Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysLobFrag(rowId, 0, 0, 0);
        }
        return new SysLobFrag(
                rowId,
                unsignedLong(values, "FRAGOBJ#", current.fragmentObjectId(), 0),
                unsignedLong(values, "PARENTOBJ#", current.parentObjectId(), 0),
                unsignedLong(values, "TS#", current.tablespaceId(), 0));
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

    private SysTabComPart patchTableCompositePartition(
            RowId rowId, SysTabComPart current,
            Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysTabComPart(rowId, 0, 0, 0);
        }
        return new SysTabComPart(
                rowId,
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                unsignedLong(values, "DATAOBJ#", current.dataObjectId(), 0),
                unsignedLong(values, "BO#", current.baseObjectId(), 0));
    }

    private SysTabPart patchTablePartition(
            RowId rowId, SysTabPart current,
            Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysTabPart(rowId, 0, 0, 0);
        }
        return new SysTabPart(
                rowId,
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                unsignedLong(values, "DATAOBJ#", current.dataObjectId(), 0),
                unsignedLong(values, "BO#", current.baseObjectId(), 0));
    }

    private SysTabSubPart patchTableSubpartition(
            RowId rowId, SysTabSubPart current,
            Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysTabSubPart(rowId, 0, 0, 0);
        }
        return new SysTabSubPart(
                rowId,
                unsignedLong(values, "OBJ#", current.objectId(), 0),
                unsignedLong(values, "DATAOBJ#", current.dataObjectId(), 0),
                unsignedLong(values, "POBJ#", current.parentObjectId(), 0));
    }

    private SysTs patchTablespace(RowId rowId, SysTs current,
                                  Map<String, SystemDictionaryValue> values) {
        if (current == null) {
            current = new SysTs(rowId, 0, "", 0);
        }
        return new SysTs(
                rowId,
                unsignedLong(values, "TS#", current.tablespaceId(), 0),
                text(values, "NAME", current.name(), SysTs.NAME_LENGTH),
                integer(values, "BLOCKSIZE", current.blockSize(), 0));
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
