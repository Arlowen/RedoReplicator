/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ValueTypeParityTest {
    private static final String FIXTURE =
            "/fixtures/value-types/openlogreplicator-6bc92bc1.properties";
    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws IOException {
        baseline = new Properties();
        try (InputStream input = ValueTypeParityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedScnSequenceAndOffsetOutput() {
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
    }

    @Test
    void matchesPinnedIdentifierAndUbaOutput() {
        Xid xid = Xid.of(2, 0x12, 0x4162);
        assertEquals(baseline.getProperty("xid.classic"), xid.toString());

        RowId rowId = RowId.of(100, (3L << 22) | 200, 5);
        assertEquals(baseline.getProperty("rowid.extended"), rowId.toString());
        assertEquals(baseline.getProperty("rowid.hex"), rowId.toHexString());

        long undoBlockAddress = 0x00AB_CDEF_1234_5678L;
        assertEquals(baseline.getProperty("uba.formatted"),
                RedoConstants.formatUndoBlockAddress(undoBlockAddress));
    }

    @Test
    void matchesPinnedExtendedValueAndRecordOutput() {
        IntX maximum = IntX.parseDecimal("340282366920938463463374607431768211455");
        assertEquals(baseline.getProperty("intx.maximum"), maximum.toString());
        assertEquals(baseline.getProperty("intx.wrapped"), maximum.plus(IntX.of(1)).toString());

        byte[] lobIdData = {0x01, 0x0A, 0x00, (byte) 0xFF, 0x10, 0x20, 0x03, 0x40, 0x05, 0x60};
        LobId lobId = LobId.of(lobIdData);
        assertEquals(baseline.getProperty("lobid.lower"), lobId.lower());
        assertEquals(baseline.getProperty("lobid.upper"), lobId.upper());
        assertEquals(baseline.getProperty("lobid.narrow"), lobId.narrow());

        RedoTime time = RedoTime.of(989_619_936L);
        assertEquals(baseline.getProperty("time.raw"), Long.toString(time.value()));
        assertEquals(baseline.getProperty("time.formatted"), time.toString());
        assertEquals(baseline.getProperty("time.epochPlus8"),
                Long.toString(time.toEpochSeconds(8 * 60 * 60)));

        assertEquals(baseline.getProperty("record.formatted"), parityRecord().toString());
    }

    private RedoLogRecord parityRecord() {
        RedoLogRecord record = new RedoLogRecord();
        record.scnRecord = Scn.of(0x1234_5678_9ABC_DEF0L);
        record.scn = Scn.of(0x0000_5678_9ABC_DEF0L);
        record.subScn = 7;
        record.xid = Xid.of(2, 0x12, 0x4162);
        record.opCode = 0x0B02;
        record.cls = 1;
        record.rbl = 2;
        record.seq = 3;
        record.typ = 4;
        record.dbId = 0xFFFF_FFFFL;
        record.conId = -1;
        record.flgRecord = 5;
        record.recordObj = 6;
        record.recordDataObj = 7;
        record.nRow = 8;
        record.afn = 9;
        record.size = 100;
        record.dba = 0xABCD_EF01L;
        record.bdba = 0x1020_3040L;
        record.obj = 11;
        record.dataObj = 12;
        record.usn = -2;
        record.slt = 13;
        record.flg = 14;
        record.opc = 0x0B01;
        record.op = 15;
        record.cc = 16;
        record.slot = 17;
        record.flags = 0xA0;
        record.fb = 0x0C;
        return record;
    }
}
