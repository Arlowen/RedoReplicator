/*
 * Java translation derived from OpenLogReplicator Transaction::flush commit
 * metadata and row grouping.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoRecordPair;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.state.RedoPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CommittedRedoTransaction(
        Xid xid,
        int containerId,
        int thread,
        RedoPosition beginPosition,
        RedoTime beginTimestamp,
        RedoPosition commitPosition,
        RedoTime commitTimestamp,
        Map<Attribute, String> attributes,
        List<RedoTransactionEntry> entries) {
    public CommittedRedoTransaction {
        Objects.requireNonNull(xid, "xid");
        Objects.requireNonNull(beginPosition, "beginPosition");
        Objects.requireNonNull(beginTimestamp, "beginTimestamp");
        Objects.requireNonNull(commitPosition, "commitPosition");
        Objects.requireNonNull(commitTimestamp, "commitTimestamp");
        attributes = Map.copyOf(attributes);
        entries = List.copyOf(entries);
    }

    public List<List<RedoRecordPair>> rowGroups() {
        List<List<RedoRecordPair>> groups = new ArrayList<>();
        List<RedoRecordPair> current = new ArrayList<>();
        RedoRowOperation operation = null;
        for (RedoTransactionEntry entry : entries) {
            if (!entry.paired() || entry.first().opCode != 0x0501) {
                continue;
            }
            RedoLogRecord redo = entry.second().orElseThrow();
            if (!isRowOpCode(redo.opCode)) {
                continue;
            }
            RedoLogRecord undo = entry.first();
            if (current.isEmpty() && redo.opCode == 0x0B16
                    && undo.suppLogBdba == 0
                    && (undo.suppLogFb & RedoLogRecord.FB_L) == 0) {
                continue;
            }
            validateSameRow(current, undo, redo);
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

            if ((undo.suppLogFb & RedoLogRecord.FB_L) != 0) {
                groups.add(List.copyOf(current));
                current.clear();
                operation = null;
            }
        }
        if (!current.isEmpty()) {
            throw new RedoLogException(50057,
                    "Committed transaction contains an incomplete row group: "
                            + xid);
        }
        return List.copyOf(groups);
    }

    private static boolean isRowOpCode(int opCode) {
        return opCode == 0x0B02 || opCode == 0x0B03
                || opCode == 0x0B05 || opCode == 0x0B06
                || opCode == 0x0B08 || opCode == 0x0B10
                || opCode == 0x0B16;
    }

    private static void validateSameRow(
            List<RedoRecordPair> current,
            RedoLogRecord undo,
            RedoLogRecord redo) {
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
