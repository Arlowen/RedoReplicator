/*
 * Java translation derived from OpenLogReplicator parser/Parser.cpp record,
 * opcode and transaction routing.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.redo.transaction.RedoUndoBlockMerger;
import io.github.arlowen.redoreplicator.state.RedoPosition;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RedoParser {
    private final RedoVectorParser vectorParser;
    private final RedoOpCodeDispatcher opCodeDispatcher;
    private final RedoTransactionBuffer transactionBuffer;
    private final RedoUndoBlockMerger undoBlockMerger;
    private final int blockSize;

    public RedoParser(ByteOrder byteOrder, long redoVersion, int blockSize) {
        this(byteOrder, redoVersion, blockSize,
                new RedoTransactionBuffer());
    }

    public RedoParser(
            ByteOrder byteOrder,
            long redoVersion,
            int blockSize,
            RedoTransactionBuffer transactionBuffer) {
        vectorParser = new RedoVectorParser(
                byteOrder, redoVersion, blockSize);
        opCodeDispatcher = new RedoOpCodeDispatcher(byteOrder, redoVersion);
        undoBlockMerger = new RedoUndoBlockMerger(byteOrder);
        this.transactionBuffer = Objects.requireNonNull(
                transactionBuffer, "transactionBuffer");
        this.blockSize = blockSize;
    }

    public List<CommittedRedoTransaction> process(
            AssembledLwn lwn, Seq sequence, int thread) {
        if (lwn.records().isEmpty()) {
            return List.of();
        }
        RedoPosition lwnPosition = new RedoPosition(
                lwn.scn(),
                thread,
                sequence,
                FileOffset.fromBlock(lwn.startBlock(), blockSize));
        List<CommittedRedoTransaction> committed = new ArrayList<>();
        for (AssembledRedoRecord assembledRecord : lwn.records()) {
            List<RedoLogRecord> vectors = vectorParser.parseAll(
                    assembledRecord, sequence, lwn.timestamp(), thread);
            processVectors(vectors, lwnPosition, committed);
        }
        return List.copyOf(committed);
    }

    public RedoTransactionBuffer transactionBuffer() {
        return transactionBuffer;
    }

    private void processVectors(
            List<RedoLogRecord> vectors,
            RedoPosition lwnPosition,
            List<CommittedRedoTransaction> committed) {
        int index = 0;
        while (index < vectors.size()) {
            RedoLogRecord first = vectors.get(index);
            RedoLogRecord second = null;
            if (index + 1 < vectors.size()) {
                second = vectors.get(index + 1);
            }
            dispatch(first, null);
            boolean pair = first.opCode == 0x0501
                    && second != null && isUndoPairSecond(second.opCode);
            boolean rollbackPair = isRowOpCode(first.opCode)
                    && second != null && isPartialRollback(second.opCode);
            if (first.opCode == 0x0501) {
                byte[] undoData = first.data();
                Optional<RedoLogRecord> prepared = transactionBuffer.prepareUndo(
                        first, pair, undoBlockMerger);
                if (prepared.isEmpty()) {
                    if (pair) {
                        dispatch(second, first);
                        index += 2;
                    } else {
                        index++;
                    }
                    continue;
                }
                RedoLogRecord preparedUndo = prepared.orElseThrow();
                if (preparedUndo.data() != undoData) {
                    first = preparedUndo;
                    dispatch(first, null);
                }
            }
            if (pair || rollbackPair) {
                dispatch(second, first);
                committed.addAll(transactionBuffer.accept(
                        List.of(first, second), lwnPosition));
                index += 2;
                continue;
            }
            committed.addAll(transactionBuffer.accept(
                    List.of(first), lwnPosition));
            index++;
        }
    }

    private void dispatch(RedoLogRecord vector, RedoLogRecord previous) {
        if (vector.encryptedTablespace) {
            throw new RedoLogException(50057,
                    "Encrypted redo vector is not supported at offset "
                            + vector.fileOffset);
        }
        Map<Attribute, String> attributes = null;
        if ((vector.opCode == 0x0513 || vector.opCode == 0x0514)
                && previous != null && previous.opCode == 0x0501) {
            attributes = transactionBuffer.attributes(
                    previous.xid, previous.conId).orElse(null);
        }
        if (!opCodeDispatcher.dispatch(vector, attributes)) {
            throw new RedoLogException(50057,
                    "Unsupported redo opcode 0x"
                            + Integer.toHexString(vector.opCode)
                            + " at offset " + vector.fileOffset);
        }
    }

    private static boolean isUndoPairSecond(int opCode) {
        return (opCode & 0xFF00) == 0x0A00
                || (opCode & 0xFF00) == 0x0B00
                || opCode == 0x0513 || opCode == 0x0514
                || opCode == 0x1A02;
    }

    private static boolean isRowOpCode(int opCode) {
        return (opCode & 0xFF00) == 0x0B00;
    }

    private static boolean isPartialRollback(int opCode) {
        return opCode == 0x0506 || opCode == 0x050B;
    }
}
