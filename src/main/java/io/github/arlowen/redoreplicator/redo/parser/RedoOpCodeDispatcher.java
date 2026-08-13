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
    private final OpCode0501 opCode0501;
    private final OpCode0502 opCode0502;
    private final OpCode0504 opCode0504;
    private final OpCode0506 opCode0506;
    private final OpCode050B opCode050B;
    private final OpCode0513 opCode0513;
    private final OpCode0514 opCode0514;
    private final OpCode0A02 opCode0A02;
    private final OpCode0A08 opCode0A08;
    private final OpCode0A12 opCode0A12;
    private final OpCode0B02 opCode0B02;
    private final OpCode0B03 opCode0B03;
    private final OpCode0B04 opCode0B04;
    private final OpCode0B05 opCode0B05;
    private final OpCode0B06 opCode0B06;
    private final OpCode0B08 opCode0B08;
    private final OpCode0B0B opCode0B0B;
    private final OpCode0B0C opCode0B0C;
    private final OpCode0B10 opCode0B10;
    private final OpCode0B16 opCode0B16;
    private final OpCode1301 opCode1301;
    private final OpCode1801 opCode1801;
    private final OpCode1A02 opCode1A02;
    private final OpCode1A06 opCode1A06;

    public RedoOpCodeDispatcher(ByteOrder byteOrder, long redoVersion) {
        RedoByteReader byteReader = new RedoByteReader(byteOrder);
        opCode0501 = new OpCode0501(byteReader);
        opCode0502 = new OpCode0502(byteReader, redoVersion);
        opCode0504 = new OpCode0504(byteReader);
        opCode0506 = new OpCode0506(byteReader);
        opCode050B = new OpCode050B(byteReader);
        opCode0513 = new OpCode0513(byteReader, redoVersion);
        opCode0514 = new OpCode0514(byteReader, redoVersion);
        opCode0A02 = new OpCode0A02(byteReader);
        opCode0A08 = new OpCode0A08(byteReader);
        opCode0A12 = new OpCode0A12(byteReader);
        opCode0B02 = new OpCode0B02(byteReader);
        opCode0B03 = new OpCode0B03(byteReader);
        opCode0B04 = new OpCode0B04(byteReader);
        opCode0B05 = new OpCode0B05(byteReader);
        opCode0B06 = new OpCode0B06(byteReader);
        opCode0B08 = new OpCode0B08(byteReader);
        opCode0B0B = new OpCode0B0B(byteReader);
        opCode0B0C = new OpCode0B0C(byteReader);
        opCode0B10 = new OpCode0B10(byteReader);
        opCode0B16 = new OpCode0B16(byteReader);
        opCode1301 = new OpCode1301(byteReader);
        opCode1801 = new OpCode1801(byteReader);
        opCode1A02 = new OpCode1A02(byteReader);
        opCode1A06 = new OpCode1A06(byteReader);
    }

    public boolean dispatch(RedoLogRecord record) {
        return dispatch(record, null);
    }

    public boolean dispatch(RedoLogRecord record, Map<Attribute, String> transactionAttributes) {
        switch (record.opCode) {
            case 0x0501:
                opCode0501.process(record);
                return true;
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
            case 0x0A02:
                opCode0A02.process(record);
                return true;
            case 0x0A08:
                opCode0A08.process(record);
                return true;
            case 0x0A12:
                opCode0A12.process(record);
                return true;
            case 0x0B02:
                opCode0B02.process(record);
                return true;
            case 0x0B03:
                opCode0B03.process(record);
                return true;
            case 0x0B04:
                opCode0B04.process(record);
                return true;
            case 0x0B05:
                opCode0B05.process(record);
                return true;
            case 0x0B06:
                opCode0B06.process(record);
                return true;
            case 0x0B08:
                opCode0B08.process(record);
                return true;
            case 0x0B0B:
                opCode0B0B.process(record);
                return true;
            case 0x0B0C:
                opCode0B0C.process(record);
                return true;
            case 0x0B10:
                opCode0B10.process(record);
                return true;
            case 0x0B16:
                opCode0B16.process(record);
                return true;
            case 0x1301:
                opCode1301.process(record);
                return true;
            case 0x1801:
                opCode1801.process(record);
                return true;
            case 0x1A02:
                opCode1A02.process(record);
                return true;
            case 0x1A06:
                opCode1A06.process(record);
                return true;
            default:
                return false;
        }
    }
}
