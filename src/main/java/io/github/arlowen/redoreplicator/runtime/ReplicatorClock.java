/*
 * Java translation derived from OpenLogReplicator: src/common/Clock.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

public interface ReplicatorClock {
    long currentEpochMicros();

    long currentEpochSeconds();
}
