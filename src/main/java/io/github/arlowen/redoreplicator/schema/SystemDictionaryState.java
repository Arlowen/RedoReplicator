/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.RowId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SystemDictionaryState {
    private final Map<RowId, SysUser> users;
    private final Map<RowId, SysObj> objects;
    private final Map<RowId, SysTab> tables;
    private final Map<RowId, SysCol> columns;
    private final Map<RowId, SysCDef> constraints;
    private final Map<RowId, SysCCol> constraintColumns;

    private SystemDictionaryState(Map<RowId, SysUser> users,
                                  Map<RowId, SysObj> objects,
                                  Map<RowId, SysTab> tables,
                                  Map<RowId, SysCol> columns,
                                  Map<RowId, SysCDef> constraints,
                                  Map<RowId, SysCCol> constraintColumns) {
        this.users = Map.copyOf(users);
        this.objects = Map.copyOf(objects);
        this.tables = Map.copyOf(tables);
        this.columns = Map.copyOf(columns);
        this.constraints = Map.copyOf(constraints);
        this.constraintColumns = Map.copyOf(constraintColumns);
    }

    public static SystemDictionaryState empty() {
        return new SystemDictionaryState(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public static SystemDictionaryState of(Collection<SystemDictionaryRow> rows) {
        Map<SystemDictionaryKey, SystemDictionaryRow> replacements = new LinkedHashMap<>();
        for (SystemDictionaryRow row : rows) {
            SystemDictionaryKey key = new SystemDictionaryKey(
                    row.dictionaryTable(), row.rowId());
            SystemDictionaryRow previous = replacements.putIfAbsent(key, row);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate dictionary row " + key);
            }
        }
        return empty().withChanges(replacements, Set.of());
    }

    SystemDictionaryRow find(SystemDictionaryKey key) {
        return switch (key.table()) {
            case USER -> users.get(key.rowId());
            case OBJECT -> objects.get(key.rowId());
            case TABLE -> tables.get(key.rowId());
            case COLUMN -> columns.get(key.rowId());
            case CONSTRAINT -> constraints.get(key.rowId());
            case CONSTRAINT_COLUMN -> constraintColumns.get(key.rowId());
        };
    }

    public List<SysUser> users() {
        return List.copyOf(users.values());
    }

    public List<SysObj> objects() {
        return List.copyOf(objects.values());
    }

    public List<SysTab> tables() {
        return List.copyOf(tables.values());
    }

    public List<SysCol> columns() {
        return List.copyOf(columns.values());
    }

    public List<SysCDef> constraints() {
        return List.copyOf(constraints.values());
    }

    public List<SysCCol> constraintColumns() {
        return List.copyOf(constraintColumns.values());
    }

    public List<SystemDictionaryRow> rows() {
        List<SystemDictionaryRow> rows = new ArrayList<>();
        rows.addAll(users.values());
        rows.addAll(objects.values());
        rows.addAll(tables.values());
        rows.addAll(columns.values());
        rows.addAll(constraints.values());
        rows.addAll(constraintColumns.values());
        return List.copyOf(rows);
    }

    SystemDictionaryState withChanges(
            Map<SystemDictionaryKey, SystemDictionaryRow> replacements,
            Set<SystemDictionaryKey> deletions) {
        Map<RowId, SysUser> nextUsers = new LinkedHashMap<>(users);
        Map<RowId, SysObj> nextObjects = new LinkedHashMap<>(objects);
        Map<RowId, SysTab> nextTables = new LinkedHashMap<>(tables);
        Map<RowId, SysCol> nextColumns = new LinkedHashMap<>(columns);
        Map<RowId, SysCDef> nextConstraints = new LinkedHashMap<>(constraints);
        Map<RowId, SysCCol> nextConstraintColumns = new LinkedHashMap<>(
                constraintColumns);

        for (SystemDictionaryKey key : deletions) {
            remove(key, nextUsers, nextObjects, nextTables, nextColumns,
                    nextConstraints, nextConstraintColumns);
        }
        for (Map.Entry<SystemDictionaryKey, SystemDictionaryRow> entry
                : replacements.entrySet()) {
            put(entry.getKey(), entry.getValue(), nextUsers, nextObjects,
                    nextTables, nextColumns, nextConstraints, nextConstraintColumns);
        }
        return new SystemDictionaryState(
                nextUsers, nextObjects, nextTables, nextColumns,
                nextConstraints, nextConstraintColumns);
    }

    private static void remove(SystemDictionaryKey key,
                               Map<RowId, SysUser> users,
                               Map<RowId, SysObj> objects,
                               Map<RowId, SysTab> tables,
                               Map<RowId, SysCol> columns,
                               Map<RowId, SysCDef> constraints,
                               Map<RowId, SysCCol> constraintColumns) {
        switch (key.table()) {
            case USER -> users.remove(key.rowId());
            case OBJECT -> objects.remove(key.rowId());
            case TABLE -> tables.remove(key.rowId());
            case COLUMN -> columns.remove(key.rowId());
            case CONSTRAINT -> constraints.remove(key.rowId());
            case CONSTRAINT_COLUMN -> constraintColumns.remove(key.rowId());
        }
    }

    private static void put(SystemDictionaryKey key, SystemDictionaryRow row,
                            Map<RowId, SysUser> users,
                            Map<RowId, SysObj> objects,
                            Map<RowId, SysTab> tables,
                            Map<RowId, SysCol> columns,
                            Map<RowId, SysCDef> constraints,
                            Map<RowId, SysCCol> constraintColumns) {
        switch (key.table()) {
            case USER -> users.put(key.rowId(), (SysUser) row);
            case OBJECT -> objects.put(key.rowId(), (SysObj) row);
            case TABLE -> tables.put(key.rowId(), (SysTab) row);
            case COLUMN -> columns.put(key.rowId(), (SysCol) row);
            case CONSTRAINT -> constraints.put(key.rowId(), (SysCDef) row);
            case CONSTRAINT_COLUMN -> constraintColumns.put(
                    key.rowId(), (SysCCol) row);
        }
    }
}
