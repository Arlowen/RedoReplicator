/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IndexOpCodeParityTest {
    private static final String FIXTURE =
            "/fixtures/redo-header/openlogreplicator-6bc92bc1.properties";

    @Test
    void matchesPinnedKtbTransactionIdentity() throws IOException {
        Properties baseline = new Properties();
        try (InputStream input = IndexOpCodeParityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }

        byte[] ktbRedo = RedoOpCodeTestSupport.field(24);
        ktbRedo[0] = 0x01;
        ktbRedo[1] = 0x08;
        RedoBinaryTestSupport.writeUnsignedShort(
                ktbRedo, 8, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                ktbRedo, 10, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                ktbRedo, 12, 0x9ABC_DEF0L, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(0x0A12, 0, ktbRedo);
        RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
                ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);

        dispatcher.dispatch(record);

        assertEquals(baseline.getProperty("opcodeKtb.xid"), record.xid.toString());
    }
}
