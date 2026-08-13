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

class LobAndDdlOpCodeParityTest {
    private static final String FIXTURE =
            "/fixtures/redo-header/openlogreplicator-6bc92bc1.properties";
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);
    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws IOException {
        baseline = new Properties();
        try (InputStream input = LobAndDdlOpCodeParityTest.class
                .getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedDirectLoaderFields() {
        byte[] field = RedoOpCodeTestSupport.field(40);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, 0xF100_0001L, ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < 10; index++) {
            field[4 + index] = (byte) (index + 1);
        }
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 24, 77, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x1301, 0, field, RedoOpCodeTestSupport.field(0));

        dispatcher.dispatch(record);

        assertProperty("opcode1301.dataObjectId", Long.toString(record.dataObj));
        assertProperty("opcode1301.lobId", record.lobId.lower());
        assertProperty("opcode1301.pageNumber", Long.toString(record.lobPageNo));
        assertProperty("opcode1301.payloadSize", Integer.toString(record.lobDataSize));
    }

    @Test
    void matchesPinnedDdlAndKdliFields() {
        byte[] ddl = RedoOpCodeTestSupport.field(18);
        RedoBinaryTestSupport.writeUnsignedShort(
                ddl, 4, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                ddl, 6, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                ddl, 8, 0x9ABC_DEF0L, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                ddl, 16, 3, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord ddlRecord = RedoOpCodeTestSupport.record(
                0x1801, 0,
                ddl,
                empty(), empty(), empty(), empty(), empty(),
                empty(), empty(), empty(), empty(), empty(),
                unsignedIntField(0xE200_0002L));
        dispatcher.dispatch(ddlRecord);
        assertProperty("opcode1801.xid", ddlRecord.xid.toString());
        assertProperty("opcode1801.objectId", Long.toString(ddlRecord.obj));

        byte[] common = RedoOpCodeTestSupport.field(12);
        common[0] = 0x06;
        RedoBinaryTestSupport.writeUnsignedInt(
                common, 8, 0xF500_0005L, ByteOrder.LITTLE_ENDIAN);
        byte[] fill = RedoOpCodeTestSupport.field(11);
        fill[0] = 0x06;
        RedoBinaryTestSupport.writeUnsignedShort(
                fill, 2, 15, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                fill, 6, 3, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord kdliRecord = RedoOpCodeTestSupport.record(
                0x1A02, 0, ktbNoOperation(), common, fill);
        dispatcher.dispatch(kdliRecord);
        assertProperty("opcodeKdli.operation", Integer.toString(kdliRecord.opc));
        assertProperty("opcodeKdli.blockDba", Long.toString(kdliRecord.dba));
        assertProperty("opcodeKdli.fillOffset", Integer.toString(kdliRecord.lobOffset));
        assertProperty("opcodeKdli.fillSize", Integer.toString(kdliRecord.lobDataSize));
    }

    private void assertProperty(String key, String actual) {
        assertEquals(baseline.getProperty(key), actual);
    }

    private static byte[] unsignedIntField(long value) {
        byte[] field = RedoOpCodeTestSupport.field(4);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, value, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] ktbNoOperation() {
        byte[] field = RedoOpCodeTestSupport.field(8);
        field[0] = 0x06;
        return field;
    }

    private static byte[] empty() {
        return RedoOpCodeTestSupport.field(0);
    }
}
