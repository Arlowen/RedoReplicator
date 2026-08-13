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

class UndoOpCodeParityTest {
    private static final String FIXTURE =
            "/fixtures/redo-header/openlogreplicator-6bc92bc1.properties";
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);
    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws IOException {
        baseline = new Properties();
        try (InputStream input = UndoOpCodeParityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedUndoAndSupplementalFields() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0501, 0, undoBlock(), ktuBlock(), ktbNoOperation(),
                deleteRowPiece(), supplementalHeader());

        dispatcher.dispatch(record);

        assertProperty("opcode0501.xid", record.xid.toString());
        assertProperty("opcode0501.objectId", Long.toString(record.obj));
        assertProperty("opcode0501.dataObjectId", Long.toString(record.dataObj));
        assertProperty("opcode0501.operation", Integer.toString(record.opc));
        assertProperty("opcode0501.supplementalFb", Integer.toString(record.suppLogFb));
        assertProperty("opcode0501.supplementalCc", Integer.toString(record.suppLogCC));
        assertProperty("opcode0501.supplementalBefore",
                Integer.toString(record.suppLogBefore));
        assertProperty("opcode0501.supplementalAfter",
                Integer.toString(record.suppLogAfter));
        assertProperty("opcode0501.supplementalBdba",
                Long.toString(record.suppLogBdba));
        assertProperty("opcode0501.supplementalSlot",
                Integer.toString(record.suppLogSlot));
    }

    private void assertProperty(String key, String actual) {
        assertEquals(baseline.getProperty(key), actual);
    }

    private static byte[] undoBlock() {
        byte[] field = RedoOpCodeTestSupport.field(20);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 8, 0x2345, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 10, 0x6789, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 12, 0xABCD_EF01L, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] ktuBlock() {
        byte[] field = RedoOpCodeTestSupport.field(24);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, 0xF600_0006L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, 0xE700_0007L, ByteOrder.LITTLE_ENDIAN);
        field[16] = 0x0B;
        field[17] = 0x01;
        return field;
    }

    private static byte[] ktbNoOperation() {
        byte[] field = RedoOpCodeTestSupport.field(8);
        field[0] = 0x06;
        return field;
    }

    private static byte[] deleteRowPiece() {
        byte[] field = RedoOpCodeTestSupport.field(20);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, 0xC800_0008L, ByteOrder.LITTLE_ENDIAN);
        field[10] = RedoLogRecord.OP_DRP;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 16, 0x4455, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] supplementalHeader() {
        byte[] field = RedoOpCodeTestSupport.field(26);
        field[1] = 0x66;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 2, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 6, 3, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 8, 4, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 20, 0xD900_0009L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 24, 0x5566, ByteOrder.LITTLE_ENDIAN);
        return field;
    }
}
