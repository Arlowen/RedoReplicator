/*
 * Java translation derived from LWN boundary handling in
 * OpenLogReplicator src/parser/Parser.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RedoLwnAssembler {
    private static final int RECORD_OFFSET = 16;

    private final RedoRecordHeaderParser headerParser;
    private final ByteOrder byteOrder;
    private final int blockSize;

    public RedoLwnAssembler(ByteOrder byteOrder, int blockSize) {
        this.byteOrder = byteOrder;
        this.blockSize = blockSize;
        headerParser = new RedoRecordHeaderParser(new RedoByteReader(byteOrder));
        if (blockSize != 512 && blockSize != 1024 && blockSize != 4096) {
            throw new IllegalArgumentException("Unsupported redo block size: " + blockSize);
        }
    }

    public Optional<AssembledLwn> tryAssemble(List<byte[]> blocks, long startBlock,
                                               Scn firstScn, Scn nextScn) {
        if (startBlock < 0 || startBlock > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Redo block must be an unsigned 32-bit value");
        }
        if (blocks.isEmpty()) {
            return Optional.empty();
        }

        RedoRecordAssembler recordAssembler = new RedoRecordAssembler(byteOrder, blockSize);
        List<RedoLwnHeader> partHeaders = new ArrayList<>();
        long currentBlock = startBlock;
        long partEndBlock = startBlock;
        int maximumParts = 0;
        Scn lwnScn = Scn.zero();
        RedoTime lwnTimestamp = RedoTime.zero();

        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            byte[] block = blocks.get(blockIndex);
            requireBlockSize(block);

            if (currentBlock == partEndBlock) {
                if (recordAssembler.hasIncompleteRecord()) {
                    throw new RedoLogException(50046, "redo record crosses an lwn part boundary");
                }
                RedoLwnHeader partHeader = parsePartHeader(block);
                long partBlockCount = partHeader.blockCount();
                if (partBlockCount == 0 || partBlockCount > 0xFFFF_FFFFL - currentBlock + 1) {
                    throw new RedoLogException(50051, "invalid lwn block count: " + partBlockCount);
                }
                int validatedMaximum = validatePartHeader(
                        partHeader, partHeaders, maximumParts, firstScn, nextScn);
                if (partBlockCount > blocks.size() - blockIndex) {
                    return Optional.empty();
                }

                partEndBlock = currentBlock + partBlockCount;
                lwnScn = partHeader.scn();
                lwnTimestamp = partHeader.timestamp();
                maximumParts = validatedMaximum;
                partHeaders.add(partHeader);
            }

            recordAssembler.acceptBlock(block, currentBlock);
            currentBlock++;

            if (currentBlock == partEndBlock && partHeaders.size() == maximumParts) {
                if (recordAssembler.hasIncompleteRecord()) {
                    throw new RedoLogException(50046, "incomplete redo record at lwn boundary");
                }
                return Optional.of(new AssembledLwn(startBlock, currentBlock, lwnScn,
                        lwnTimestamp, partHeaders, recordAssembler.recordsInOrder()));
            }
            if (partHeaders.size() > maximumParts) {
                throw new RedoLogException(50055, "lwn overflow: " + partHeaders.size()
                        + "/" + maximumParts);
            }
        }
        return Optional.empty();
    }

    private RedoLwnHeader parsePartHeader(byte[] block) {
        RedoByteReader byteReader = new RedoByteReader(byteOrder);
        long rawSize = byteReader.readUnsignedInt(block, RECORD_OFFSET);
        if (rawSize > Integer.MAX_VALUE) {
            throw new RedoLogException(50053, "too big redo log record, size: " + rawSize);
        }
        return headerParser.parseLwn(block, RECORD_OFFSET, (int) rawSize);
    }

    private static int validatePartHeader(RedoLwnHeader partHeader,
                                          List<RedoLwnHeader> partHeaders,
                                          int maximumParts, Scn firstScn, Scn nextScn) {
        if (partHeaders.isEmpty()) {
            if (partHeader.scn().compareTo(firstScn) < 0
                    || (!nextScn.isNone() && partHeader.scn().compareTo(nextScn) > 0)) {
                throw new RedoLogException(50049, "invalid lwn scn: " + partHeader.scn());
            }
            return partHeader.maximum();
        }
        if (partHeader.maximum() != maximumParts) {
            throw new RedoLogException(50050, "invalid lwn max: " + partHeader.number()
                    + "/" + partHeader.maximum() + "/" + maximumParts);
        }
        return maximumParts;
    }

    private void requireBlockSize(byte[] block) {
        if (block.length != blockSize) {
            throw new RedoLogException(50046, "redo block size is " + block.length
                    + ", expected: " + blockSize);
        }
    }
}
