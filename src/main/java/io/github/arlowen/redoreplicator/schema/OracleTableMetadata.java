/*
 * Java translation derived from OpenLogReplicator SYS.USER$, SYS.OBJ$ and SYS.TAB$ metadata.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import java.util.Objects;

public record OracleTableMetadata(String owner, String name, long objectId,
                                  long dataObjectId, long userId,
                                  int clusterColumns, int options,
                                  long objectFlags, long tableFlags,
                                  long tableProperties,
                                  boolean delayedStorageCompressed) {
    public OracleTableMetadata {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
    }
}
