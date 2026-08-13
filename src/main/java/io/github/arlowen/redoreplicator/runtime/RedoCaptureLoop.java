/*
 * Java translation derived from OpenLogReplicator continuous online reader
 * lifecycle in src/replicator/Replicator.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.redo.parser.ParsedLwn;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadStatus;
import io.github.arlowen.redoreplicator.state.RuntimeState;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

final class RedoCaptureLoop {
    private final RedoBatchSource batchSource;
    private final RedoLwnConsumer lwnConsumer;
    private final RedoStatusConsumer statusConsumer;
    private final BooleanSupplier stopRequested;
    private final long idleWaitMillis;
    private final LongConsumer sleeper;

    RedoCaptureLoop(
            RedoBatchSource batchSource,
            RedoLwnConsumer lwnConsumer,
            RedoStatusConsumer statusConsumer,
            BooleanSupplier stopRequested,
            long idleWaitMillis) {
        this(batchSource, lwnConsumer, statusConsumer, stopRequested,
                idleWaitMillis, RedoCaptureLoop::sleep);
    }

    RedoCaptureLoop(
            RedoBatchSource batchSource,
            RedoLwnConsumer lwnConsumer,
            RedoStatusConsumer statusConsumer,
            BooleanSupplier stopRequested,
            long idleWaitMillis,
            LongConsumer sleeper) {
        this.batchSource = Objects.requireNonNull(
                batchSource, "batchSource");
        this.lwnConsumer = Objects.requireNonNull(
                lwnConsumer, "lwnConsumer");
        this.statusConsumer = Objects.requireNonNull(
                statusConsumer, "statusConsumer");
        this.stopRequested = Objects.requireNonNull(
                stopRequested, "stopRequested");
        if (idleWaitMillis <= 0) {
            throw new IllegalArgumentException(
                    "idleWaitMillis must be positive");
        }
        this.idleWaitMillis = idleWaitMillis;
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    void run() throws IOException, SQLException {
        boolean running = true;
        while (running && !stopRequested.getAsBoolean()) {
            RedoThreadBatch batch = batchSource.read();
            for (ParsedLwn lwn : batch.parsedLwns()) {
                if (stopRequested.getAsBoolean()) {
                    running = false;
                    break;
                }
                RuntimeState state = lwnConsumer.process(lwn);
                statusConsumer.update(batch.redoLog(), lwn, state);
            }
            if (running && batch.status() == RedoReadStatus.WAITING
                    && !stopRequested.getAsBoolean()) {
                sleeper.accept(idleWaitMillis);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedoRuntimeException(
                    10041, "interrupted while waiting for online redo growth");
        }
    }
}
