/*
 * Java translation derived from opcode dispatch in
 * OpenLogReplicator src/parser/Parser.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

import java.nio.ByteOrder;
import java.util.Map;

public final class RedoOpCodeDispatcher {
    private final OpCode0502 opCode0502;
    private final OpCode0504 opCode0504;
    private final OpCode0506 opCode0506;
    private final OpCode050B opCode050B;
    private final OpCode0513 opCode0513;
    private final OpCode0514 opCode0514;

    public RedoOpCodeDispatcher(ByteOrder byteOrder, long redoVersion) {
        RedoByteReader byteReader = new RedoByteReader(byteOrder);
        opCode0502 = new OpCode0502(byteReader, redoVersion);
        opCode0504 = new OpCode0504(byteReader);
        opCode0506 = new OpCode0506(byteReader);
        opCode050B = new OpCode050B(byteReader);
        opCode0513 = new OpCode0513(byteReader, redoVersion);
        opCode0514 = new OpCode0514(byteReader, redoVersion);
    }

    public boolean dispatch(RedoLogRecord record) {
        return dispatch(record, null);
    }

    public boolean dispatch(RedoLogRecord record, Map<Attribute, String> transactionAttributes) {
        switch (record.opCode) {
            case 0x0502:
                opCode0502.process(record);
                return true;
            case 0x0504:
                opCode0504.process(record);
                return true;
            case 0x0506:
                opCode0506.process(record);
                return true;
            case 0x050B:
                opCode050B.process(record);
                return true;
            case 0x0513:
                opCode0513.process(record, transactionAttributes);
                return true;
            case 0x0514:
                opCode0514.process(record, transactionAttributes);
                return true;
            default:
                return false;
        }
    }
}
