/*
 * Java translation derived from OpenLogReplicator:
 * src/common/ClockHW.h and src/common/ClockHW.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import java.time.Instant;

public final class SystemClock implements ReplicatorClock {
    @Override
    public long currentEpochMicros() {
        Instant now = Instant.now();
        return now.getEpochSecond() * 1_000_000 + now.getNano() / 1_000;
    }

    @Override
    public long currentEpochSeconds() {
        return Instant.now().getEpochSecond();
    }
}
