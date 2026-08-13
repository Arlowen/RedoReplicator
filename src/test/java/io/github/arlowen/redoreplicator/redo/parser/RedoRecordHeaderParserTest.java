/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoRecordHeaderParserTest {
    private final RedoRecordHeaderParser parser =
            new RedoRecordHeaderParser(new RedoByteReader(ByteOrder.LITTLE_ENDIAN));

    @Test
    void parsesExtendedLwnHeaderAtBlockOffset16() {
        byte[] block = RedoBinaryTestSupport.extendedRecord(600);

        RedoLwnHeader header = parser.parseLwn(block, 16, 600);

        assertTrue(header.recordHeader().isValid());
        assertEquals(68, header.recordHeader().headerSize());
        assertEquals(42, header.recordHeader().containerUid());
        assertEquals(2, header.number());
        assertEquals(3, header.maximum());
        assertEquals(4, header.blockCount());
        assertEquals(600, header.length());
        assertEquals(Scn.of(0x0000_1234_5678_9ABCL), header.scn());
        assertEquals(989_619_936L, header.timestamp().value());
    }

    @Test
    void parsesNormal24ByteRecordHeader() {
        byte[] data = new byte[24];
        RedoBinaryTestSupport.writeUnsignedInt(data, 0, 24, ByteOrder.LITTLE_ENDIAN);
        data[4] = 0x01;
        RedoBinaryTestSupport.writeUnsignedInt(data, 16, 7, ByteOrder.LITTLE_ENDIAN);

        RedoRecordHeader header = parser.parse(data, 0, 24);

        assertEquals(24, header.headerSize());
        assertEquals(7, header.containerUid());
    }

    @Test
    void rejectsSizeMismatchAndMissingLwnMarker() {
        byte[] data = RedoBinaryTestSupport.extendedRecord(100);
        RedoLogException sizeError = assertThrows(RedoLogException.class,
                () -> parser.parse(data, 16, 99));
        assertEquals(50046, sizeError.getErrorCode());

        data[20] = 0x01;
        RedoLogException lwnError = assertThrows(RedoLogException.class,
                () -> parser.parseLwn(data, 16, 100));
        assertEquals(50051, lwnError.getErrorCode());
    }

    @Test
    void acceptsShortRecordWhenUpstreamValidityFlagSaysToSkipIt() {
        byte[] data = new byte[8];
        RedoBinaryTestSupport.writeUnsignedInt(data, 0, data.length, ByteOrder.LITTLE_ENDIAN);

        RedoRecordHeader header = parser.parse(data, 0, data.length);

        assertEquals(5, header.headerSize());
        assertEquals(0, header.containerUid());
        assertFalse(header.isValid());
    }

}
