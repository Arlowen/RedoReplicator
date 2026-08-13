/*
 * Java translation derived from OpenLogReplicator Transaction::flush row
 * grouping in src/parser/Transaction.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoRecordPair;
import io.github.arlowen.redoreplicator.redo.common.Xid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RedoRowGroupAssembler {
    private final List<RedoRecordPair> current = new ArrayList<>();
    private RedoRowOperation operation;

    public Optional<List<RedoRecordPair>> accept(
            RedoLogRecord undo, RedoLogRecord redo) {
        if (current.isEmpty() && redo.opCode == 0x0B16
                && undo.suppLogBdba == 0
                && (undo.suppLogFb & RedoLogRecord.FB_L) == 0) {
            return Optional.empty();
        }
        validateSameRow(undo, redo);
        operation = RedoRowOperation.append(operation, redo.opCode);
        RedoRecordPair pair = new RedoRecordPair(undo, redo);
        if (operation == RedoRowOperation.INSERT) {
            current.add(0, pair);
        } else if (redo.opCode == 0x0B06
                && !current.isEmpty()
                && current.get(current.size() - 1).redo().opCode == 0x0B02) {
            RedoRecordPair previous = current.remove(current.size() - 1);
            current.add(pair);
            current.add(previous);
        } else {
            current.add(pair);
        }

        if ((undo.suppLogFb & RedoLogRecord.FB_L) == 0) {
            return Optional.empty();
        }
        List<RedoRecordPair> complete = List.copyOf(current);
        current.clear();
        operation = null;
        return Optional.of(complete);
    }

    public void finish(Xid xid) {
        if (!current.isEmpty()) {
            throw new RedoLogException(50057,
                    "Committed transaction contains an incomplete row group: "
                            + xid);
        }
    }

    public boolean hasPendingRow() {
        return !current.isEmpty();
    }

    private void validateSameRow(
            RedoLogRecord undo, RedoLogRecord redo) {
        if (current.isEmpty()) {
            return;
        }
        RedoRecordPair first = current.get(0);
        RedoRecordPair last = current.get(current.size() - 1);
        boolean sameRow = last.undo().suppLogBdba == undo.suppLogBdba
                && last.undo().suppLogSlot == undo.suppLogSlot
                && first.undo().obj == undo.obj
                && first.redo().obj == redo.obj;
        if (!sameRow) {
            throw new RedoLogException(50057,
                    "Minimal supplemental log cannot prove one row group");
        }
    }
}
