/*
 * Java translation derived from OpenLogReplicator TransactionChunk entries.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

import java.util.Objects;
import java.util.Optional;

public final class RedoTransactionEntry {
    private final RedoLogRecord first;
    private final RedoLogRecord second;

    private RedoTransactionEntry(
            RedoLogRecord first, RedoLogRecord second) {
        this.first = Objects.requireNonNull(first, "first");
        this.second = second;
    }

    public static RedoTransactionEntry single(RedoLogRecord record) {
        return new RedoTransactionEntry(record, null);
    }

    public static RedoTransactionEntry pair(
            RedoLogRecord first, RedoLogRecord second) {
        return new RedoTransactionEntry(
                first, Objects.requireNonNull(second, "second"));
    }

    public RedoLogRecord first() {
        return first;
    }

    public Optional<RedoLogRecord> second() {
        return Optional.ofNullable(second);
    }

    public boolean paired() {
        return second != null;
    }

    public int operationCode() {
        int secondOpCode = 0;
        if (second != null) {
            secondOpCode = second.opCode;
        }
        return first.opCode << 16 | secondOpCode;
    }
}
