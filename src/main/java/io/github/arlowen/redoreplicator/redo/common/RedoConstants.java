/*
 * Java translation derived from OpenLogReplicator: src/common/types/Types.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

public final class RedoConstants {
    private RedoConstants() {
    }

    public static long undoBlockAddressBlock(long undoBlockAddress) {
        return undoBlockAddress & 0xFFFF_FFFFL;
    }

    public static int undoBlockAddressSequence(long undoBlockAddress) {
        return (int) ((undoBlockAddress >>> 32) & 0xFFFFL);
    }

    public static int undoBlockAddressRecord(long undoBlockAddress) {
        return (int) ((undoBlockAddress >>> 48) & 0xFFL);
    }

    public static String formatUndoBlockAddress(long undoBlockAddress) {
        return String.format("0x%08x.%04x.%02x",
                undoBlockAddressBlock(undoBlockAddress),
                undoBlockAddressSequence(undoBlockAddress),
                undoBlockAddressRecord(undoBlockAddress));
    }
}
