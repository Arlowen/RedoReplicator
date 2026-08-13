/*
 * Java translation derived from OpenLogReplicator Transaction::flush in
 * src/parser/Transaction.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoRecordPair;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.RedoRowDecoder;
import io.github.arlowen.redoreplicator.redo.transaction.RedoRowGroupAssembler;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionEntry;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.TableSchema;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RedoJsonChangeAssembler {
    private final RedoRowDecoder rowDecoder;
    private final Charset databaseCharacterSet;

    public RedoJsonChangeAssembler(
            ByteOrder byteOrder, Charset databaseCharacterSet) {
        rowDecoder = new RedoRowDecoder(Objects.requireNonNull(
                byteOrder, "byteOrder"));
        this.databaseCharacterSet = Objects.requireNonNull(
                databaseCharacterSet, "databaseCharacterSet");
    }

    public List<RedoJsonChange> assemble(
            CommittedRedoTransaction transaction,
            SchemaCatalog schemaCatalog) {
        return assemble(transaction, schemaCatalog, schemaCatalog);
    }

    public List<RedoJsonChange> assemble(
            CommittedRedoTransaction transaction,
            SchemaCatalog schemaCatalog,
            SchemaCatalog previousSchemaCatalog) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(schemaCatalog, "schemaCatalog");
        Objects.requireNonNull(
                previousSchemaCatalog, "previousSchemaCatalog");
        List<RedoJsonChange> changes = new ArrayList<>();
        RedoRowGroupAssembler rowAssembler = new RedoRowGroupAssembler();
        RedoDdlAssembler ddlAssembler = new RedoDdlAssembler(
                databaseCharacterSet);
        for (RedoTransactionEntry entry : transaction.entries()) {
            appendEntry(transaction, schemaCatalog, previousSchemaCatalog,
                    rowAssembler, ddlAssembler, changes, entry);
        }
        rowAssembler.finish(transaction.xid());
        ddlAssembler.finish();
        return List.copyOf(changes);
    }

    private void appendEntry(
            CommittedRedoTransaction transaction,
            SchemaCatalog schemaCatalog,
            SchemaCatalog previousSchemaCatalog,
            RedoRowGroupAssembler rowAssembler,
            RedoDdlAssembler ddlAssembler,
            List<RedoJsonChange> changes,
            RedoTransactionEntry entry) {
        if (skipClusterOrPartitionMove(entry)) {
            return;
        }
        int operation = entry.operationCode();
        if (isRowOperation(operation)) {
            appendRow(schemaCatalog, rowAssembler, changes, entry);
            return;
        }
        if (operation == 0x05010B0B || operation == 0x05010B0C) {
            throw new RedoLogException(50057,
                    "Multi-row DML is not translated at offset "
                            + entry.first().fileOffset);
        }
        if (operation == 0x18010000) {
            if (rowAssembler.hasPendingRow()) {
                throw new RedoLogException(50057,
                        "DDL interrupts an incomplete row group at offset "
                                + entry.first().fileOffset);
            }
            ddlAssembler.accept(
                    entry.first(), transaction.commitPosition().scn(),
                    schemaCatalog, previousSchemaCatalog)
                    .ifPresent(changes::add);
            return;
        }
        if (!isNonOutputOperation(operation)) {
            throw new RedoLogException(50057,
                    "Unknown committed operation 0x"
                            + Integer.toHexString(operation)
                            + " at offset " + entry.first().fileOffset);
        }
    }

    private void appendRow(
            SchemaCatalog schemaCatalog,
            RedoRowGroupAssembler rowAssembler,
            List<RedoJsonChange> changes,
            RedoTransactionEntry entry) {
        RedoLogRecord redo = entry.second().orElseThrow();
        Optional<List<RedoRecordPair>> complete = rowAssembler.accept(
                entry.first(), redo);
        if (complete.isEmpty()) {
            return;
        }
        List<RedoRecordPair> group = complete.orElseThrow();
        TableSchema table = resolveTable(group, schemaCatalog);
        if ("SYS".equals(table.owner())) {
            throw new DataException(50071,
                    "System dictionary redo for "
                            + table.qualifiedName()
                            + " must use the system transaction pipeline");
        }
        changes.add(new RedoJsonDmlChange(
                rowDecoder.decode(table, group)));
    }

    private static TableSchema resolveTable(
            List<RedoRecordPair> group, SchemaCatalog schemaCatalog) {
        RedoRecordPair first = group.get(0);
        Optional<TableSchema> byObject = schemaCatalog.findByObjectId(
                first.undo().obj);
        if (byObject.isEmpty()) {
            byObject = schemaCatalog.findByObjectId(first.redo().obj);
        }
        if (byObject.isEmpty()) {
            byObject = schemaCatalog.findByDataObjectId(
                    first.redo().dataObj);
        }
        if (byObject.isEmpty()) {
            byObject = schemaCatalog.findByDataObjectId(
                    first.undo().dataObj);
        }
        if (byObject.isEmpty()) {
            throw new DataException(50071,
                    "No proven table schema for redo object "
                            + first.undo().obj + ", data object "
                            + first.redo().dataObj + " at offset "
                            + first.undo().fileOffset);
        }
        TableSchema table = byObject.orElseThrow();
        for (RedoRecordPair pair : group) {
            Optional<TableSchema> current = schemaCatalog.findByObjectId(
                    pair.undo().obj);
            if (current.isPresent() && !current.get().equals(table)) {
                throw new DataException(50071,
                        "Redo row group resolves to multiple table schemas");
            }
        }
        return table;
    }

    private static boolean skipClusterOrPartitionMove(
            RedoTransactionEntry entry) {
        RedoLogRecord first = entry.first();
        if ((first.fb & RedoLogRecord.FB_K) != 0
                || (first.suppLogFb & RedoLogRecord.FB_K) != 0) {
            return true;
        }
        if (entry.second().isEmpty()) {
            return false;
        }
        RedoLogRecord second = entry.second().orElseThrow();
        return (second.fb & RedoLogRecord.FB_K) != 0
                || (second.suppLogFb & RedoLogRecord.FB_K) != 0;
    }

    private static boolean isRowOperation(int operation) {
        return operation == 0x05010B02 || operation == 0x05010B03
                || operation == 0x05010B05 || operation == 0x05010B06
                || operation == 0x05010B08 || operation == 0x05010B10
                || operation == 0x05010B16;
    }

    private static boolean isNonOutputOperation(int operation) {
        return operation == 0x05010000
                || operation == 0x05010513
                || operation == 0x05010514
                || operation == 0x1A020000
                || operation == 0x13010000
                || operation == 0x1A060000
                || operation == 0x05011A02
                || operation == 0x05010A02
                || operation == 0x05010A08
                || operation == 0x05010A12;
    }
}
