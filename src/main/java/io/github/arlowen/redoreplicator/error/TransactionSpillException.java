/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.error;

public final class TransactionSpillException
        extends RedoReplicatorException {
    public TransactionSpillException(String message, Throwable cause) {
        super(20003, message, cause);
    }
}
