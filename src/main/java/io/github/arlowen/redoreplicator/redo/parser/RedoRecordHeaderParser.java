/*
 * Java translation derived from redo record and LWN header handling in
 * OpenLogReplicator src/parser/Parser.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;

public final class RedoRecordHeaderParser {
    private final RedoByteReader byteReader;

    public RedoRecordHeaderParser(RedoByteReader byteReader) {
        this.byteReader = byteReader;
    }

    public RedoRecordHeader parse(byte[] data, int offset, int expectedSize) {
        if (offset < 0 || expectedSize < 5 || offset > data.length - 5) {
            throw new RedoLogException(50046, "redo record is too small for its base header");
        }
        long recordSize = byteReader.readUnsignedInt(data, offset);
        if (recordSize != expectedSize) {
            throw new RedoLogException(50046, "too small log record, buffer size: "
                    + expectedSize + ", field size: " + recordSize);
        }

        int validity = data[offset + 4] & 0xFF;
        int headerSize = 24;
        if ((validity & 0x04) != 0) {
            headerSize = 68;
        }
        if (headerSize > recordSize) {
            throw new RedoLogException(50046, "too small log record, header size: "
                    + headerSize + ", field size: " + recordSize);
        }
        if (offset > data.length - headerSize) {
            throw new RedoLogException(50046, "redo record buffer does not contain its complete header");
        }

        long containerUid = 0;
        if (recordSize >= 20) {
            containerUid = byteReader.readUnsignedInt(data, offset + 16);
        }
        return new RedoRecordHeader(recordSize, validity, headerSize, containerUid);
    }

    public RedoLwnHeader parseLwn(byte[] block, int offset, int expectedSize) {
        RedoRecordHeader recordHeader = parse(block, offset, expectedSize);
        if (!recordHeader.isExtended()) {
            throw new RedoLogException(50051, "did not find lwn at record offset: " + offset);
        }

        int number = byteReader.readUnsignedShort(block, offset + 24);
        int maximum = byteReader.readUnsignedShort(block, offset + 26);
        long blockCount = byteReader.readUnsignedInt(block, offset + 28);
        long length = byteReader.readUnsignedInt(block, offset + 32);
        Scn scn = byteReader.readScn(block, offset + 40);
        RedoTime timestamp = RedoTime.of(byteReader.readUnsignedInt(block, offset + 64));
        return new RedoLwnHeader(recordHeader, number, maximum, blockCount, length, scn, timestamp);
    }
}
