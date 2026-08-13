/*
 * Java translation derived from OpenLogReplicator src/common/LobCtx.cpp and
 * parser/Transaction.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.lob;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionEntry;
import io.github.arlowen.redoreplicator.schema.LobSchema;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RedoLobContext {
    private final Map<LobId, RedoLobData> lobs = new LinkedHashMap<>();

    public static RedoLobContext from(
            List<RedoTransactionEntry> entries,
            SchemaCatalog schemaCatalog,
            SchemaCatalog transactionSchemaCatalog) {
        RedoLobContext context = new RedoLobContext();
        for (RedoTransactionEntry entry : entries) {
            if (entry.paired()) {
                RedoLogRecord record = entry.second().orElseThrow();
                if (record.opCode == 0x1A02
                        && record.indKeyDataCode == 0x06
                        && !record.lobId.equals(LobId.zero())) {
                    int pageSize = context.pageSize(
                            record.dataObj, schemaCatalog,
                            transactionSchemaCatalog);
                    context.addDataRecord(
                            record, pageSize, record.lobOffset);
                }
                continue;
            }
            RedoLogRecord record = entry.first();
            if (record.opCode != 0x1301 && record.opCode != 0x1A06) {
                continue;
            }
            Optional<LobSchema> lob = transactionSchemaCatalog
                    .findLobByDataObjectId(record.dataObj);
            if (lob.isEmpty()) {
                lob = schemaCatalog.findLobByDataObjectId(record.dataObj);
            }
            if (lob.isPresent()) {
                context.addDataRecord(record,
                        lob.orElseThrow().pageSize(record.dataObj), 0);
            }
        }
        return context;
    }

    public byte[] readIndexed(
            LobId lobId,
            long pageCount,
            int sizeRest,
            List<Long> explicitPages) {
        RedoLobData lob = require(lobId);
        long outputSize = Math.multiplyExact(
                pageCount, lob.pageSize()) + sizeRest;
        if (outputSize > Integer.MAX_VALUE) {
            throw invalid(lobId, "LOB output exceeds the Java array limit");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                (int) outputSize);
        long totalPages = pageCount;
        if (sizeRest > 0) {
            totalPages++;
        }
        for (long pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            long page;
            if (pageNumber < explicitPages.size()) {
                page = explicitPages.get((int) pageNumber);
            } else {
                page = lob.indexedPage(pageNumber);
            }
            int size = lob.pageSize();
            if (pageNumber == pageCount) {
                size = sizeRest;
            }
            output.writeBytes(lob.readPage(page, size));
        }
        if (output.size() != outputSize) {
            throw invalid(lobId, "LOB output contains " + output.size()
                    + " bytes, expected " + outputSize);
        }
        return output.toByteArray();
    }

    int lobCount() {
        return lobs.size();
    }

    private void addDataRecord(
            RedoLogRecord record, int pageSize, int pageOffset) {
        if (record.lobData < 0 || record.lobDataSize < 0
                || record.lobData > record.size - record.lobDataSize) {
            throw invalid(record.lobId,
                    "LOB payload extends past the redo record");
        }
        int start = record.dataOffset() + record.lobData;
        byte[] data = Arrays.copyOfRange(
                record.data(), start, start + record.lobDataSize);
        RedoLobData lob = lobs.computeIfAbsent(
                record.lobId, RedoLobData::new);
        lob.addPage(record.dba, pageOffset, data, pageSize);
        if (record.lobPageNo != RedoLogRecord.INVALID_LOB_PAGE_NO) {
            lob.setPage(record.lobPageNo, record.dba);
        }
    }

    private int pageSize(
            long dataObjectId,
            SchemaCatalog schemaCatalog,
            SchemaCatalog transactionSchemaCatalog) {
        Optional<LobSchema> lob = transactionSchemaCatalog
                .findLobByDataObjectId(dataObjectId);
        if (lob.isEmpty()) {
            lob = transactionSchemaCatalog
                    .findLobIndexByDataObjectId(dataObjectId);
        }
        if (lob.isEmpty()) {
            lob = schemaCatalog.findLobByDataObjectId(dataObjectId);
        }
        if (lob.isEmpty()) {
            lob = schemaCatalog.findLobIndexByDataObjectId(dataObjectId);
        }
        if (lob.isEmpty()) {
            return 0;
        }
        LobSchema schema = lob.orElseThrow();
        return schema.pageSize(schema.dataObjectId());
    }

    private RedoLobData require(LobId lobId) {
        RedoLobData lob = lobs.get(lobId);
        if (lob == null) {
            throw invalid(lobId, "transaction contains no matching LOB data");
        }
        return lob;
    }

    private static RedoLogException invalid(LobId lobId, String reason) {
        return new RedoLogException(50075,
                "Invalid LOB " + lobId.upper() + ": " + reason);
    }
}
