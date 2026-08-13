/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoFileHeaderParserTest {
    private final RedoFileHeaderParser parser = new RedoFileHeaderParser();

    @Test
    void parsesLittleEndianOracle19cHeader() {
        byte[] data = RedoBinaryTestSupport.fileHeader(ByteOrder.LITTLE_ENDIAN, 512, 0x131C_0000L);

        RedoFileHeader header = parser.parse(data, true);

        assertFalse(header.isEmpty());
        assertEquals(ByteOrder.LITTLE_ENDIAN, header.byteOrder());
        assertEquals(512, header.blockSize());
        assertEquals("19.28.0", header.version());
        assertEquals(77, header.sequence().value());
        assertEquals(0xF000_0001L, header.databaseId());
        assertEquals("ORCL19", header.databaseSid());
        assertEquals(9, header.activation());
        assertEquals(1_000, header.blockCount());
        assertEquals(5, header.resetlogs());
        assertEquals(2, header.thread());
        assertEquals(Scn.of(0x0000_1234_5678_9ABCL), header.firstScn());
        assertEquals(Scn.of(0x0000_1234_5678_ABCDL), header.nextScn());
    }

    @Test
    void parsesBigEndian4096Byte26aiFamilyHeader() {
        byte[] data = RedoBinaryTestSupport.fileHeader(ByteOrder.BIG_ENDIAN, 4096, 0x171A_2000L);

        RedoFileHeader header = parser.parse(data, true);

        assertEquals(ByteOrder.BIG_ENDIAN, header.byteOrder());
        assertEquals(4096, header.blockSize());
        assertEquals("23.26.32", header.version());
        assertEquals(989_619_936L, header.firstTime().value());
        assertEquals(989_619_996L, header.nextTime().value());
    }

    @Test
    void representsUninitializedOnlineHeaderAsEmpty() {
        byte[] data = RedoBinaryTestSupport.fileHeader(ByteOrder.LITTLE_ENDIAN, 1024, 0);

        RedoFileHeader header = parser.parse(data, true);

        assertTrue(header.isEmpty());
        assertEquals(1024, header.blockSize());
    }

    @Test
    void rejectsInvalidMarkersVersionsAndChecksums() {
        byte[] invalidMarker = RedoBinaryTestSupport.fileHeader(ByteOrder.LITTLE_ENDIAN, 512, 0x1300_0000L);
        invalidMarker[28] = 0;
        RedoLogException markerError = assertThrows(RedoLogException.class,
                () -> parser.parse(invalidMarker, true));
        assertEquals(40004, markerError.getErrorCode());

        byte[] unsupportedVersion = RedoBinaryTestSupport.fileHeader(
                ByteOrder.LITTLE_ENDIAN, 512, 0x1500_0000L);
        RedoLogException versionError = assertThrows(RedoLogException.class,
                () -> parser.parse(unsupportedVersion, true));
        assertEquals(40006, versionError.getErrorCode());

        byte[] badChecksum = RedoBinaryTestSupport.fileHeader(ByteOrder.LITTLE_ENDIAN, 512, 0x1300_0000L);
        badChecksum[512 + 100] ^= 1;
        RedoLogException checksumError = assertThrows(RedoLogException.class,
                () -> parser.parse(badChecksum, true));
        assertEquals(60025, checksumError.getErrorCode());
    }
}
