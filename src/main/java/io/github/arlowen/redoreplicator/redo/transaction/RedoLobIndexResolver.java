/*
 * Java translation derived from OpenLogReplicator
 * Parser::appendToTransactionIndex.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

import java.util.Arrays;

final class RedoLobIndexResolver {
    boolean resolve(RedoLogRecord undo, RedoLogRecord redo) {
        if (redo.opCode == 0x0A02) {
            if (!isIndexKey(redo, redo.indKey)) {
                return false;
            }
            readIdentity(redo, redo, redo.indKey);
        } else if (redo.opCode == 0x0A08) {
            if (!isIndexInitialization(redo)) {
                return false;
            }
            readIdentity(redo, redo, redo.indKey + 34);
            redo.indKeyData = redo.indKey + 2;
            redo.indKeyDataSize = 32;
        } else if (redo.opCode == 0x0A12) {
            if (!isIndexKey(undo, undo.indKey)
                    || !hasRange(redo, redo.indKeyData + 4, 6)) {
                return false;
            }
            readIdentity(undo, redo, undo.indKey);
            redo.lobSizePages = readUnsignedInt(
                    redo, redo.indKeyData + 4);
            redo.lobSizeRest = readUnsignedShort(
                    redo, redo.indKeyData + 8);
        } else if (redo.opCode != 0x1A02) {
            return false;
        }
        byte[] lobId = redo.lobId.bytes();
        return lobId[0] == 0 && lobId[1] == 0
                && lobId[2] == 0 && lobId[3] == 1;
    }

    private static boolean isIndexKey(
            RedoLogRecord record, int position) {
        return record.indKeySize == 16
                && hasRange(record, position, 16)
                && record.byteAt(position) == 10
                && record.byteAt(position + 11) == 4;
    }

    private static boolean isIndexInitialization(RedoLogRecord record) {
        int position = record.indKey;
        return position != 0 && record.indKeySize == 50
                && hasRange(record, position, 50)
                && record.byteAt(position) == 0x01
                && record.byteAt(position + 1) == 0x01
                && record.byteAt(position + 34) == 10
                && record.byteAt(position + 45) == 4;
    }

    private static void readIdentity(
            RedoLogRecord source, RedoLogRecord target, int position) {
        int absolute = source.dataOffset() + position;
        target.lobId = LobId.of(Arrays.copyOfRange(
                source.data(), absolute + 1,
                absolute + 1 + LobId.LENGTH));
        target.lobPageNo = readUnsignedInt(source, position + 12);
    }

    private static int readUnsignedShort(
            RedoLogRecord record, int position) {
        int absolute = record.dataOffset() + position;
        return (record.data()[absolute] & 0xFF) << 8
                | record.data()[absolute + 1] & 0xFF;
    }

    private static long readUnsignedInt(
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
}
