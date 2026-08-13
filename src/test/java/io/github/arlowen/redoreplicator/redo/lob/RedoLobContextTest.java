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
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionEntry;
import io.github.arlowen.redoreplicator.schema.LobPartition;
import io.github.arlowen.redoreplicator.schema.LobSchema;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
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
                catalog(), catalog());

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
                catalog(), catalog());

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
                catalog(), catalog());

        assertArrayEquals(new byte[]{1, 2, 3, 4},
                context.readIndexed(LOB_ID, 1, 0, List.of(100L)));
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
        LobSchema lob = new LobSchema(
                22, LOB_DATA_OBJECT_ID, 44, 1, 1,
                List.of(), List.of(new LobPartition(
                        LOB_DATA_OBJECT_ID, 4)));
        TableSchema table = new TableSchema(
                "FREEPDB1", "APP", "LOB_DATA",
                22, 33, 1, 0, 0,
                List.of(), List.of(lob), List.of());
        SchemaCatalog catalog = new SchemaCatalog();
        catalog.add(table);
        return catalog;
    }
}
