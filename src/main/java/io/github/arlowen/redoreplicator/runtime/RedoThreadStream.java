/*
 * Java translation derived from OpenLogReplicator online redo sequencing in
 * src/replicator/Replicator.cpp and src/replicator/ReplicatorOnline.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.parser.ParsedLwn;
import io.github.arlowen.redoreplicator.redo.parser.RedoStreamParser;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadBatch;
import io.github.arlowen.redoreplicator.redo.reader.RedoFileHeader;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadStatus;
import io.github.arlowen.redoreplicator.redo.reader.RedoReader;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.source.OracleRedoCatalogPoller;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import io.github.arlowen.redoreplicator.state.RedoPosition;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RedoThreadStream implements AutoCloseable {
    private final OracleRedoCatalogPoller catalogPoller;
    private final DatabaseIdentity databaseIdentity;
    private final Scn captureStartScn;
    private final RedoTransactionBuffer transactionBuffer;
    private final int maximumReadBlocks;
    private final boolean verifyChecksum;
    private final int thread;

    private OracleRedoLog currentLog;
    private FileOffset currentStartOffset;
    private Seq expectedSequence;
    private RedoReader reader;
    private RedoStreamParser parser;
    private RedoPosition parsedPosition;
    private boolean closed;

    public RedoThreadStream(
            OracleRedoCatalogPoller catalogPoller,
            OracleRedoLog startLog,
            DatabaseIdentity databaseIdentity,
            Scn captureStartScn,
            FileOffset startOffset,
            RedoTransactionBuffer transactionBuffer,
            int maximumReadBlocks,
            boolean verifyChecksum) {
        this.catalogPoller = Objects.requireNonNull(
                catalogPoller, "catalogPoller");
        currentLog = Objects.requireNonNull(startLog, "startLog");
        this.databaseIdentity = Objects.requireNonNull(
                databaseIdentity, "databaseIdentity");
        this.captureStartScn = Objects.requireNonNull(
                captureStartScn, "captureStartScn");
        currentStartOffset = Objects.requireNonNull(
                startOffset, "startOffset");
        this.transactionBuffer = Objects.requireNonNull(
                transactionBuffer, "transactionBuffer");
        if (maximumReadBlocks <= 0) {
            throw new IllegalArgumentException(
                    "maximumReadBlocks must be positive");
        }
        this.maximumReadBlocks = maximumReadBlocks;
        this.verifyChecksum = verifyChecksum;
        thread = startLog.thread();
        expectedSequence = startLog.sequence();
    }

    public RedoThreadBatch read() throws IOException, SQLException {
        requireOpen();
        if (currentLog == null) {
            currentLog = catalogPoller.awaitNext(thread, expectedSequence);
            currentStartOffset = FileOffset.zero();
        }
        if (!openReader()) {
            return new RedoThreadBatch(
                    currentLog, RedoReadStatus.WAITING,
                    currentStartOffset, List.of());
        }

        OracleRedoLog processedLog = currentLog;
        RedoReadBatch readBatch = reader.read(maximumReadBlocks);
        List<ParsedLwn> parsedLwns = parser.accept(readBatch);
        if (!parsedLwns.isEmpty()) {
            parsedPosition = parsedLwns.get(
                    parsedLwns.size() - 1).position();
        }
        FileOffset nextOffset = reader.nextOffset();
        if (readBatch.status() == RedoReadStatus.FINISHED) {
            finishSequence();
        }
        return new RedoThreadBatch(
                processedLog, readBatch.status(), nextOffset, parsedLwns);
    }

    public int thread() {
        return thread;
    }

    public Seq expectedSequence() {
        return expectedSequence;
    }

    public Optional<OracleRedoLog> currentLog() {
        return Optional.ofNullable(currentLog);
    }

    public Optional<RedoPosition> parsedPosition() {
        return Optional.ofNullable(parsedPosition);
    }

    public Optional<RedoFileHeader> openHeader() throws IOException {
        requireOpen();
        if (!openReader()) {
            return Optional.empty();
        }
        return Optional.of(reader.header());
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (reader != null) {
            reader.close();
            reader = null;
            parser = null;
        }
    }

    private boolean openReader() throws IOException {
        if (reader != null) {
            return true;
        }
        Optional<RedoReader> opened = RedoReader.open(
                currentLog, databaseIdentity, currentStartOffset,
                verifyChecksum);
        if (opened.isEmpty()) {
            return false;
        }
        reader = opened.orElseThrow();
        parser = new RedoStreamParser(
                reader.header(), captureStartScn, currentStartOffset,
                transactionBuffer);
        return true;
    }

    private void finishSequence() throws IOException {
        reader.close();
        reader = null;
        parser = null;
        expectedSequence = currentLog.sequence().next();
        currentLog = null;
        currentStartOffset = FileOffset.zero();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Redo thread stream is closed");
        }
    }
}
