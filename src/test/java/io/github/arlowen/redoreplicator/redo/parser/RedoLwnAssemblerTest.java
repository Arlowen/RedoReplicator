/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoLwnAssemblerTest {
    private final RedoLwnAssembler assembler =
            new RedoLwnAssembler(ByteOrder.LITTLE_ENDIAN, 512);

    @Test
    void assemblesOneRecordAcrossTwoBlocks() {
        Optional<AssembledLwn> result = assembler.tryAssemble(
                RedoBinaryTestSupport.spanningLwnBlocks(), 100,
                Scn.of(0x0000_1234_5678_0000L), Scn.of(0x0000_1234_5679_0000L));

        assertTrue(result.isPresent());
        AssembledLwn lwn = result.orElseThrow();
        assertEquals(100, lwn.startBlock());
        assertEquals(102, lwn.endBlock());
        assertEquals(2, lwn.consumedBlocks());
        assertEquals(Scn.of(0x0000_1234_5678_9ABCL), lwn.scn());
        assertEquals(989_619_936L, lwn.timestamp().value());
        assertEquals(1, lwn.partHeaders().size());
        assertEquals(1, lwn.records().size());

        AssembledRedoRecord record = lwn.records().get(0);
        assertEquals(100, record.member().block());
        assertEquals(16, record.member().pageOffset());
        assertEquals(600, record.member().size());
        assertEquals(Scn.of(0x0000_1234_5678_9000L), record.member().scn());
        assertEquals(3, record.member().subScn());
        assertArrayEquals(RedoBinaryTestSupport.spanningLwnRecord(), record.data());
    }

    @Test
    void waitsUntilEveryBlockOfTheLwnIsAvailable() {
        List<byte[]> blocks = RedoBinaryTestSupport.spanningLwnBlocks();

        Optional<AssembledLwn> result = assembler.tryAssemble(
                List.of(blocks.get(0)), 100, Scn.zero(), Scn.none());

        assertFalse(result.isPresent());
    }

    @Test
    void validatesLwnScnAndPartMaximum() {
        RedoLogException scnError = assertThrows(RedoLogException.class,
                () -> assembler.tryAssemble(RedoBinaryTestSupport.spanningLwnBlocks(), 100,
                        Scn.of(0x0000_1234_5678_9ABDL), Scn.none()));
        assertEquals(50049, scnError.getErrorCode());

        List<byte[]> mismatchedParts = List.of(
                lwnPart(1, 2, 200, 1),
                lwnPart(2, 3, 100, 2));
        RedoLogException maximumError = assertThrows(RedoLogException.class,
                () -> assembler.tryAssemble(mismatchedParts, 100, Scn.zero(), Scn.none()));
        assertEquals(50050, maximumError.getErrorCode());
    }

    @Test
    void ordersRecordsFromMultiplePartsByScn() {
        List<byte[]> blocks = List.of(
                lwnPart(1, 2, 200, 2),
                lwnPart(2, 2, 100, 1));

        AssembledLwn result = assembler.tryAssemble(blocks, 50, Scn.zero(), Scn.none())
                .orElseThrow();

        assertEquals(2, result.partHeaders().size());
        assertEquals(2, result.records().size());
        assertEquals(Scn.of(100), result.records().get(0).member().scn());
        assertEquals(Scn.of(200), result.records().get(1).member().scn());
    }

    private static byte[] lwnPart(int number, int maximum, long memberScn, int subScn) {
        byte[] block = new byte[512];
        int offset = 16;
        RedoBinaryTestSupport.writeUnsignedInt(block, offset, 68, ByteOrder.LITTLE_ENDIAN);
        block[offset + 4] = 0x05;
        RedoBinaryTestSupport.writeUnsignedShort(block, offset + 6,
                (int) (memberScn >>> 32), ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(block, offset + 8,
                memberScn, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(block, offset + 12,
                subScn, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(block, offset + 24,
                number, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(block, offset + 26,
                maximum, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(block, offset + 28,
                1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(block, offset + 32,
                68, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeScn(block, offset + 40,
                150, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(block, offset + 64,
                989_619_936L, ByteOrder.LITTLE_ENDIAN);
        return block;
    }
}
