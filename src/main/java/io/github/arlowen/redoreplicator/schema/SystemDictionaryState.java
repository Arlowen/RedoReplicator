/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SystemDictionaryState {
    private final Map<SystemDictionaryKey, SystemDictionaryRow> rows;

    private SystemDictionaryState(
            Map<SystemDictionaryKey, SystemDictionaryRow> rows) {
        this.rows = Map.copyOf(rows);
    }

    public static SystemDictionaryState empty() {
        return new SystemDictionaryState(Map.of());
    }

    public static SystemDictionaryState of(Collection<SystemDictionaryRow> rows) {
        Map<SystemDictionaryKey, SystemDictionaryRow> indexed = new LinkedHashMap<>();
        for (SystemDictionaryRow row : rows) {
            SystemDictionaryKey key = new SystemDictionaryKey(
                    row.dictionaryTable(), row.rowId());
            SystemDictionaryRow previous = indexed.putIfAbsent(key, row);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate dictionary row " + key);
            }
        }
        return new SystemDictionaryState(indexed);
    }

    SystemDictionaryRow find(SystemDictionaryKey key) {
        return rows.get(key);
    }

    public List<SysUser> users() {
        return rows(SysUser.class);
    }

    public List<SysObj> objects() {
        return rows(SysObj.class);
    }

    public List<SysTab> tables() {
        return rows(SysTab.class);
    }

    public List<SysCol> columns() {
        return rows(SysCol.class);
    }

    public List<SysDeferredStg> deferredStorage() {
        return rows(SysDeferredStg.class);
    }

    public List<SysECol> extendedColumns() {
        return rows(SysECol.class);
    }

    public List<SysLob> lobs() {
        return rows(SysLob.class);
    }

    public List<SysLobCompPart> lobCompositePartitions() {
        return rows(SysLobCompPart.class);
    }

    public List<SysLobFrag> lobFragments() {
        return rows(SysLobFrag.class);
    }

    public List<SysCDef> constraints() {
        return rows(SysCDef.class);
    }

    public List<SysCCol> constraintColumns() {
        return rows(SysCCol.class);
    }

    public List<SysTabComPart> tableCompositePartitions() {
        return rows(SysTabComPart.class);
    }

    public List<SysTabPart> tablePartitions() {
        return rows(SysTabPart.class);
    }

    public List<SysTabSubPart> tableSubpartitions() {
        return rows(SysTabSubPart.class);
    }

    public List<SysTs> tablespaces() {
        return rows(SysTs.class);
    }

    public List<SystemDictionaryRow> rows() {
        return List.copyOf(rows.values());
    }

    SystemDictionaryState withChanges(
            Map<SystemDictionaryKey, SystemDictionaryRow> replacements,
            Set<SystemDictionaryKey> deletions) {
        Map<SystemDictionaryKey, SystemDictionaryRow> next = new LinkedHashMap<>(rows);
        for (SystemDictionaryKey key : deletions) {
            next.remove(key);
        }
        for (Map.Entry<SystemDictionaryKey, SystemDictionaryRow> entry
                : replacements.entrySet()) {
            SystemDictionaryRow row = entry.getValue();
            if (row.dictionaryTable() != entry.getKey().table()
                    || !row.rowId().equals(entry.getKey().rowId())) {
                throw new IllegalArgumentException(
                        "Dictionary replacement does not match " + entry.getKey());
            }
            next.put(entry.getKey(), row);
        }
        return new SystemDictionaryState(next);
    }

    private <T extends SystemDictionaryRow> List<T> rows(Class<T> type) {
        return rows.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }
}
