/*
 * Java translation derived from OpenLogReplicator src/common/DbLob.h and DbLob.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import java.util.List;

public record LobSchema(long objectId, long dataObjectId, long lobObjectId,
                        int columnNumber, int internalColumn,
                        List<Long> indexDataObjectIds,
                        List<LobPartition> partitions) {
    private static final int DEFAULT_PAGE_SIZE = 8132;

    public LobSchema {
        indexDataObjectIds = List.copyOf(indexDataObjectIds);
        partitions = List.copyOf(partitions);
    }

    public int pageSize(long partitionDataObjectId) {
        for (LobPartition partition : partitions) {
            if (partition.dataObjectId() == partitionDataObjectId) {
                return partition.pageSize();
            }
        }
        return DEFAULT_PAGE_SIZE;
    }
}
