/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShutdownCoordinator implements AutoCloseable {
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final Thread shutdownHook;

    private boolean closed;

    private ShutdownCoordinator() {
        shutdownHook = new Thread(this::requestAndWait,
                "redo-replicator-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public static ShutdownCoordinator install() {
        return new ShutdownCoordinator();
    }

    public boolean stopRequested() {
        return stopRequested.get();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        stopped.countDown();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM shutdown is already running this hook.
        }
    }

    private void requestAndWait() {
        stopRequested.set(true);
        boolean interrupted = false;
        while (true) {
            try {
                stopped.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
