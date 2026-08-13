/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.lob;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.parser.RedoKdliDecoder;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionEntry;
import io.github.arlowen.redoreplicator.schema.LobPartition;
import io.github.arlowen.redoreplicator.schema.LobSchema;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedoLobContextTest {
    private static final long LOB_DATA_OBJECT_ID = 808;
    private static final LobId LOB_ID = LobId.of(
            new byte[]{0, 0, 0, 1, 2, 3, 4, 5, 6, 7});

    @Test
    void assemblesExplicitAndIndexedDirectLoaderPages() throws Exception {
        RedoLobContext context = RedoLobContext.from(
                List.of(
                        RedoTransactionEntry.single(page(
                                100, 0, new byte[]{1, 2, 3, 4})),
                        RedoTransactionEntry.single(page(
                                101, 1, new byte[]{5, 6, 7}))),
                catalog(), catalog(), ByteOrder.LITTLE_ENDIAN);

        assertEquals(1, context.lobCount());
        byte[] value = context.readIndexed(
                LOB_ID, 1, 3, List.of(100L));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7}, value);

        Properties expected = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/lob-locator/"
                        + "openlogreplicator-6bc92bc1.properties")) {
            assertNotNull(input);
            expected.load(input);
        }
        assertEquals("true", expected.getProperty("external.ok"));
        assertEquals(expected.getProperty("external.hex"),
                HexFormat.of().formatHex(context.readIndexed(
                        LOB_ID, 0, 3, List.of(100L))));
    }

    @Test
    void stopsWhenAnIndexedPageIsMissing() {
        RedoLobContext context = RedoLobContext.from(
                List.of(RedoTransactionEntry.single(page(
                        100, 0, new byte[]{1, 2, 3, 4}))),
                catalog(), catalog(), ByteOrder.LITTLE_ENDIAN);

        RedoLogException error = assertThrows(
                RedoLogException.class,
                () -> context.readIndexed(
                        LOB_ID, 1, 3, List.of(100L)));
        assertEquals(50075, error.getErrorCode());
    }

    @Test
    void mergesKdliFillFragmentsAtTheirPageOffsets() {
        RedoLogRecord first = page(
                100, 0, new byte[]{1, 2});
        RedoLogRecord fill = page(
                100, 0, new byte[]{3, 4});
        fill.opCode = 0x1A02;
        fill.indKeyDataCode = 0x06;
        fill.lobOffset = 2;
        RedoLogRecord undo = new RedoLogRecord();
        undo.opCode = 0x0501;
        RedoLobContext context = RedoLobContext.from(
                List.of(
                        RedoTransactionEntry.single(first),
                        RedoTransactionEntry.pair(undo, fill)),
                catalog(), catalog(), ByteOrder.LITTLE_ENDIAN);

        assertArrayEquals(new byte[]{1, 2, 3, 4},
                context.readIndexed(LOB_ID, 1, 0, List.of(100L)));
    }

    @Test
    void assemblesClassicLobIndexPagesAndLength() throws Exception {
        byte[] indexData = new byte[20];
        indexData[9] = 3;
        writeUnsignedInt(indexData, 16, 100);
        RedoLogRecord index = new RedoLogRecord();
        index.attachData(indexData, 0, indexData.length);
        index.opCode = 0x0A12;
        index.lobId = LOB_ID;
        index.lobPageNo = 0;
        index.lobSizePages = 0;
        index.lobSizeRest = 3;
        index.indKeyData = 0;
        index.indKeyDataSize = indexData.length;
        RedoLogRecord undo = new RedoLogRecord();
        undo.opCode = 0x0501;

        RedoLobContext context = RedoLobContext.from(
                List.of(
                        RedoTransactionEntry.single(page(
                                100, 0, new byte[]{1, 2, 3})),
                        RedoTransactionEntry.pair(undo, index)),
                catalog(), catalog(), ByteOrder.LITTLE_ENDIAN);

        byte[] value = context.readOutOfRow(LOB_ID);
        assertArrayEquals(new byte[]{1, 2, 3}, value);

        Properties expected = new Properties();
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/lob-locator/"
                        + "openlogreplicator-6bc92bc1.properties")) {
            assertNotNull(input);
            expected.load(input);
        }
        assertEquals("true", expected.getProperty("outofrow.ok"));
        assertEquals(expected.getProperty("outofrow.hex"),
                HexFormat.of().formatHex(value));
    }

    @Test
    void preservesKnownEmptyOutOfRowLob() {
        RedoLogRecord index = new RedoLogRecord();
        index.attachData(new byte[10], 0, 10);
        index.opCode = 0x0A12;
        index.lobId = LOB_ID;
        index.indKeyDataSize = 10;
        RedoLogRecord undo = new RedoLogRecord();
        undo.opCode = 0x0501;

        RedoLobContext context = RedoLobContext.from(
                List.of(RedoTransactionEntry.pair(undo, index)),
                catalog(), catalog(), ByteOrder.LITTLE_ENDIAN);

        assertArrayEquals(new byte[0], context.readOutOfRow(LOB_ID));
    }

    @Test
    void stopsWhenClassicLobLengthIsUnknown() {
        RedoLobContext context = RedoLobContext.from(
                List.of(RedoTransactionEntry.single(page(
                        100, 0, new byte[]{1, 2, 3}))),
                catalog(), catalog(), ByteOrder.LITTLE_ENDIAN);

        RedoLogException error = assertThrows(
                RedoLogException.class,
                () -> context.readOutOfRow(LOB_ID));
        assertEquals(50075, error.getErrorCode());
    }

    @Test
    void resolvesDuplicateLobIdsWithinTheTransactionPdb() {
        SchemaCatalog catalogs = catalog("FREEPDB1");
        catalogs.addAll(catalog("REPORTING"));

        RedoLobContext context = RedoLobContext.from(
                List.of(RedoTransactionEntry.single(page(
                        100, 0, new byte[]{1, 2, 3}))),
                catalogs, new SchemaCatalog(),
                ByteOrder.LITTLE_ENDIAN, "REPORTING");

        assertArrayEquals(new byte[]{1, 2, 3},
                context.readIndexed(LOB_ID, 0, 3, List.of(100L)));
    }

    @Test
    void followsAndAppendsKdliListPages() {
        RedoLogRecord firstList = listRecord(
                RedoKdliDecoder.CODE_LMAP, 200, 0, 100, 1);
        firstList.dba0 = 201;
        RedoLogRecord secondList = listRecord(
                RedoKdliDecoder.CODE_ALMAP, 201, 0, 101, 1);
        RedoLogRecord undo = new RedoLogRecord();
        undo.opCode = 0x0501;

        RedoLobContext context = RedoLobContext.from(
                List.of(
                        RedoTransactionEntry.single(page(
                                100, 0, new byte[]{1, 2})),
                        RedoTransactionEntry.single(page(
                                101, 1, new byte[]{3, 4})),
                        RedoTransactionEntry.pair(undo, firstList),
                        RedoTransactionEntry.pair(undo, secondList)),
                catalog(), catalog(), ByteOrder.LITTLE_ENDIAN);

        assertArrayEquals(new byte[]{1, 2, 3, 4},
                context.readList(LOB_ID, 4, 200));
    }

    @Test
    void appendsKdliEntriesAfterAnExistingList() {
        RedoLogRecord initial = listRecord(
                RedoKdliDecoder.CODE_LMAP, 200, 0, 100, 1);
        RedoLogRecord appended = listRecord(
                RedoKdliDecoder.CODE_ALMAP, 200, 1, 101, 1);
        RedoLogRecord undo = new RedoLogRecord();
        undo.opCode = 0x0501;

        RedoLobContext context = RedoLobContext.from(
                List.of(
                        RedoTransactionEntry.single(page(
                                100, 0, new byte[]{1, 2})),
                        RedoTransactionEntry.single(page(
                                101, 1, new byte[]{3, 4})),
                        RedoTransactionEntry.pair(undo, initial),
                        RedoTransactionEntry.pair(undo, appended)),
                catalog(), catalog(), ByteOrder.LITTLE_ENDIAN);

        assertArrayEquals(new byte[]{1, 2, 3, 4},
                context.readList(LOB_ID, 4, 200));
    }

    @Test
    void stopsOnAKdliListPageCycle() {
        RedoLogRecord list = listRecord(
                RedoKdliDecoder.CODE_LMAP, 200, 0, 100, 1);
        list.dba0 = 200;
        RedoLogRecord undo = new RedoLogRecord();
        undo.opCode = 0x0501;
        RedoLobContext context = RedoLobContext.from(
                List.of(
                        RedoTransactionEntry.single(page(
                                100, 0, new byte[]{1, 2})),
                        RedoTransactionEntry.pair(undo, list)),
                catalog(), catalog(), ByteOrder.LITTLE_ENDIAN);

        RedoLogException error = assertThrows(
                RedoLogException.class,
                () -> context.readList(LOB_ID, 2, 200));
        assertEquals(50075, error.getErrorCode());
    }

    private static RedoLogRecord page(
            long dba, long pageNumber, byte[] payload) {
        RedoLogRecord record = new RedoLogRecord();
        record.attachData(payload, 0, payload.length);
        record.opCode = 0x1301;
        record.dataObj = LOB_DATA_OBJECT_ID;
        record.dba = dba;
        record.lobId = LOB_ID;
        record.lobPageNo = pageNumber;
        record.lobData = 0;
        record.lobDataSize = payload.length;
        return record;
    }

    private static SchemaCatalog catalog() {
        return catalog("FREEPDB1");
    }

    private static SchemaCatalog catalog(String container) {
        LobSchema lob = new LobSchema(
                22, LOB_DATA_OBJECT_ID, 44, 1, 1,
                List.of(), List.of(new LobPartition(
                        LOB_DATA_OBJECT_ID, 4)));
        TableSchema table = new TableSchema(
                container, "APP", "LOB_DATA",
                22, 33, 1, 0, 0,
                List.of(), List.of(lob), List.of());
        SchemaCatalog catalog = new SchemaCatalog();
        catalog.add(table);
        return catalog;
    }

    private static RedoLogRecord listRecord(
            int code, long dba, int startIndex,
            long firstPage, int pageCount) {
        int entryOffset = 8;
        if (code == RedoKdliDecoder.CODE_ALMAP
                || code == RedoKdliDecoder.CODE_IMAP) {
            entryOffset = 12;
        }
        byte[] data = new byte[entryOffset + 8];
        data[0] = (byte) code;
        writeLittleEndianInt(data, 4, 1);
        if (entryOffset == 12) {
            writeLittleEndianInt(data, 8, startIndex);
        }
        writeLittleEndianShort(data, entryOffset + 2, pageCount);
        writeLittleEndianInt(data, entryOffset + 4, firstPage);
        RedoLogRecord record = new RedoLogRecord();
        record.attachData(data, 0, data.length);
        record.opCode = 0x1A02;
        record.dba = dba;
        record.lobId = LOB_ID;
        record.indKeyDataCode = code;
        record.indKeyDataSize = data.length;
        return record;
    }

    private static void writeUnsignedInt(
            byte[] data, int offset, long value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    private static void writeLittleEndianInt(
            byte[] data, int offset, long value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static void writeLittleEndianShort(
            byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }
}
