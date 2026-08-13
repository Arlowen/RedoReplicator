/*
 * Java translation derived from OpenLogReplicator online archive sequencing in
 * src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class RedoLogPlanner {
    public Optional<List<OracleRedoLog>> locateStart(
            OracleRedoCatalog catalog, Scn startScn) {
        if (startScn.compareTo(catalog.databaseContext().currentScn()) > 0) {
            throw new RedoRuntimeException(10040,
                    "start SCN " + startScn + " is ahead of Oracle current SCN "
                            + catalog.databaseContext().currentScn());
        }

        Set<Integer> threads = new TreeSet<>();
        for (OracleRedoLog online : catalog.onlineLogs()) {
            if ("CURRENT".equals(online.status())) {
                threads.add(online.thread());
            }
        }
        if (threads.isEmpty()) {
            throw new RedoRuntimeException(10037,
                    "failed to find online redo log files");
        }

        List<OracleRedoLog> selected = new ArrayList<>(threads.size());
        for (int thread : threads) {
            OracleRedoLog readable = findReadableCovering(
                    catalog.archivedLogs(), thread, startScn);
            if (readable == null) {
                readable = findReadableCovering(
                        catalog.onlineLogs(), thread, startScn);
            }
            if (readable != null) {
                selected.add(readable);
                continue;
            }

            boolean covered = hasCovering(
                    catalog.archivedLogs(), thread, startScn)
                    || hasCovering(catalog.onlineLogs(), thread, startScn);
            if (covered) {
                return Optional.empty();
            }
            if (hasLaterLog(catalog.archivedLogs(), thread, startScn)
                    || hasLaterLog(catalog.onlineLogs(), thread, startScn)) {
                throw new RedoRuntimeException(10039,
                        "redo history does not cover start SCN " + startScn
                                + " for thread " + thread);
            }
            return Optional.empty();
        }
        return Optional.of(List.copyOf(selected));
    }

    public Optional<OracleRedoLog> locateNext(
            OracleRedoCatalog catalog, int thread, Seq expectedSequence) {
        boolean foundExpected = false;
        for (OracleRedoLog archived : catalog.archivedLogs()) {
            if (archived.thread() == thread
                    && archived.sequence().equals(expectedSequence)) {
                foundExpected = true;
                if (archived.isReadable()) {
                    return Optional.of(archived);
                }
            }
        }
        for (OracleRedoLog online : catalog.onlineLogs()) {
            if (online.thread() == thread
                    && online.sequence().equals(expectedSequence)) {
                foundExpected = true;
                if (online.isReadable()) {
                    return Optional.of(online);
                }
            }
        }
        if (foundExpected) {
            return Optional.empty();
        }

        if (hasLaterSequence(catalog.archivedLogs(), thread, expectedSequence)
                || hasLaterSequence(catalog.onlineLogs(), thread,
                expectedSequence)) {
            throw new RedoRuntimeException(10039,
                    "redo sequence gap for thread " + thread
                            + ": expected " + expectedSequence);
        }
        return Optional.empty();
    }

    private static OracleRedoLog findReadableCovering(
            List<OracleRedoLog> logs, int thread, Scn startScn) {
        for (OracleRedoLog log : logs) {
            if (log.thread() == thread && log.covers(startScn)
                    && log.isReadable()) {
                return log;
            }
        }
        return null;
    }

    private static boolean hasCovering(
            List<OracleRedoLog> logs, int thread, Scn startScn) {
        for (OracleRedoLog log : logs) {
            if (log.thread() == thread && log.covers(startScn)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLaterLog(
            List<OracleRedoLog> logs, int thread, Scn startScn) {
        for (OracleRedoLog log : logs) {
            if (log.thread() == thread
                    && log.firstScn().compareTo(startScn) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLaterSequence(
            List<OracleRedoLog> logs, int thread, Seq expectedSequence) {
        for (OracleRedoLog log : logs) {
            if (log.thread() == thread
                    && log.sequence().compareTo(expectedSequence) > 0) {
                return true;
            }
        }
        return false;
    }
}
