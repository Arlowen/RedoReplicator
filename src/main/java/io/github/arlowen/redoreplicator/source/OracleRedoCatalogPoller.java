/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.config.ResolvedConfiguration;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.runtime.ReplicatorClock;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class OracleRedoCatalogPoller {
    private final Callable<OracleRedoCatalog> catalogLoader;
    private final RedoLogPlanner planner;
    private final long pollIntervalSeconds;
    private final long missingFileTimeoutSeconds;
    private final LongSupplier epochSeconds;
    private final LongConsumer sleeper;
    private DatabaseIdentity databaseIdentity;

    public OracleRedoCatalogPoller(
            Connection connection,
            ResolvedConfiguration configuration,
            ReplicatorClock clock) {
        OracleRedoCatalogReader reader = new OracleRedoCatalogReader();
        catalogLoader = () -> reader.read(
                connection, configuration.redoPathMapper());
        planner = new RedoLogPlanner();
        pollIntervalSeconds = configuration.configuration().archive()
                .pollIntervalSeconds();
        missingFileTimeoutSeconds = configuration.configuration().archive()
                .missingFileTimeoutSeconds();
        epochSeconds = clock::currentEpochSeconds;
        sleeper = OracleRedoCatalogPoller::sleep;
    }

    OracleRedoCatalogPoller(
            Callable<OracleRedoCatalog> catalogLoader,
            RedoLogPlanner planner,
            long pollIntervalSeconds,
            long missingFileTimeoutSeconds,
            LongSupplier epochSeconds,
            LongConsumer sleeper) {
        this.catalogLoader = catalogLoader;
        this.planner = planner;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.missingFileTimeoutSeconds = missingFileTimeoutSeconds;
        this.epochSeconds = epochSeconds;
        this.sleeper = sleeper;
    }

    public List<OracleRedoLog> awaitStart(Scn startScn) throws SQLException {
        return await(catalog -> planner.locateStart(catalog, startScn),
                "redo covering start SCN " + startScn);
    }

    public OracleRedoLog awaitNext(int thread, Seq expectedSequence)
            throws SQLException {
        return await(catalog -> planner.locateNext(
                        catalog, thread, expectedSequence),
                "redo thread " + thread + " sequence " + expectedSequence);
    }

    private <T> T await(
            Function<OracleRedoCatalog, Optional<T>> locator,
            String description) throws SQLException {
        long startedAt = epochSeconds.getAsLong();
        while (true) {
            Optional<T> located = locator.apply(loadCatalog());
            if (located.isPresent()) {
                return located.get();
            }

            long elapsed = epochSeconds.getAsLong() - startedAt;
            if (elapsed >= missingFileTimeoutSeconds) {
                throw new RedoRuntimeException(10042,
                        "timed out waiting for " + description + " after "
                                + missingFileTimeoutSeconds + " seconds");
            }
            long remaining = missingFileTimeoutSeconds - elapsed;
            sleeper.accept(Math.min(pollIntervalSeconds, remaining));
        }
    }

    private OracleRedoCatalog loadCatalog() throws SQLException {
        OracleRedoCatalog catalog;
        try {
            catalog = catalogLoader.call();
        } catch (SQLException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to load Oracle redo catalog", e);
        }
        if (databaseIdentity == null) {
            databaseIdentity = catalog.databaseContext().identity();
        } else if (!databaseIdentity.equals(
                catalog.databaseContext().identity())) {
            throw new RedoRuntimeException(10043,
                    "Oracle database identity changed while waiting for redo");
        }
        return catalog;
    }

    private static void sleep(long seconds) {
        try {
            Thread.sleep(seconds * 1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedoRuntimeException(10041,
                    "interrupted while waiting for Oracle redo files");
        }
    }
}
