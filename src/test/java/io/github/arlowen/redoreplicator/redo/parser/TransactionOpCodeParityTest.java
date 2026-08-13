/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransactionOpCodeParityTest {
    private static final String FIXTURE =
            "/fixtures/redo-header/openlogreplicator-6bc92bc1.properties";
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);
    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws IOException {
        baseline = new Properties();
        try (InputStream input = TransactionOpCodeParityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedBeginAndCommitFields() {
        byte[] beginField = RedoOpCodeTestSupport.field(32);
        RedoBinaryTestSupport.writeUnsignedShort(
                beginField, 0, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                beginField, 4, 0x89AB_CDEFL, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                beginField, 16, 0x0108, ByteOrder.LITTLE_ENDIAN);
        byte[] databaseField = RedoOpCodeTestSupport.field(4);
        RedoBinaryTestSupport.writeUnsignedInt(
                databaseField, 0, 0xF000_0001L, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord begin = RedoOpCodeTestSupport.record(
                0x0502, 7, beginField, databaseField);
        dispatcher.dispatch(begin);

        byte[] commitField = RedoOpCodeTestSupport.field(20);
        RedoBinaryTestSupport.writeUnsignedShort(
                commitField, 0, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                commitField, 4, 0x1020_3040L, ByteOrder.LITTLE_ENDIAN);
        commitField[16] = 0x06;
        RedoLogRecord commit = RedoOpCodeTestSupport.record(0x0504, 8, commitField);
        dispatcher.dispatch(commit);

        assertEquals(baseline.getProperty("opcode0502.xid"), begin.xid.toString());
        assertEquals(baseline.getProperty("opcode0502.flags"), Integer.toString(begin.flg));
        assertEquals(baseline.getProperty("opcode0502.databaseId"), Long.toString(begin.dbId));
        assertEquals(baseline.getProperty("opcode0504.xid"), commit.xid.toString());
        assertEquals(baseline.getProperty("opcode0504.flags"), Integer.toString(commit.flg));
    }

    @Test
    void matchesPinnedKtuBlockFields() {
        byte[] field = RedoOpCodeTestSupport.field(24);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, 0xF100_0001L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, 0xE200_0002L, ByteOrder.LITTLE_ENDIAN);
        field[16] = 0x0B;
        field[17] = 0x01;
        field[18] = 0x22;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 20, 0x010C, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(0x050B, 0, field);
        dispatcher.dispatch(record);

        assertEquals(baseline.getProperty("opcodeKtu.objectId"), Long.toString(record.obj));
        assertEquals(baseline.getProperty("opcodeKtu.dataObjectId"), Long.toString(record.dataObj));
        assertEquals(baseline.getProperty("opcodeKtu.opCode"), Integer.toString(record.opc));
        assertEquals(baseline.getProperty("opcodeKtu.slot"), Integer.toString(record.slt));
        assertEquals(baseline.getProperty("opcodeKtu.flags"), Integer.toString(record.flg));
    }
}
