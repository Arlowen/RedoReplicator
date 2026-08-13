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

class RowOpCodeParityTest {
    private static final String FIXTURE =
            "/fixtures/redo-header/openlogreplicator-6bc92bc1.properties";
    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws IOException {
        baseline = new Properties();
        try (InputStream input = RowOpCodeParityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedKdoInsertFields() {
        byte[] ktb = RedoOpCodeTestSupport.field(8);
        ktb[0] = 0x06;
        byte[] kdo = RedoOpCodeTestSupport.field(48);
        RedoBinaryTestSupport.writeUnsignedInt(
                kdo, 0, 0xF100_0001L, ByteOrder.LITTLE_ENDIAN);
        kdo[10] = 0x02;
        kdo[11] = 0x41;
        kdo[16] = 0x0C;
        kdo[18] = 3;
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 40, 9, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                kdo, 42, 0x1234, ByteOrder.LITTLE_ENDIAN);
        kdo[45] = 0x02;
        RedoLogRecord record = RedoOpCodeTestSupport.record(0x0B02, 0, ktb, kdo);
        RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
                ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);

        dispatcher.dispatch(record);

        assertProperty("blockDba", Long.toString(record.bdba));
        assertProperty("operation", Integer.toString(record.op));
        assertProperty("flags", Integer.toString(record.flags));
        assertProperty("rowFlags", Integer.toString(record.fb));
        assertProperty("columnCount", Integer.toString(record.cc));
        assertProperty("columnDataCount", Integer.toString(record.ccData));
        assertProperty("sizeDelta", Integer.toString(record.sizeDelt));
        assertProperty("slot", Integer.toString(record.slot));
        int kdoPosition = record.fieldPos + ktb.length;
        assertProperty("nullsOffset", Integer.toString(record.nullsDelta - kdoPosition));
    }

    private void assertProperty(String suffix, String actual) {
        assertEquals(baseline.getProperty("opcodeKdo." + suffix), actual);
    }
}
