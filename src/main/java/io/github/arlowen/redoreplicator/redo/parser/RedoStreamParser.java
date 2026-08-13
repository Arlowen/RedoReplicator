/*
 * Java translation derived from OpenLogReplicator parser buffering in
 * src/parser/Parser.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.reader.RedoFileHeader;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadBatch;
import io.github.arlowen.redoreplicator.redo.reader.RedoReadStatus;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.state.RedoPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RedoStreamParser {
    private final RedoFileHeader fileHeader;
    private final Scn captureStartScn;
    private final RedoLwnAssembler lwnAssembler;
    private final RedoParser redoParser;
    private final RedoTransactionBuffer transactionBuffer;
    private final List<byte[]> pendingBlocks;

    private FileOffset expectedReaderOffset;
    private long pendingStartBlock;
    private RedoPosition parsedPosition;
    private boolean finished;

    public RedoStreamParser(
            RedoFileHeader fileHeader,
            Scn captureStartScn,
            FileOffset startOffset,
            RedoTransactionBuffer transactionBuffer) {
        this.fileHeader = Objects.requireNonNull(fileHeader, "fileHeader");
        this.captureStartScn = Objects.requireNonNull(
                captureStartScn, "captureStartScn");
        Objects.requireNonNull(startOffset, "startOffset");
        Objects.requireNonNull(transactionBuffer, "transactionBuffer");
        lwnAssembler = new RedoLwnAssembler(
                fileHeader.byteOrder(), fileHeader.blockSize());
        redoParser = new RedoParser(
                fileHeader.byteOrder(), fileHeader.compatibleVersion(),
                fileHeader.blockSize(), transactionBuffer);
        this.transactionBuffer = transactionBuffer;
        pendingBlocks = new ArrayList<>();
        initializeOffset(startOffset);
    }

    public List<ParsedLwn> accept(RedoReadBatch batch) {
        Objects.requireNonNull(batch, "batch");
        if (finished) {
            throw new IllegalStateException("Redo stream is already finished");
        }
        if (!batch.startOffset().equals(expectedReaderOffset)) {
            throw new RedoLogException(50046,
                    "non-contiguous redo batch, found offset "
                            + batch.startOffset() + ", expected "
                            + expectedReaderOffset);
        }

        if (batch.status() == RedoReadStatus.DATA) {
            pendingBlocks.addAll(batch.blocks());
            expectedReaderOffset = expectedReaderOffset.plus(
                    (long) batch.blocks().size() * fileHeader.blockSize());
        }
        List<ParsedLwn> parsed = parseAvailableLwns();
        if (batch.status() == RedoReadStatus.FINISHED) {
            if (!pendingBlocks.isEmpty()) {
                throw new RedoLogException(50046,
                        "redo file finished with an incomplete LWN at block "
                                + pendingStartBlock);
            }
            finished = true;
        }
        return parsed;
    }

    public Optional<RedoPosition> parsedPosition() {
        return Optional.ofNullable(parsedPosition);
    }

    public boolean isFinished() {
        return finished;
    }

    private List<ParsedLwn> parseAvailableLwns() {
        List<ParsedLwn> parsed = new ArrayList<>();
        while (!pendingBlocks.isEmpty()) {
            Optional<AssembledLwn> assembled = lwnAssembler.tryAssemble(
                    pendingBlocks,
                    pendingStartBlock,
                    fileHeader.firstScn(),
                    fileHeader.nextScn());
            if (assembled.isEmpty()) {
                break;
            }

            AssembledLwn lwn = assembled.orElseThrow();
            List<CommittedRedoTransaction> lwnTransactions =
                    redoParser.process(
                            lwn, fileHeader.sequence(), fileHeader.thread());
            List<CommittedRedoTransaction> committed = new ArrayList<>();
            for (CommittedRedoTransaction transaction : lwnTransactions) {
                if (transaction.commitPosition().scn()
                        .compareTo(captureStartScn) >= 0) {
                    committed.add(transaction);
                }
            }
            pendingBlocks.subList(
                    0, (int) lwn.consumedBlocks()).clear();
            pendingStartBlock = lwn.endBlock();
            parsedPosition = new RedoPosition(
                    lwn.scn(), fileHeader.thread(), fileHeader.sequence(),
                    FileOffset.fromBlock(
                            lwn.endBlock(), fileHeader.blockSize()));
            parsed.add(new ParsedLwn(
                    parsedPosition, committed,
                    transactionBuffer.lowWatermark()));
        }
        return List.copyOf(parsed);
    }

    private void initializeOffset(FileOffset startOffset) {
        long firstDataOffset = (long) fileHeader.blockSize() * 2;
        if (startOffset.isZero()) {
            expectedReaderOffset = FileOffset.of(firstDataOffset);
        } else {
            if (!startOffset.isBlockAligned(fileHeader.blockSize())
                    || Long.compareUnsigned(
                    startOffset.value(), firstDataOffset) < 0) {
                throw new IllegalArgumentException(
                        "redo start offset must be block aligned and after the file header");
            }
            expectedReaderOffset = startOffset;
        }
        pendingStartBlock = expectedReaderOffset.block(
                fileHeader.blockSize());
    }
}
