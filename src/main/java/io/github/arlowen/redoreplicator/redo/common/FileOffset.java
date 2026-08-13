/*
 * Java translation derived from OpenLogReplicator: src/common/types/FileOffset.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import java.io.Serial;
import java.io.Serializable;

public final class FileOffset
        implements Comparable<FileOffset>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final FileOffset ZERO = new FileOffset(0);

    private final long value;

    private FileOffset(long value) {
        this.value = value;
    }

    public static FileOffset of(long value) {
        if (value == 0) {
            return ZERO;
        }
        return new FileOffset(value);
    }

    public static FileOffset fromBlock(long block, int blockSize) {
        if (block < 0 || block > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Redo block must be an unsigned 32-bit value");
        }
        requireBlockSize(blockSize);
        return of(block * blockSize);
    }

    public static FileOffset zero() {
        return ZERO;
    }

    public long value() {
        return value;
    }

    public FileOffset plus(long bytes) {
        return of(value + bytes);
    }

    public FileOffset minus(FileOffset other) {
        return of(value - other.value);
    }

    public boolean isBlockAligned(int blockSize) {
        requireBlockSize(blockSize);
        return (value & (blockSize - 1L)) == 0;
    }

    public int withinBlockOffset(int blockSize) {
        requireBlockSize(blockSize);
        return (int) (value & (blockSize - 1L));
    }

    public long block(int blockSize) {
        requireBlockSize(blockSize);
        long block = Long.divideUnsigned(value, blockSize);
        return Integer.toUnsignedLong((int) block);
    }

    public boolean isZero() {
        return value == 0;
    }

    public String toHex(int width) {
        return String.format("%0" + width + "x", value);
    }

    @Override
    public int compareTo(FileOffset other) {
        return Long.compareUnsigned(value, other.value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FileOffset fileOffset)) {
            return false;
        }
        return value == fileOffset.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return Long.toUnsignedString(value);
    }

    private static void requireBlockSize(int blockSize) {
        if (blockSize <= 0 || (blockSize & (blockSize - 1)) != 0) {
            throw new IllegalArgumentException("Block size must be a positive power of two");
        }
    }
}
