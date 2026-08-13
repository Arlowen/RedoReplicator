/*
 * Java translation derived from OpenLogReplicator SystemTransaction commit semantics.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import io.github.arlowen.redoreplicator.state.SchemaSource;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SystemTransactionManager {
    private final String container;
    private final long defaultCharacterSetId;
    private final long defaultNationalCharacterSetId;
    private final Charset dictionaryCharacterSet;
    private final SystemDictionarySchemaAssembler schemaAssembler;
    private final TableSchemaJsonCodec jsonCodec;
    private final Map<Xid, SystemTransaction> transactions;

    private SystemDictionaryState dictionaryState;

    public SystemTransactionManager(SystemDictionaryState dictionaryState,
                                    String container,
                                    long defaultCharacterSetId,
                                    long defaultNationalCharacterSetId,
                                    Charset dictionaryCharacterSet,
                                    TableSchemaJsonCodec jsonCodec) {
        this.dictionaryState = dictionaryState;
        this.container = container;
        this.defaultCharacterSetId = defaultCharacterSetId;
        this.defaultNationalCharacterSetId = defaultNationalCharacterSetId;
        this.dictionaryCharacterSet = dictionaryCharacterSet;
        this.jsonCodec = jsonCodec;
        schemaAssembler = new SystemDictionarySchemaAssembler();
        transactions = new LinkedHashMap<>();
    }

    public void apply(Xid xid, SystemDictionaryChange change) {
        SystemTransaction transaction = transactions.get(xid);
        boolean newTransaction = transaction == null;
        if (newTransaction) {
            transaction = new SystemTransaction(
                    xid, dictionaryState, dictionaryCharacterSet);
            transactions.put(xid, transaction);
        }
        try {
            transaction.apply(change);
        } catch (RuntimeException e) {
            if (newTransaction) {
                transactions.remove(xid);
            }
            throw e;
        }
    }

    public SystemTransactionCommit commit(Xid xid, Scn commitScn)
            throws IOException {
        SystemTransaction transaction = transactions.get(xid);
        if (transaction == null) {
            throw new DataException(50020,
                    "System transaction is missing for commit: " + xid);
        }
        SystemDictionaryState nextState = transaction.commitAgainst(dictionaryState);
        List<TableSchemaVersion> versions = new ArrayList<>();
        for (Long objectId : transaction.touchedObjectIds(nextState).stream()
                .sorted(Long::compareUnsigned)
                .toList()) {
            var schema = schemaAssembler.assemble(
                    nextState,
                    container,
                    objectId,
                    defaultCharacterSetId,
                    defaultNationalCharacterSetId);
            if (schema.isPresent()) {
                versions.add(TableSchemaVersion.ddl(
                        schema.get(),
                        commitScn,
                        "SYSTEM_TRANSACTION",
                        "",
                        SchemaSource.REDO,
                        jsonCodec));
                continue;
            }
            TableSchema previous = schemaAssembler.assemble(
                    dictionaryState,
                    container,
                    objectId,
                    defaultCharacterSetId,
                    defaultNationalCharacterSetId).orElse(null);
            if (previous != null) {
                TableSchemaVersion previousVersion = TableSchemaVersion.ddl(
                        previous,
                        commitScn,
                        "SYSTEM_TRANSACTION",
                        "",
                        SchemaSource.REDO,
                        jsonCodec);
                versions.add(TableSchemaVersion.drop(
                        previousVersion,
                        commitScn,
                        "SYSTEM_TRANSACTION_DROP",
                        "",
                        SchemaSource.REDO));
            }
        }
        dictionaryState = nextState;
        transactions.remove(xid);
        return new SystemTransactionCommit(dictionaryState, versions);
    }

    public void rollback(Xid xid) {
        transactions.remove(xid);
    }

    public SystemDictionaryState dictionaryState() {
        return dictionaryState;
    }

    public int openTransactionCount() {
        return transactions.size();
    }
}
