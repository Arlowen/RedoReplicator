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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SchemaCatalog {
    private final Map<String, TableSchema> tablesByName = new LinkedHashMap<>();
    private final Map<SchemaObjectKey, TableSchema> tablesByObject =
            new LinkedHashMap<>();
    private final Map<SchemaObjectKey, TableSchema> tablesByDataObject =
            new LinkedHashMap<>();
    private final Set<SchemaObjectKey> ambiguousTableDataObjects =
            new LinkedHashSet<>();
    private final Map<SchemaObjectKey, LobSchema> lobsByDataObject =
            new LinkedHashMap<>();
    private final Map<SchemaObjectKey, LobSchema> lobIndexesByDataObject =
            new LinkedHashMap<>();

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

    public void addAllMissing(SchemaCatalog catalog) {
        for (TableSchema table : catalog.tables()) {
            if (!tablesByName.containsKey(table.qualifiedName())) {
                add(table);
            }
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
        String container = table.container();
        putUnique(tablesByObject, container,
                table.objectId(), table, "object");
        putTableDataObject(container, table.dataObjectId(), table);
        for (TablePartition partition : table.partitions()) {
            putUnique(tablesByObject, container,
                    partition.objectId(), table, "partition object");
            putTableDataObject(
                    container, partition.dataObjectId(), table);
        }
        for (LobSchema lob : table.lobs()) {
            putUnique(tablesByObject, container,
                    lob.lobObjectId(), table, "LOB object");
            putTableDataObject(container, lob.dataObjectId(), table);
            putUniqueLob(lobsByDataObject, container,
                    lob.dataObjectId(), lob, "LOB data object");
            for (Long indexDataObjectId : lob.indexDataObjectIds()) {
                putTableDataObject(container, indexDataObjectId, table);
                putUniqueLob(lobIndexesByDataObject, container,
                        indexDataObjectId, lob, "LOB index");
            }
            for (LobPartition partition : lob.partitions()) {
                putTableDataObject(
                        container, partition.dataObjectId(), table);
                putUniqueLob(lobsByDataObject, container,
                        partition.dataObjectId(), lob, "LOB partition");
            }
        }
    }

    private void rebuildIndexes() {
        tablesByObject.clear();
        tablesByDataObject.clear();
        ambiguousTableDataObjects.clear();
        lobsByDataObject.clear();
        lobIndexesByDataObject.clear();
        for (TableSchema table : tablesByName.values()) {
            index(table);
        }
    }

    public Optional<TableSchema> findByObjectId(long objectId) {
        return findUnique(tablesByObject, objectId, "object");
    }

    public Optional<TableSchema> findByObjectId(
            String container, long objectId) {
        return Optional.ofNullable(tablesByObject.get(
                new SchemaObjectKey(container, objectId)));
    }

    public Optional<TableSchema> findByDataObjectId(long dataObjectId) {
        requireUnambiguous(ambiguousTableDataObjects, dataObjectId);
        return findUnique(tablesByDataObject, dataObjectId, "data object");
    }

    public Optional<TableSchema> findByDataObjectId(
            String container, long dataObjectId) {
        SchemaObjectKey key = new SchemaObjectKey(container, dataObjectId);
        if (ambiguousTableDataObjects.contains(key)) {
            throw new IllegalStateException(
                    "Ambiguous data object id " + dataObjectId
                            + " in Oracle container " + container);
        }
        return Optional.ofNullable(tablesByDataObject.get(key));
    }

    public Optional<LobSchema> findLobByDataObjectId(long dataObjectId) {
        return findUnique(lobsByDataObject, dataObjectId, "LOB data object");
    }

    public Optional<LobSchema> findLobByDataObjectId(
            String container, long dataObjectId) {
        return Optional.ofNullable(lobsByDataObject.get(
                new SchemaObjectKey(container, dataObjectId)));
    }

    public Optional<LobSchema> findLobIndexByDataObjectId(long dataObjectId) {
        return findUnique(
                lobIndexesByDataObject, dataObjectId, "LOB index");
    }

    public Optional<LobSchema> findLobIndexByDataObjectId(
            String container, long dataObjectId) {
        return Optional.ofNullable(lobIndexesByDataObject.get(
                new SchemaObjectKey(container, dataObjectId)));
    }

    public int tableCount() {
        return tablesByName.size();
    }

    private static <T> Optional<T> findUnique(
            Map<SchemaObjectKey, T> lookup, long id, String identityType) {
        T result = null;
        for (Map.Entry<SchemaObjectKey, T> entry : lookup.entrySet()) {
            if (entry.getKey().id() != id) {
                continue;
            }
            if (result != null && result != entry.getValue()) {
                throw new IllegalStateException(
                        "Ambiguous " + identityType + " id " + id
                                + " across Oracle containers");
            }
            result = entry.getValue();
        }
        return Optional.ofNullable(result);
    }

    private static void requireUnambiguous(
            Set<SchemaObjectKey> ambiguous, long id) {
        for (SchemaObjectKey key : ambiguous) {
            if (key.id() == id) {
                throw new IllegalStateException(
                        "Ambiguous data object id " + id
                                + " in Oracle container "
                                + key.container());
            }
        }
    }

    private void putTableDataObject(
            String container, long id, TableSchema table) {
        if (id == 0) {
            return;
        }
        SchemaObjectKey key = new SchemaObjectKey(container, id);
        if (ambiguousTableDataObjects.contains(key)) {
            return;
        }
        TableSchema previous = tablesByDataObject.putIfAbsent(key, table);
        if (previous != null && !previous.equals(table)) {
            tablesByDataObject.remove(key);
            ambiguousTableDataObjects.add(key);
        }
    }

    private static void putUnique(
            Map<SchemaObjectKey, TableSchema> lookup,
            String container, long id,
            TableSchema table, String identityType) {
        if (id == 0) {
            return;
        }
        SchemaObjectKey key = new SchemaObjectKey(container, id);
        TableSchema previous = lookup.putIfAbsent(key, table);
        if (previous != null && !previous.equals(table)) {
            throw new IllegalArgumentException("Duplicate " + identityType
                    + " id " + id + " in " + container
                    + " for " + previous.qualifiedName()
                    + " and " + table.qualifiedName());
        }
    }

    private static void putUniqueLob(
            Map<SchemaObjectKey, LobSchema> lookup,
            String container, long id,
            LobSchema lob, String identityType) {
        if (id == 0) {
            return;
        }
        LobSchema previous = lookup.putIfAbsent(
                new SchemaObjectKey(container, id), lob);
        if (previous != null && !previous.equals(lob)) {
            throw new IllegalArgumentException(
                    "Duplicate " + identityType + " id " + id
                            + " in " + container);
        }
    }
}
