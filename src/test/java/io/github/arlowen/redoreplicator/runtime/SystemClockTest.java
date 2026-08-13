/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemClockTest {
    @Test
    void returnsUnixWallClockInSecondsAndMicroseconds() {
        ReplicatorClock clock = new SystemClock();
        long before = Instant.now().getEpochSecond();
        long micros = clock.currentEpochMicros();
        long seconds = clock.currentEpochSeconds();
        long after = Instant.now().getEpochSecond();

        assertTrue(seconds >= before && seconds <= after);
        assertTrue(micros / 1_000_000 >= before && micros / 1_000_000 <= after);
    }
}
