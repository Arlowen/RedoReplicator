/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode0B04.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

public final class OpCode0B04 {
    private final RedoByteReader byteReader;

    public OpCode0B04(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public void process(RedoLogRecord record) {
        RedoOpCodeSupport.readRowHeaders(byteReader, record, 0x0B0401);
    }
}
