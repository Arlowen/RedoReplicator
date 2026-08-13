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
import io.github.arlowen.redoreplicator.redo.common.RedoFieldCursor;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoVectorParserTest {
    private final RedoVectorParser parser =
            new RedoVectorParser(ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0, 512);

    @Test
    void parsesVectorAndFieldLayoutFromAssembledRecord() {
        AssembledRedoRecord assembled = assembled(RedoBinaryTestSupport.spanningLwnRecord());

        List<RedoLogRecord> vectors = parser.parseAll(
                assembled, Seq.of(77), RedoTime.of(989_619_936L), 2);

        assertEquals(1, vectors.size());
        RedoLogRecord vector = vectors.get(0);
        assertEquals(1, vector.vectorNo);
        assertEquals(0x0502, vector.opCode);
        assertEquals(17, vector.cls);
        assertEquals(1, vector.usn);
        assertEquals(7, vector.afn);
        assertEquals(0x1234_5678L, vector.dba);
        assertEquals(Scn.of(0x0000_1234_5678_8FFFL), vector.scnRecord);
        assertEquals(9, vector.seq);
        assertEquals(3, vector.typ);
        assertTrue(vector.encryptedTablespace);
        assertEquals(4, vector.conId);
        assertEquals(0x55AA, vector.flgRecord);
        assertEquals(1, vector.fieldCnt);
        assertEquals(36, vector.fieldPos);
        assertEquals(532, vector.size);
        assertEquals(51_284, vector.fileOffset.value());
        assertEquals(2, vector.thread);

        RedoFieldCursor cursor = new RedoFieldCursor(
                new RedoByteReader(ByteOrder.LITTLE_ENDIAN), vector, 500);
        cursor.next();
        assertEquals(36, cursor.fieldPosition());
        assertEquals(496, cursor.fieldSize());
        assertEquals(0, vector.byteAt(cursor.fieldPosition()));
        assertEquals(1, vector.byteAt(cursor.fieldPosition() + 1));
    }

    @Test
    void rejectsFieldDataBeyondRecordBoundary() {
        byte[] data = RedoBinaryTestSupport.spanningLwnRecord();
        RedoBinaryTestSupport.writeUnsignedShort(data, 102, 500, ByteOrder.LITTLE_ENDIAN);

        RedoLogException error = assertThrows(RedoLogException.class,
                () -> parser.parseAll(assembled(data), Seq.of(77), RedoTime.zero(), 2));

        assertEquals(50046, error.getErrorCode());
    }

    @Test
    void skipsRecordWithoutValidFlag() {
        byte[] data = RedoBinaryTestSupport.spanningLwnRecord();
        data[4] = 0x04;

        List<RedoLogRecord> vectors = parser.parseAll(
                assembled(data), Seq.of(77), RedoTime.zero(), 2);

        assertTrue(vectors.isEmpty());
    }

    @Test
    void acceptsAZeroFieldVectorEndingAtTheRecordBoundary() {
        byte[] data = new byte[60];
        RedoBinaryTestSupport.writeUnsignedInt(data, 0, data.length, ByteOrder.LITTLE_ENDIAN);
        data[4] = 0x01;
        data[24] = 0x05;
        data[25] = 0x04;
        RedoBinaryTestSupport.writeUnsignedShort(data, 56, 2, ByteOrder.LITTLE_ENDIAN);

        List<RedoLogRecord> vectors = parser.parseAll(
                assembled(data), Seq.of(77), RedoTime.zero(), 2);

        assertEquals(1, vectors.size());
        assertEquals(36, vectors.get(0).size);
        assertEquals(0, vectors.get(0).fieldCnt);
        assertEquals(36, vectors.get(0).fieldPos);
    }

    private static AssembledRedoRecord assembled(byte[] data) {
        LwnMember member = new LwnMember(16, Scn.of(0x0000_1234_5678_9000L),
                data.length, 100, 3);
        return new AssembledRedoRecord(member, data);
    }
}
