/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.redo.common.Xid;

record TransactionSlotKey(int containerId, int undoSegment, int slot) {
    static TransactionSlotKey of(int containerId, Xid xid) {
        return new TransactionSlotKey(
                containerId, xid.unsignedUndoSegment(), xid.slot());
    }

    static TransactionSlotKey of(
            int containerId, int undoSegment, int slot) {
        return new TransactionSlotKey(
                containerId, undoSegment & 0xFFFF, slot & 0xFFFF);
    }
}
