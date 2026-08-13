/*
 * Java translation derived from OpenLogReplicator:
 * src/common/exception/BootException.h and BootException.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.error;

public final class BootException extends RedoReplicatorException {
    public BootException(int errorCode, String message) {
        super(errorCode, message);
    }
}
