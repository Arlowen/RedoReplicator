/*
 * Java translation derived from OpenLogReplicator src/common/DbTable.h and DbTable.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record TableSchema(String container, String owner, String name,
                          long objectId, long dataObjectId, long userId,
                          int clusterColumns, int options,
                          List<ColumnSchema> columns, List<LobSchema> lobs,
                          List<TablePartition> partitions) {
    public TableSchema {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        columns = List.copyOf(columns);
        lobs = List.copyOf(lobs);
        partitions = List.copyOf(partitions);

        int expectedSegmentColumn = 1;
        for (ColumnSchema column : columns) {
            if (column.segmentColumn() != expectedSegmentColumn) {
                throw new IllegalArgumentException("Column " + column.name()
                        + " has segment position " + column.segmentColumn()
                        + ", expected " + expectedSegmentColumn);
            }
            expectedSegmentColumn++;
        }
        for (LobSchema lob : lobs) {
            if (lob.objectId() != objectId) {
                throw new IllegalArgumentException(
                        "LOB object " + lob.lobObjectId()
                                + " belongs to table object " + lob.objectId()
                                + ", expected " + objectId);
            }
        }
    }

    public String qualifiedName() {
        if (container.isEmpty()) {
            return owner + "." + name;
        }
        return container + "." + owner + "." + name;
    }

    public List<Integer> primaryKeyColumnIndexes() {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).primaryKeyMembership() > 0) {
                indexes.add(index);
            }
        }
        return List.copyOf(indexes);
    }
}
