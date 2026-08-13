/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoBlockHeaderParserTest {
    @Test
    void validatesBlockIdentitySequenceAndChecksum() {
        byte[] data = RedoBinaryTestSupport.fileHeader(ByteOrder.LITTLE_ENDIAN, 512, 0x1300_0000L);
        RedoBlockHeaderParser parser = new RedoBlockHeaderParser(ByteOrder.LITTLE_ENDIAN, 512);

        RedoBlockHeader header = parser.parse(data, 512, 1, Seq.of(77), true);

        assertEquals(1, header.blockNumber());
        assertEquals(Seq.of(77), header.sequence());

        RedoLogException blockError = assertThrows(RedoLogException.class,
                () -> parser.parse(data, 512, 2, Seq.of(77), true));
        assertEquals(40002, blockError.getErrorCode());

        RedoLogException sequenceError = assertThrows(RedoLogException.class,
                () -> parser.parse(data, 512, 1, Seq.of(78), true));
        assertEquals(60024, sequenceError.getErrorCode());
    }

    @Test
    void detectsEmptyBlock() {
        byte[] data = new byte[512];
        RedoBlockHeaderParser parser = new RedoBlockHeaderParser(ByteOrder.LITTLE_ENDIAN, 512);

        assertTrue(parser.parse(data, 0, 1, Seq.zero(), true).isEmpty());
    }
}
