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

import io.github.arlowen.redoreplicator.charset.CharacterSet;
import io.github.arlowen.redoreplicator.charset.CharacterSetJdk;
import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoRecordPair;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.redo.lob.RedoLobContext;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.DecodedRedoRow;
import io.github.arlowen.redoreplicator.redo.transaction.RedoColumnValue;
import io.github.arlowen.redoreplicator.redo.transaction.RedoMultiRowDecoder;
import io.github.arlowen.redoreplicator.redo.transaction.RedoRowDecoder;
import io.github.arlowen.redoreplicator.redo.transaction.RedoRowGroupAssembler;
import io.github.arlowen.redoreplicator.redo.transaction.RedoRowOperation;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionEntry;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryRedoBridge;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryRedoChange;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryChange;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryOperation;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryTable;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryValue;
import io.github.arlowen.redoreplicator.schema.SystemTransactionCommit;
import io.github.arlowen.redoreplicator.schema.SystemTransactionManager;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;
import io.github.arlowen.redoreplicator.source.OracleContainer;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public final class RedoJsonChangeAssembler {
    private final RedoRowDecoder rowDecoder;
    private final RedoMultiRowDecoder multiRowDecoder;
    private final ByteOrder byteOrder;
    private final CharacterSet databaseCharacterSet;
    private final SystemDictionaryRedoBridge systemDictionaryBridge;
    private final TableSchemaJsonCodec tableSchemaJsonCodec;
    private final Predicate<String> outputTableFilter;
    private final IntFunction<String> containerResolver;
    private final RedoLobValueResolver lobValueResolver;

    public RedoJsonChangeAssembler(
            ByteOrder byteOrder, Charset databaseCharacterSet) {
        this(byteOrder, databaseCharacterSet, ignored -> true);
    }

    public RedoJsonChangeAssembler(
            ByteOrder byteOrder,
            Charset databaseCharacterSet,
            Predicate<String> outputTableFilter) {
        this(byteOrder,
                new CharacterSetJdk(0, databaseCharacterSet.name(),
                        databaseCharacterSet),
                outputTableFilter);
    }

    public RedoJsonChangeAssembler(
            ByteOrder byteOrder,
            Charset databaseCharacterSet,
            Predicate<String> outputTableFilter,
            IntFunction<String> containerResolver) {
        this(byteOrder,
                new CharacterSetJdk(0, databaseCharacterSet.name(),
                        databaseCharacterSet),
                outputTableFilter, containerResolver);
    }

    public RedoJsonChangeAssembler(
            ByteOrder byteOrder,
            CharacterSet databaseCharacterSet,
            Predicate<String> outputTableFilter) {
        this(byteOrder, databaseCharacterSet, outputTableFilter,
                ignored -> null);
    }

    public RedoJsonChangeAssembler(
            ByteOrder byteOrder,
            CharacterSet databaseCharacterSet,
            Predicate<String> outputTableFilter,
            IntFunction<String> containerResolver) {
        this.byteOrder = Objects.requireNonNull(byteOrder, "byteOrder");
        rowDecoder = new RedoRowDecoder(byteOrder);
        multiRowDecoder = new RedoMultiRowDecoder(byteOrder);
        systemDictionaryBridge = new SystemDictionaryRedoBridge(byteOrder);
        tableSchemaJsonCodec = new TableSchemaJsonCodec();
        lobValueResolver = new RedoLobValueResolver();
        this.databaseCharacterSet = Objects.requireNonNull(
                databaseCharacterSet, "databaseCharacterSet");
        this.outputTableFilter = Objects.requireNonNull(
                outputTableFilter, "outputTableFilter");
        this.containerResolver = Objects.requireNonNull(
                containerResolver, "containerResolver");
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
        String container = containerResolver.apply(transaction.containerId());
        if (OracleContainer.ROOT_NAME.equals(container)) {
            return List.of();
        }
        return assembleOutput(transaction, schemaCatalog,
                previousSchemaCatalog, schemaCatalog, false, container);
    }

    public AssembledRedoTransaction assembleCommitted(
            CommittedRedoTransaction transaction,
            SchemaCatalog schemaCatalog,
            SchemaCatalog previousSchemaCatalog,
            SystemTransactionManager systemTransactionManager)
            throws IOException {
        Objects.requireNonNull(systemTransactionManager,
                "systemTransactionManager");
        String container = containerResolver.apply(transaction.containerId());
        if (OracleContainer.ROOT_NAME.equals(container)) {
            return new AssembledRedoTransaction(List.of(), List.of());
        }
        List<SystemDictionaryRedoChange> systemChanges =
                decodeSystemChanges(transaction, schemaCatalog, container);
        if (systemChanges.isEmpty()) {
            List<RedoJsonChange> changes = assemble(
                    transaction, schemaCatalog, previousSchemaCatalog);
            return new AssembledRedoTransaction(changes, List.of());
        }

        SystemTransactionCommit commit;
        try {
            for (SystemDictionaryRedoChange change : systemChanges) {
                systemTransactionManager.apply(
                        transaction.xid(), change.change());
            }
            commit = systemTransactionManager.commit(
                    transaction.xid(), transaction.commitPosition().scn());
        } catch (IOException | RuntimeException e) {
            systemTransactionManager.rollback(transaction.xid());
            throw e;
        }

        SchemaCatalog transactionSchemaCatalog = new SchemaCatalog();
        for (TableSchemaVersion version : commit.schemaVersions()) {
            if (!version.dropTombstone()) {
                transactionSchemaCatalog.add(
                        version.decode(tableSchemaJsonCodec));
            }
        }
        List<RedoJsonChange> changes = assembleOutput(
                transaction, schemaCatalog, previousSchemaCatalog,
                transactionSchemaCatalog, true, container);
        return new AssembledRedoTransaction(
                changes, commit.schemaVersions());
    }

    private List<RedoJsonChange> assembleOutput(
            CommittedRedoTransaction transaction,
            SchemaCatalog schemaCatalog,
            SchemaCatalog previousSchemaCatalog,
            SchemaCatalog transactionSchemaCatalog,
            boolean skipSystemRows,
            String container) {
        List<RedoJsonChange> changes = new ArrayList<>();
        RedoRowGroupAssembler rowAssembler = new RedoRowGroupAssembler();
        RedoDdlAssembler ddlAssembler = new RedoDdlAssembler(
                databaseCharacterSet, container);
        RedoLobContext lobContext = RedoLobContext.from(
                transaction.entries(), schemaCatalog,
                transactionSchemaCatalog, byteOrder, container);
        for (RedoTransactionEntry entry : transaction.entries()) {
            appendEntry(transaction, schemaCatalog, previousSchemaCatalog,
                    transactionSchemaCatalog, rowAssembler, ddlAssembler,
                    lobContext, changes, entry, skipSystemRows, container);
        }
        rowAssembler.finish(transaction.xid());
        ddlAssembler.finish();
        return List.copyOf(changes);
    }

    private void appendEntry(
            CommittedRedoTransaction transaction,
            SchemaCatalog schemaCatalog,
            SchemaCatalog previousSchemaCatalog,
            SchemaCatalog transactionSchemaCatalog,
            RedoRowGroupAssembler rowAssembler,
            RedoDdlAssembler ddlAssembler,
            RedoLobContext lobContext,
            List<RedoJsonChange> changes,
            RedoTransactionEntry entry,
            boolean skipSystemRows,
            String container) {
        if (skipClusterOrPartitionMove(entry)) {
            return;
        }
        int operation = entry.operationCode();
        if (isRowOperation(operation)) {
            appendRow(schemaCatalog, rowAssembler, lobContext, changes, entry,
                    skipSystemRows, container);
            return;
        }
        if (operation == 0x05010B0B || operation == 0x05010B0C) {
            if (rowAssembler.hasPendingRow()) {
                throw new RedoLogException(50057,
                        "Multi-row DML interrupts an incomplete row group at offset "
                                + entry.first().fileOffset);
            }
            appendMultiRows(
                    schemaCatalog, lobContext, changes, entry, skipSystemRows,
                    container);
            return;
        }
        if (operation == 0x18010000) {
            if (rowAssembler.hasPendingRow()) {
                throw new RedoLogException(50057,
                        "DDL interrupts an incomplete row group at offset "
                                + entry.first().fileOffset);
            }
            ddlAssembler.accept(
                    entry.first(), transaction.commitPosition().scn(),
                    transactionSchemaCatalog, schemaCatalog,
                    previousSchemaCatalog)
                    .filter(change -> outputTableFilter.test(
                            change.change().qualifiedName()))
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

    private void appendMultiRows(
            SchemaCatalog schemaCatalog,
            RedoLobContext lobContext,
            List<RedoJsonChange> changes,
            RedoTransactionEntry entry,
            boolean skipSystemRows,
            String container) {
        RedoLogRecord redo = entry.second().orElseThrow();
        RedoRecordPair pair = new RedoRecordPair(entry.first(), redo);
        TableSchema table = resolveTable(
                List.of(pair), schemaCatalog, container);
        if ("SYS".equals(table.owner())) {
            if (skipSystemRows) {
                return;
            }
            throw new DataException(50071,
                    "System dictionary redo for "
                            + table.qualifiedName()
                            + " must use the system transaction pipeline");
        }
        if (!outputTableFilter.test(table.qualifiedName())) {
            return;
        }
        for (DecodedRedoRow row : multiRowDecoder.decode(
                table, entry.first(), redo)) {
            changes.add(new RedoJsonDmlChange(
                    lobValueResolver.resolve(row, lobContext)));
        }
    }

    private void appendRow(
            SchemaCatalog schemaCatalog,
            RedoRowGroupAssembler rowAssembler,
            RedoLobContext lobContext,
            List<RedoJsonChange> changes,
            RedoTransactionEntry entry,
            boolean skipSystemRows,
            String container) {
        RedoLogRecord redo = entry.second().orElseThrow();
        Optional<List<RedoRecordPair>> complete = rowAssembler.accept(
                entry.first(), redo);
        if (complete.isEmpty()) {
            return;
        }
        List<RedoRecordPair> group = complete.orElseThrow();
        TableSchema table = resolveTable(group, schemaCatalog, container);
        if ("SYS".equals(table.owner())) {
            if (skipSystemRows) {
                return;
            }
            throw new DataException(50071,
                    "System dictionary redo for "
                            + table.qualifiedName()
                            + " must use the system transaction pipeline");
        }
        if (!outputTableFilter.test(table.qualifiedName())) {
            return;
        }
        DecodedRedoRow row = rowDecoder.decode(table, group);
        changes.add(new RedoJsonDmlChange(
                lobValueResolver.resolve(row, lobContext)));
    }

    private List<SystemDictionaryRedoChange> decodeSystemChanges(
            CommittedRedoTransaction transaction,
            SchemaCatalog schemaCatalog,
            String container) {
        List<SystemDictionaryRedoChange> changes = new ArrayList<>();
        RedoRowGroupAssembler rowAssembler = new RedoRowGroupAssembler();
        boolean userRows = false;
        for (RedoTransactionEntry entry : transaction.entries()) {
            if (skipClusterOrPartitionMove(entry)) {
                continue;
            }
            int operation = entry.operationCode();
            if (isRowOperation(operation)) {
                RedoLogRecord redo = entry.second().orElseThrow();
                Optional<List<RedoRecordPair>> complete = rowAssembler.accept(
                        entry.first(), redo);
                if (complete.isEmpty()) {
                    continue;
                }
                List<RedoRecordPair> group = complete.orElseThrow();
                TableSchema table = resolveTable(
                        group, schemaCatalog, container);
                if (!"SYS".equals(table.owner())) {
                    userRows = true;
                    continue;
                }
                SystemDictionaryTable dictionaryTable =
                        SystemDictionaryTable.findByTableName(table.name())
                                .orElseThrow(() -> new DataException(50071,
                                        "SYS redo table is not translated: "
                                                + table.qualifiedName()));
                changes.add(systemDictionaryBridge.decode(
                        dictionaryTable, table, group));
                continue;
            }
            if (operation == 0x05010B0B || operation == 0x05010B0C) {
                if (rowAssembler.hasPendingRow()) {
                    throw new RedoLogException(50057,
                            "Multi-row DML interrupts an incomplete row group at offset "
                                    + entry.first().fileOffset);
                }
                RedoLogRecord redo = entry.second().orElseThrow();
                RedoRecordPair pair = new RedoRecordPair(
                        entry.first(), redo);
                TableSchema table = resolveTable(
                        List.of(pair), schemaCatalog, container);
                if (!"SYS".equals(table.owner())) {
                    userRows = true;
                    continue;
                }
                List<DecodedRedoRow> rows = multiRowDecoder.decode(
                        table, entry.first(), redo);
                SystemDictionaryTable dictionaryTable =
                        SystemDictionaryTable.findByTableName(table.name())
                                .orElseThrow(() -> new DataException(50071,
                                        "SYS redo table is not translated: "
                                                + table.qualifiedName()));
                for (DecodedRedoRow row : rows) {
                    changes.add(toSystemChange(
                            transaction.xid(), dictionaryTable, row));
                }
                continue;
            }
            if (!isNonOutputOperation(operation)
                    && operation != 0x18010000) {
                throw new RedoLogException(50057,
                        "Unknown committed operation 0x"
                                + Integer.toHexString(operation)
                                + " at offset " + entry.first().fileOffset);
            }
        }
        rowAssembler.finish(transaction.xid());
        if (!changes.isEmpty() && userRows) {
            throw new DataException(50071,
                    "A committed transaction mixes SYS dictionary and user rows: "
                            + transaction.xid());
        }
        return List.copyOf(changes);
    }

    private static SystemDictionaryRedoChange toSystemChange(
            Xid xid,
            SystemDictionaryTable table,
            DecodedRedoRow row) {
        if (row.operation() == RedoRowOperation.DELETE) {
            return new SystemDictionaryRedoChange(
                    xid, SystemDictionaryChange.delete(
                            table, row.rowId()));
        }
        if (row.operation() != RedoRowOperation.INSERT) {
            throw new RedoLogException(50057,
                    "Unsupported multi-row dictionary operation "
                            + row.operation());
        }
        Map<String, SystemDictionaryValue> values =
                new LinkedHashMap<>();
        for (Map.Entry<String, RedoColumnValue> entry
                : row.after().entrySet()) {
            RedoColumnValue value = entry.getValue();
            if (value.nullValue()) {
                values.put(entry.getKey(),
                        SystemDictionaryValue.nullValue(value.type()));
            } else {
                values.put(entry.getKey(),
                        SystemDictionaryValue.of(
                                value.type(), value.data()));
            }
        }
        return new SystemDictionaryRedoChange(
                xid, new SystemDictionaryChange(
                        SystemDictionaryOperation.INSERT,
                        table, row.rowId(), values));
    }

    private static TableSchema resolveTable(
            List<RedoRecordPair> group,
            SchemaCatalog schemaCatalog,
            String container) {
        RedoRecordPair first = group.get(0);
        Optional<TableSchema> byObject = findByObjectId(
                schemaCatalog, container, first.undo().obj);
        if (byObject.isEmpty()) {
            byObject = findByObjectId(
                    schemaCatalog, container, first.redo().obj);
        }
        if (byObject.isEmpty()) {
            byObject = findByDataObjectId(
                    schemaCatalog, container, first.redo().dataObj);
        }
        if (byObject.isEmpty()) {
            byObject = findByDataObjectId(
                    schemaCatalog, container, first.undo().dataObj);
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
            Optional<TableSchema> current = findByObjectId(
                    schemaCatalog, container, pair.undo().obj);
            if (current.isPresent() && !current.get().equals(table)) {
                throw new DataException(50071,
                        "Redo row group resolves to multiple table schemas");
            }
        }
        return table;
    }

    private static Optional<TableSchema> findByObjectId(
            SchemaCatalog schemaCatalog, String container, long objectId) {
        if (container == null) {
            return schemaCatalog.findByObjectId(objectId);
        }
        return schemaCatalog.findByObjectId(container, objectId);
    }

    private static Optional<TableSchema> findByDataObjectId(
            SchemaCatalog schemaCatalog,
            String container,
            long dataObjectId) {
        if (container == null) {
            return schemaCatalog.findByDataObjectId(dataObjectId);
        }
        return schemaCatalog.findByDataObjectId(container, dataObjectId);
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
