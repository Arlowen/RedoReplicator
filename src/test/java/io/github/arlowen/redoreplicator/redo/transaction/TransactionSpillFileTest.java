/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.TransactionSpillException;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionSpillFileTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsEntryInterruptedBeforeItsFooter() throws Exception {
        Path path = temporaryDirectory.resolve("transaction-interrupted.spill");
        TransactionSpillFile spill = new TransactionSpillFile(path);
        try {
            RedoLogRecord record = new RedoLogRecord();
            record.opCode = 0x0501;
            record.xid = Xid.of(1, 2, 3);
            spill.append(RedoTransactionEntry.single(record));
            try (FileChannel damage = FileChannel.open(
                    path, StandardOpenOption.WRITE)) {
                damage.truncate(Files.size(path) - 1);
            }

            assertThrows(TransactionSpillException.class, spill::readAll);
        } finally {
            spill.delete();
        }
        assertFalse(Files.exists(path));
    }
}
