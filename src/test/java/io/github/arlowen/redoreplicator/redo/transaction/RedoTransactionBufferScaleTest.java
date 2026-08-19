/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.state.RedoPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(
        named = "redoreplicator.test.scale", matches = "true")
class RedoTransactionBufferScaleTest {
    private static final int ROW_COUNT = 100_000;
    private static final Xid XID = Xid.of(1, 2, 3);

    @TempDir
    Path temporaryDirectory;

    @Test
    void spillsAndCommitsOneHundredThousandRowsInOrder() throws Exception {
        try (RedoTransactionBuffer buffer = new RedoTransactionBuffer(
                temporaryDirectory, 1)) {
            buffer.begin(begin(), position(100, 512));
            for (int row = 0; row < ROW_COUNT; row++) {
                buffer.appendPair(undo(), redo(row));
            }

            assertEquals(1, buffer.spilledTransactionCount());
            assertEquals(0, buffer.bufferedMemoryBytes());
            CommittedRedoTransaction committed = buffer.commit(
                    commit()).orElseThrow();

            assertEquals(ROW_COUNT, committed.entries().size());
            assertEquals(0, committed.entries().get(0)
                    .second().orElseThrow().slot);
            assertEquals(ROW_COUNT / 2, committed.entries()
                    .get(ROW_COUNT / 2).second().orElseThrow().slot);
            assertEquals(ROW_COUNT - 1, committed.entries()
                    .get(ROW_COUNT - 1).second().orElseThrow().slot);
            try (Stream<Path> files = Files.list(temporaryDirectory)) {
                assertEquals(0, files.count());
            }
        }
    }

    private static RedoLogRecord begin() {
        RedoLogRecord record = record(0x0502);
        record.sequence = Seq.of(10);
        record.timestamp = RedoTime.of(1);
        return record;
    }

    private static RedoLogRecord undo() {
        RedoLogRecord record = record(0x0501);
        record.obj = 100;
        record.dataObj = 101;
        record.bdba = 200;
        record.suppLogBdba = 200;
        record.suppLogSlot = 1;
        record.suppLogFb = RedoLogRecord.FB_L;
        return record;
    }

    private static RedoLogRecord redo(int slot) {
        RedoLogRecord record = record(0x0B02);
        record.obj = 100;
        record.dataObj = 101;
        record.bdba = 200;
        record.slot = slot;
        return record;
    }

    private static RedoLogRecord commit() {
        RedoLogRecord record = record(0x0504);
        record.scn = Scn.of(200);
        record.sequence = Seq.of(10);
        record.timestamp = RedoTime.of(2);
        record.fileOffset = FileOffset.of(4096);
        return record;
    }

    private static RedoLogRecord record(int opCode) {
        RedoLogRecord record = new RedoLogRecord();
        record.opCode = opCode;
        record.xid = XID;
        record.scn = Scn.of(110);
        record.thread = 1;
        record.conId = 3;
        return record;
    }

    private static RedoPosition position(long scn, long offset) {
        return new RedoPosition(
                Scn.of(scn), 1, Seq.of(10), FileOffset.of(offset));
    }
}
