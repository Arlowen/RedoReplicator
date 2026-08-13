/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

public record RuntimeState(long databaseId, long incarnation, long resetlogsId,
                           RedoPosition durablePosition,
                           Optional<RedoPosition> lowWatermarkPosition,
                           long jsonlFileNumber, long jsonlFsyncOffset,
                           String configFingerprint, OffsetDateTime updatedAt) {
    public RuntimeState {
        Objects.requireNonNull(durablePosition, "durablePosition");
        Objects.requireNonNull(lowWatermarkPosition, "lowWatermarkPosition");
        Objects.requireNonNull(configFingerprint, "configFingerprint");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
