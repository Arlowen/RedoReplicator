/*
 * Java translation derived from cross-block redo record buffering in
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
import io.github.arlowen.redoreplicator.redo.common.Scn;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RedoRecordAssembler {
    private static final int BLOCK_HEADER_SIZE = 16;
    private static final int RECORD_LOOKAHEAD_SIZE = 20;
    private static final int MAX_RECORD_SIZE = 1_048_536;
    private static final int MAX_RECORDS_IN_LWN = 1_048_576;

    private final RedoByteReader byteReader;
    private final int blockSize;
    private final List<AssembledRedoRecord> records = new ArrayList<>();

    private byte[] pendingData;
    private LwnMember pendingMember;
    private int pendingPosition;

    public RedoRecordAssembler(ByteOrder byteOrder, int blockSize) {
        this.byteReader = new RedoByteReader(byteOrder);
        this.blockSize = blockSize;
        if (blockSize != 512 && blockSize != 1024 && blockSize != 4096) {
            throw new IllegalArgumentException("Unsupported redo block size: " + blockSize);
        }
    }

    public void acceptBlock(byte[] block, long blockNumber) {
        if (block.length != blockSize) {
            throw new RedoLogException(50046, "redo block size is " + block.length
                    + ", expected: " + blockSize);
        }
        if (blockNumber < 0 || blockNumber > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Redo block must be an unsigned 32-bit value");
        }

        int blockOffset = BLOCK_HEADER_SIZE;
        while (blockOffset < blockSize) {
            if (pendingData == null) {
                if (blockOffset + RECORD_LOOKAHEAD_SIZE >= blockSize) {
                    break;
                }
                long rawSize = byteReader.readUnsignedInt(block, blockOffset);
                if (rawSize == 0) {
                    break;
                }
                startRecord(block, blockOffset, blockNumber, rawSize);
            }

            int bytesToCopy = pendingData.length - pendingPosition;
            int available = blockSize - blockOffset;
            if (bytesToCopy > available) {
                bytesToCopy = available;
            }
            System.arraycopy(block, blockOffset, pendingData, pendingPosition, bytesToCopy);
            pendingPosition += bytesToCopy;
            blockOffset += bytesToCopy;

            if (pendingPosition == pendingData.length) {
                records.add(new AssembledRedoRecord(pendingMember, pendingData));
                pendingData = null;
                pendingMember = null;
                pendingPosition = 0;
            }
        }
    }

    public boolean hasIncompleteRecord() {
        return pendingData != null;
    }

    public List<AssembledRedoRecord> recordsInOrder() {
        List<AssembledRedoRecord> ordered = new ArrayList<>(records);
        Collections.sort(ordered);
        return List.copyOf(ordered);
    }

    private void startRecord(byte[] block, int blockOffset, long blockNumber, long rawSize) {
        long alignedSize = (rawSize + 3) & ~3L;
        if (alignedSize < 14) {
            throw new RedoLogException(50046, "too small redo log record, size: " + alignedSize);
        }
        if (alignedSize > MAX_RECORD_SIZE) {
            throw new RedoLogException(50053, "too big redo log record, size: " + alignedSize);
        }
        if (records.size() + 1 >= MAX_RECORDS_IN_LWN) {
            throw new RedoLogException(50054, "all " + (records.size() + 1)
                    + " records in lwn were used");
        }

        long scnLow = byteReader.readUnsignedInt(block, blockOffset + 8);
        long scnHigh = byteReader.readUnsignedShort(block, blockOffset + 6);
        Scn scn = Scn.fromWords(scnHigh, scnLow);
        int subScn = byteReader.readUnsignedShort(block, blockOffset + 12);
        pendingMember = new LwnMember(blockOffset, scn, alignedSize, blockNumber, subScn);
        pendingData = new byte[(int) alignedSize];
        pendingPosition = 0;
    }
}
