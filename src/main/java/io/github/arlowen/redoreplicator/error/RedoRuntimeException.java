/*
 * Java translation derived from OpenLogReplicator:
 * src/common/exception/RuntimeException.h and RuntimeException.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.error;

public final class RedoRuntimeException extends RedoReplicatorException {
    private final int supplementalCode;

    public RedoRuntimeException(int errorCode, String message) {
        this(errorCode, message, 0);
    }

    public RedoRuntimeException(int errorCode, String message, int supplementalCode) {
        super(errorCode, message);
        this.supplementalCode = supplementalCode;
    }

    public int getSupplementalCode() {
        return supplementalCode;
    }
}
