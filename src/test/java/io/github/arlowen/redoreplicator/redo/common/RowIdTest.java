/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import io.github.arlowen.redoreplicator.error.DataException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RowIdTest {
    @Test
    void roundTripsOracleExtendedRowId() {
        RowId rowId = RowId.parse("AAAAZ8AABAAAOwgAAA");

        assertEquals("AAAAZ8AABAAAOwgAAA", rowId.toString());
        assertEquals(1, rowId.dataBlockAddress() >>> 22);
        assertEquals(0, rowId.slot());
    }

    @Test
    void buildsAndOrdersRowIdsByUnsignedFields() {
        RowId first = RowId.of(100, (3L << 22) | 200, 5);
        RowId second = RowId.of(100, (3L << 22) | 201, 0);

        assertEquals(first, RowId.parse(first.toString()));
        assertEquals("00c000c8.0064.0005", first.toHexString());
        assertEquals(-1, Integer.signum(first.compareTo(second)));
    }

    @Test
    void decodesBinaryRowIdUsingUpstreamLayout() {
        byte[] bytes = {
                0x00, 0x00, 0x00, 0x64,
                0x00, 0x05,
                0x00, 0x03,
                0x00, 0x00, 0x00, (byte) 0xC8
        };

        RowId rowId = RowId.fromBinary(bytes);

        assertEquals(100, rowId.dataObject());
        assertEquals((3L << 22) | 200, rowId.dataBlockAddress());
        assertEquals(5, rowId.slot());
    }

    @Test
    void rejectsIncorrectTextLength() {
        DataException error = assertThrows(DataException.class, () -> RowId.parse("too-short"));
        assertEquals(20008, error.getErrorCode());
    }
}
