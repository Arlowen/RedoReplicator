/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.Scn;

import java.sql.Connection;
import java.util.Optional;

final class FixedReferenceDictionaryLoader
        implements SystemDictionaryStateLoader {
    private final SystemDictionaryState referenceData;

    FixedReferenceDictionaryLoader(SystemDictionaryState referenceData) {
        this.referenceData = referenceData;
    }

    @Override
    public SystemDictionaryState loadReferenceData(
            Connection connection, Scn targetScn) {
        return referenceData;
    }

    @Override
    public Optional<SystemDictionaryState> loadTable(
            Connection connection, String owner, String table, Scn targetScn) {
        return Optional.empty();
    }
}
