/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ValueTypeParityTest {
    private static final String FIXTURE =
            "/fixtures/value-types/openlogreplicator-6bc92bc1.properties";

    @Test
    void matchesPinnedOpenLogReplicatorOutput() throws IOException {
        Properties baseline = loadBaseline();

        Scn scn = Scn.of(0x1234_5678_9ABC_DEF0L);
        assertEquals(baseline.getProperty("scn.to48"), scn.to48());
        assertEquals(baseline.getProperty("scn.to64"), scn.to64());
        assertEquals(baseline.getProperty("scn.to64d"), scn.to64D());
        assertEquals(baseline.getProperty("scn.hex12"), scn.toHex12());
        assertEquals(baseline.getProperty("scn.hex16"), scn.toHex16());
        assertEquals(baseline.getProperty("scn.decimal"), scn.toDecimalString());
        assertEquals(baseline.getProperty("scn.none"), Scn.none().toDecimalString());

        Seq sequence = Seq.of(0xFFFF_FFFFL);
        assertEquals(baseline.getProperty("seq.decimal"), sequence.toString());
        assertEquals(baseline.getProperty("seq.hex"), sequence.toHex(8));

        FileOffset fileOffset = FileOffset.fromBlock(31, 512);
        assertEquals(baseline.getProperty("offset.decimal"), fileOffset.toString());
        assertEquals(baseline.getProperty("offset.hex"), fileOffset.toHex(8));
        assertEquals(baseline.getProperty("offset.block"), Long.toString(fileOffset.block(512)));
        FileOffset maximumOffset = FileOffset.of(-1);
        assertEquals(baseline.getProperty("offset.maximum"), maximumOffset.toString());
        assertEquals(baseline.getProperty("offset.maximumHex"), maximumOffset.toHex(16));
        assertEquals(baseline.getProperty("offset.wrapped"), maximumOffset.plus(1).toString());

        Xid xid = Xid.of(2, 0x12, 0x4162);
        assertEquals(baseline.getProperty("xid.classic"), xid.toString());

        RowId rowId = RowId.of(100, (3L << 22) | 200, 5);
        assertEquals(baseline.getProperty("rowid.extended"), rowId.toString());
        assertEquals(baseline.getProperty("rowid.hex"), rowId.toHexString());

        long undoBlockAddress = 0x00AB_CDEF_1234_5678L;
        assertEquals(baseline.getProperty("uba.formatted"),
                RedoConstants.formatUndoBlockAddress(undoBlockAddress));
    }

    private Properties loadBaseline() throws IOException {
        Properties baseline = new Properties();
        try (InputStream input = ValueTypeParityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }
        return baseline;
    }
}
