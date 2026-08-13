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
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.parser.RedoKdliDecoder;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionEntry;
import io.github.arlowen.redoreplicator.schema.LobSchema;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;

import java.io.ByteArrayOutputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RedoLobContext {
    private final Map<LobId, RedoLobData> lobs = new LinkedHashMap<>();
    private final Map<Long, RedoLobListPage> listPages =
            new LinkedHashMap<>();
    private final RedoByteReader byteReader;

    private RedoLobContext(ByteOrder byteOrder) {
        byteReader = new RedoByteReader(byteOrder);
    }

    public static RedoLobContext from(
            List<RedoTransactionEntry> entries,
            SchemaCatalog schemaCatalog,
            SchemaCatalog transactionSchemaCatalog,
            ByteOrder byteOrder) {
        return from(entries, schemaCatalog, transactionSchemaCatalog,
                byteOrder, null);
    }

    public static RedoLobContext from(
            List<RedoTransactionEntry> entries,
            SchemaCatalog schemaCatalog,
            SchemaCatalog transactionSchemaCatalog,
            ByteOrder byteOrder,
            String container) {
        RedoLobContext context = new RedoLobContext(byteOrder);
        for (RedoTransactionEntry entry : entries) {
            if (entry.paired()) {
                RedoLogRecord record = entry.second().orElseThrow();
                if (record.opCode == 0x0A02
                        || record.opCode == 0x0A08
                        || record.opCode == 0x0A12) {
                    context.addIndexRecord(record);
                }
                if (record.opCode == 0x1A02) {
                    context.addListRecord(record);
                }
                if (record.opCode == 0x1A02
                        && record.indKeyDataCode == 0x06
                        && !record.lobId.equals(LobId.zero())) {
                    int pageSize = context.pageSize(
                            record.dataObj, schemaCatalog,
                            transactionSchemaCatalog, container);
                    context.addDataRecord(
                            record, pageSize, record.lobOffset);
                }
                continue;
            }
            RedoLogRecord record = entry.first();
            if (record.opCode != 0x1301 && record.opCode != 0x1A06) {
                continue;
            }
            Optional<LobSchema> lob = findLobByDataObjectId(
                    transactionSchemaCatalog, container, record.dataObj);
            if (lob.isEmpty()) {
                lob = findLobByDataObjectId(
                        schemaCatalog, container, record.dataObj);
            }
            if (lob.isPresent()) {
                context.addDataRecord(record,
                        lob.orElseThrow().pageSize(record.dataObj), 0);
            }
        }
        return context;
    }

    public byte[] readExtents(
            LobId lobId, long size, List<RedoLobExtent> extents) {
        RedoLobData lob = require(lobId);
        if (size > Integer.MAX_VALUE) {
            throw invalid(lobId, "LOB output exceeds the Java array limit");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) size);
        for (RedoLobExtent extent : extents) {
            long page = extent.firstPage();
            for (int index = 0; index < extent.pageCount(); index++) {
                byte[] data = lob.readPage(page);
                if (output.size() > size - data.length) {
                    throw invalid(lobId,
                            "LOB page data exceeds the locator length");
                }
                output.writeBytes(data);
                page++;
            }
        }
        if (output.size() != size) {
            throw invalid(lobId, "LOB output contains " + output.size()
                    + " bytes, expected " + size);
        }
        return output.toByteArray();
    }

    public byte[] readList(LobId lobId, long size, long firstListPage) {
        List<RedoLobExtent> extents = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        long listPage = firstListPage;
        while (listPage != 0) {
            if (!visited.add(listPage)) {
                throw invalid(lobId,
                        "LOB list page chain contains a cycle at " + listPage);
            }
            RedoLobListPage page = listPages.get(listPage);
            if (page == null) {
                throw invalid(lobId,
                        "missing LOB list page " + listPage);
            }
            extents.addAll(page.extents());
            listPage = page.nextPage();
        }
        return readExtents(lobId, size, extents);
    }

    public byte[] readOutOfRow(LobId lobId) {
        RedoLobData lob = require(lobId);
        long pageCount = lob.sizePages();
        int sizeRest = lob.sizeRest();
        if (pageCount == 0 && sizeRest == 0) {
            return new byte[0];
        }
        return readIndexed(lobId, pageCount, sizeRest, List.of());
    }

    public byte[] readIndexed(
            LobId lobId,
            long pageCount,
            int sizeRest,
            List<Long> explicitPages) {
        RedoLobData lob = require(lobId);
        if (pageCount == 0 && sizeRest == 0) {
            return new byte[0];
        }
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

    private void addIndexRecord(RedoLogRecord record) {
        if (record.lobId.equals(LobId.zero())) {
            return;
        }
        RedoLobData lob = lobs.computeIfAbsent(
                record.lobId, RedoLobData::new);
        int start = 16;
        long pageNumber = record.lobPageNo;
        if (pageNumber > 0) {
            start = 0;
        }
        if (record.indKeyDataSize > start) {
            int pageBytes = record.indKeyDataSize - start;
            if (pageBytes % 4 != 0
                    || !hasRange(record, record.indKeyData + start,
                    pageBytes)) {
                throw invalid(record.lobId,
                        "LOB index page list is malformed");
            }
            int position = start;
            while (position < record.indKeyDataSize) {
                long page = readBigEndianUnsignedInt(
                        record, record.indKeyData + position);
                if (page > 0) {
                    lob.setPage(pageNumber, page);
                }
                pageNumber++;
                position += 4;
            }
        }
        if (record.opCode == 0x0A12 && record.lobPageNo == 0) {
            lob.setSize(record.lobSizePages, record.lobSizeRest);
        }
    }

    private void addListRecord(RedoLogRecord record) {
        if (record.dba0 != 0) {
            orderList(record.dba, record.dba0);
            if (record.dba1 != 0) {
                orderList(record.dba0, record.dba1);
                if (record.dba2 != 0) {
                    orderList(record.dba1, record.dba2);
                    if (record.dba3 != 0) {
                        orderList(record.dba2, record.dba3);
                    }
                }
            }
        }
        if (record.indKeyDataCode == RedoKdliDecoder.CODE_LMAP
                || record.indKeyDataCode
                == RedoKdliDecoder.CODE_LOAD_ITREE) {
            setList(record);
        } else if (record.indKeyDataCode == RedoKdliDecoder.CODE_IMAP
                || record.indKeyDataCode == RedoKdliDecoder.CODE_ALMAP) {
            appendList(record);
        }
    }

    private void orderList(long page, long nextPage) {
        listPages.computeIfAbsent(page, ignored -> new RedoLobListPage())
                .setNextPage(nextPage);
    }

    private void setList(RedoLogRecord record) {
        requireListRange(record, 0, 8);
        long count = readUnsignedInt(record, 4);
        List<RedoLobExtent> extents = readExtents(record, 8, count);
        listPages.computeIfAbsent(
                        record.dba, ignored -> new RedoLobListPage())
                .replace(extents);
    }

    private void appendList(RedoLogRecord record) {
        requireListRange(record, 0, 12);
        long count = readUnsignedInt(record, 4);
        long startIndex = readUnsignedInt(record, 8);
        if (startIndex > Integer.MAX_VALUE - count) {
            throw invalid(record.lobId,
                    "LOB list entry index exceeds the Java list limit");
        }
        List<RedoLobExtent> extents = readExtents(record, 12, count);
        listPages.computeIfAbsent(
                        record.dba, ignored -> new RedoLobListPage())
                .append((int) startIndex, extents);
    }

    private List<RedoLobExtent> readExtents(
            RedoLogRecord record, int start, long count) {
        if (count > (record.indKeyDataSize - start) / 8
                || !hasRange(record, record.indKeyData + start,
                (int) count * 8)) {
            throw invalid(record.lobId,
                    "LOB list entries extend past the redo field");
        }
        List<RedoLobExtent> extents = new ArrayList<>((int) count);
        int position = start;
        for (long index = 0; index < count; index++) {
            int pageCount = readUnsignedShort(record, position + 2);
            long firstPage = readUnsignedInt(record, position + 4);
            extents.add(new RedoLobExtent(firstPage, pageCount));
            position += 8;
        }
        return extents;
    }

    private void requireListRange(
            RedoLogRecord record, int position, int size) {
        if (record.indKeyDataSize < position + size
                || !hasRange(record,
                record.indKeyData + position, size)) {
            throw invalid(record.lobId,
                    "LOB list field is malformed");
        }
    }

    private int pageSize(
            long dataObjectId,
            SchemaCatalog schemaCatalog,
            SchemaCatalog transactionSchemaCatalog,
            String container) {
        Optional<LobSchema> lob = findLobByDataObjectId(
                transactionSchemaCatalog, container, dataObjectId);
        if (lob.isEmpty()) {
            lob = findLobIndexByDataObjectId(
                    transactionSchemaCatalog, container, dataObjectId);
        }
        if (lob.isEmpty()) {
            lob = findLobByDataObjectId(
                    schemaCatalog, container, dataObjectId);
        }
        if (lob.isEmpty()) {
            lob = findLobIndexByDataObjectId(
                    schemaCatalog, container, dataObjectId);
        }
        if (lob.isEmpty()) {
            return 0;
        }
        LobSchema schema = lob.orElseThrow();
        return schema.pageSize(schema.dataObjectId());
    }

    private static Optional<LobSchema> findLobByDataObjectId(
            SchemaCatalog schemaCatalog,
            String container,
            long dataObjectId) {
        if (container == null) {
            return schemaCatalog.findLobByDataObjectId(dataObjectId);
        }
        return schemaCatalog.findLobByDataObjectId(container, dataObjectId);
    }

    private static Optional<LobSchema> findLobIndexByDataObjectId(
            SchemaCatalog schemaCatalog,
            String container,
            long dataObjectId) {
        if (container == null) {
            return schemaCatalog.findLobIndexByDataObjectId(dataObjectId);
        }
        return schemaCatalog.findLobIndexByDataObjectId(
                container, dataObjectId);
    }

    private RedoLobData require(LobId lobId) {
        RedoLobData lob = lobs.get(lobId);
        if (lob == null) {
            throw invalid(lobId, "transaction contains no matching LOB data");
        }
        return lob;
    }

    private int readUnsignedShort(
            RedoLogRecord record, int position) {
        int absolute = record.dataOffset()
                + record.indKeyData + position;
        return byteReader.readUnsignedShort(record.data(), absolute);
    }

    private long readUnsignedInt(
            RedoLogRecord record, int position) {
        int absolute = record.dataOffset()
                + record.indKeyData + position;
        return byteReader.readUnsignedInt(record.data(), absolute);
    }

    private static long readBigEndianUnsignedInt(
            RedoLogRecord record, int position) {
        int absolute = record.dataOffset() + position;
        return (long) (record.data()[absolute] & 0xFF) << 24
                | (long) (record.data()[absolute + 1] & 0xFF) << 16
                | (long) (record.data()[absolute + 2] & 0xFF) << 8
                | record.data()[absolute + 3] & 0xFFL;
    }

    private static boolean hasRange(
            RedoLogRecord record, int position, int size) {
        return position >= 0 && size >= 0
                && position <= record.size - size;
    }

    private static RedoLogException invalid(LobId lobId, String reason) {
        return new RedoLogException(50075,
                "Invalid LOB " + lobId.upper() + ": " + reason);
    }
}
