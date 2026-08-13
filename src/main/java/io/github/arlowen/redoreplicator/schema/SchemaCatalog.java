/*
 * Java translation derived from OpenLogReplicator schema object lookup.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SchemaCatalog {
    private final Map<String, TableSchema> tablesByName = new LinkedHashMap<>();
    private final Map<Long, TableSchema> tablesByObject = new LinkedHashMap<>();
    private final Map<Long, TableSchema> tablesByDataObject = new LinkedHashMap<>();
    private final Map<Long, LobSchema> lobsByDataObject = new LinkedHashMap<>();
    private final Map<Long, LobSchema> lobIndexesByDataObject = new LinkedHashMap<>();

    public void add(TableSchema table) {
        TableSchema previous = tablesByName.putIfAbsent(
                table.qualifiedName(), table);
        if (previous != null) {
            if (!previous.equals(table)) {
                throw new IllegalArgumentException(
                        "Duplicate table schema " + table.qualifiedName());
            }
            return;
        }
        index(table);
    }

    public void replace(TableSchema table) {
        tablesByName.put(table.qualifiedName(), table);
        rebuildIndexes();
    }

    public void remove(String container, String owner, String table) {
        tablesByName.entrySet().removeIf(entry -> {
            TableSchema schema = entry.getValue();
            return schema.container().equals(container)
                    && schema.owner().equals(owner)
                    && schema.name().equals(table);
        });
        rebuildIndexes();
    }

    public void addAll(SchemaCatalog catalog) {
        for (TableSchema table : catalog.tables()) {
            add(table);
        }
    }

    public SchemaCatalog copy() {
        SchemaCatalog copy = new SchemaCatalog();
        copy.addAll(this);
        return copy;
    }

    public List<TableSchema> tables() {
        return List.copyOf(tablesByName.values());
    }

    private void index(TableSchema table) {
        putUnique(tablesByObject, table.objectId(), table, "object");
        putUnique(tablesByDataObject, table.dataObjectId(), table, "data object");
        for (TablePartition partition : table.partitions()) {
            putUnique(tablesByObject, partition.objectId(), table, "partition object");
            putUnique(tablesByDataObject,
                    partition.dataObjectId(), table, "partition data object");
        }
        for (LobSchema lob : table.lobs()) {
            putUnique(tablesByObject, lob.lobObjectId(), table, "LOB object");
            putUnique(tablesByDataObject, lob.dataObjectId(), table, "LOB data object");
            putUniqueLob(lobsByDataObject, lob.dataObjectId(), lob, "LOB data object");
            for (Long indexDataObjectId : lob.indexDataObjectIds()) {
                putUnique(tablesByDataObject, indexDataObjectId, table, "LOB index");
                putUniqueLob(lobIndexesByDataObject,
                        indexDataObjectId, lob, "LOB index");
            }
            for (LobPartition partition : lob.partitions()) {
                putUnique(tablesByDataObject,
                        partition.dataObjectId(), table, "LOB partition");
                putUniqueLob(lobsByDataObject,
                        partition.dataObjectId(), lob, "LOB partition");
            }
        }
    }

    private void rebuildIndexes() {
        tablesByObject.clear();
        tablesByDataObject.clear();
        lobsByDataObject.clear();
        lobIndexesByDataObject.clear();
        for (TableSchema table : tablesByName.values()) {
            index(table);
        }
    }

    public Optional<TableSchema> findByObjectId(long objectId) {
        return Optional.ofNullable(tablesByObject.get(objectId));
    }

    public Optional<TableSchema> findByDataObjectId(long dataObjectId) {
        return Optional.ofNullable(tablesByDataObject.get(dataObjectId));
    }

    public Optional<LobSchema> findLobByDataObjectId(long dataObjectId) {
        return Optional.ofNullable(lobsByDataObject.get(dataObjectId));
    }

    public Optional<LobSchema> findLobIndexByDataObjectId(long dataObjectId) {
        return Optional.ofNullable(lobIndexesByDataObject.get(dataObjectId));
    }

    public int tableCount() {
        return tablesByName.size();
    }

    private static void putUnique(Map<Long, TableSchema> lookup, long key,
                                  TableSchema table, String identityType) {
        if (key == 0) {
            return;
        }
        TableSchema previous = lookup.putIfAbsent(key, table);
        if (previous != null && !previous.equals(table)) {
            throw new IllegalArgumentException("Duplicate " + identityType + " id " + key
                    + " for " + previous.qualifiedName() + " and " + table.qualifiedName());
        }
    }

    private static void putUniqueLob(Map<Long, LobSchema> lookup, long key,
                                     LobSchema lob, String identityType) {
        if (key == 0) {
            return;
        }
        LobSchema previous = lookup.putIfAbsent(key, lob);
        if (previous != null && !previous.equals(lob)) {
            throw new IllegalArgumentException(
                    "Duplicate " + identityType + " id " + key);
        }
    }
}
