/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.parser.RedoLwnHeader;
import io.github.arlowen.redoreplicator.redo.parser.RedoRecordHeaderParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RedoHeaderParityTest {
    private static final String FIXTURE =
            "/fixtures/redo-header/openlogreplicator-6bc92bc1.properties";
    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws IOException {
        baseline = new Properties();
        try (InputStream input = RedoHeaderParityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedFileAndBlockHeaderLayout() {
        byte[] data = RedoBinaryTestSupport.fileHeader(ByteOrder.LITTLE_ENDIAN, 512, 0x131C_0000L);
        RedoFileHeader fileHeader = new RedoFileHeaderParser().parse(data, true);
        RedoBlockHeaderParser blockParser = new RedoBlockHeaderParser(ByteOrder.LITTLE_ENDIAN, 512);
        RedoBlockHeader blockHeader = blockParser.parse(data, 512, 1, Seq.of(77), true);

        assertEquals(baseline.getProperty("file.byteOrder"), fileHeader.byteOrder().toString());
        assertEquals(baseline.getProperty("file.blockSize"), Integer.toString(fileHeader.blockSize()));
        assertEquals(baseline.getProperty("file.version"), Long.toString(fileHeader.compatibleVersion()));
        assertEquals(baseline.getProperty("file.sequence"), fileHeader.sequence().toString());
        assertEquals(baseline.getProperty("file.databaseId"), Long.toString(fileHeader.databaseId()));
        assertEquals(baseline.getProperty("file.activation"), Long.toString(fileHeader.activation()));
        assertEquals(baseline.getProperty("file.blockCount"), Long.toString(fileHeader.blockCount()));
        assertEquals(baseline.getProperty("file.resetlogs"), Long.toString(fileHeader.resetlogs()));
        assertEquals(baseline.getProperty("file.thread"), Integer.toString(fileHeader.thread()));
        assertEquals(baseline.getProperty("file.firstScn"), fileHeader.firstScn().toString());
        assertEquals(baseline.getProperty("file.firstTime"), Long.toString(fileHeader.firstTime().value()));
        assertEquals(baseline.getProperty("file.nextScn"), fileHeader.nextScn().toString());
        assertEquals(baseline.getProperty("file.nextTime"), Long.toString(fileHeader.nextTime().value()));
        assertEquals(baseline.getProperty("block.type"), Integer.toString(blockHeader.type()));
        assertEquals(baseline.getProperty("block.number"), Long.toString(blockHeader.blockNumber()));
        assertEquals(baseline.getProperty("block.checksum"), Integer.toString(blockHeader.checksum()));
        assertEquals(baseline.getProperty("block.calculatedChecksum"),
                Integer.toString(blockParser.calculateChecksum(data, 512)));
    }

    @Test
    void matchesPinnedRecordAndLwnHeaderLayout() {
        byte[] data = RedoBinaryTestSupport.extendedRecord(600);
        RedoRecordHeaderParser parser =
                new RedoRecordHeaderParser(new RedoByteReader(ByteOrder.LITTLE_ENDIAN));
        RedoLwnHeader header = parser.parseLwn(data, 16, 600);

        assertEquals(baseline.getProperty("record.size"), Long.toString(header.recordHeader().recordSize()));
        assertEquals(baseline.getProperty("record.validity"),
                Integer.toString(header.recordHeader().validity()));
        assertEquals(baseline.getProperty("record.containerUid"),
                Long.toString(header.recordHeader().containerUid()));
        assertEquals(baseline.getProperty("lwn.number"), Integer.toString(header.number()));
        assertEquals(baseline.getProperty("lwn.maximum"), Integer.toString(header.maximum()));
        assertEquals(baseline.getProperty("lwn.blockCount"), Long.toString(header.blockCount()));
        assertEquals(baseline.getProperty("lwn.length"), Long.toString(header.length()));
        assertEquals(baseline.getProperty("lwn.scn"), header.scn().toString());
        assertEquals(baseline.getProperty("lwn.timestamp"), Long.toString(header.timestamp().value()));
    }

    @Test
    void matchesPinnedBigEndianFileAndChecksumLayout() {
        byte[] data = RedoBinaryTestSupport.fileHeader(ByteOrder.BIG_ENDIAN, 512, 0x171A_2000L);
        RedoFileHeader fileHeader = new RedoFileHeaderParser().parse(data, true);
        RedoBlockHeaderParser blockParser = new RedoBlockHeaderParser(ByteOrder.BIG_ENDIAN, 512);
        RedoBlockHeader blockHeader = blockParser.parse(data, 512, 1, Seq.of(77), true);

        assertEquals(baseline.getProperty("big.file.byteOrder"), fileHeader.byteOrder().toString());
        assertEquals(baseline.getProperty("big.file.blockSize"), Integer.toString(fileHeader.blockSize()));
        assertEquals(baseline.getProperty("big.file.version"), Long.toString(fileHeader.compatibleVersion()));
        assertEquals(baseline.getProperty("big.file.sequence"), fileHeader.sequence().toString());
        assertEquals(baseline.getProperty("big.file.firstScn"), fileHeader.firstScn().toString());
        assertEquals(baseline.getProperty("big.block.checksum"), Integer.toString(blockHeader.checksum()));
        assertEquals(baseline.getProperty("big.block.calculatedChecksum"),
                Integer.toString(blockParser.calculateChecksum(data, 512)));
    }
}
