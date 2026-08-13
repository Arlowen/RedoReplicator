/*
 * Java translation derived from OpenLogReplicator src/parser/OpCode0513.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.RedoByteReader;
import io.github.arlowen.redoreplicator.redo.common.RedoFieldCursor;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class OpCode0513 {
    private static final String TRUE = "true";

    protected final RedoByteReader byteReader;
    private final long redoVersion;

    public OpCode0513(RedoByteReader byteReader, long redoVersion) {
        this.byteReader = byteReader;
        this.redoVersion = redoVersion;
    }

    public void process(RedoLogRecord record, Map<Attribute, String> attributes) {
        if (attributes == null) {
            return;
        }

        RedoFieldCursor fields = new RedoFieldCursor(byteReader, record, 0x051301);
        fields.next();
        readSessionSerial(record, fields.fieldPosition(), fields.fieldSize(), attributes);

        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.CURRENT_USER_NAME, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.LOGIN_USER_NAME, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.CLIENT_INFO, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.OS_USER_NAME, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.MACHINE_NAME, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.OS_TERMINAL, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.OS_PROCESS_ID, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.OS_PROGRAM_NAME, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.TRANSACTION_NAME, attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readFlags(record, fields.fieldPosition(), fields.fieldSize(), attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readVersion(record, fields.fieldPosition(), fields.fieldSize(), attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAuditSessionId(record, fields.fieldPosition(), fields.fieldSize(), attributes);
        if (!fields.nextOptional()) {
            return;
        }
        readAttribute(record, fields.fieldPosition(), fields.fieldSize(),
                Attribute.CLIENT_ID, attributes);
    }

    protected final void readAttribute(RedoLogRecord record, int fieldPosition, int fieldSize,
                                       Attribute key, Map<Attribute, String> attributes) {
        String value = new String(record.data(), record.dataOffset() + fieldPosition,
                fieldSize, StandardCharsets.UTF_8);
        if (!value.isEmpty()) {
            attributes.put(key, value);
        }
    }

    protected final void readSessionSerial(RedoLogRecord record, int fieldPosition, int fieldSize,
                                           Map<Attribute, String> attributes) {
        if (fieldSize < 4) {
            return;
        }

        int absolutePosition = record.dataOffset() + fieldPosition;
        int serialNumber = byteReader.readUnsignedShort(record.data(), absolutePosition + 2);
        long sessionNumber;
        if (redoVersion < RedoLogRecord.REDO_VERSION_19_0) {
            sessionNumber = byteReader.readUnsignedShort(record.data(), absolutePosition);
        } else {
            if (fieldSize < 8) {
                return;
            }
            sessionNumber = byteReader.readUnsignedInt(record.data(), absolutePosition + 4);
        }

        attributes.put(Attribute.SESSION_NUMBER, Long.toString(sessionNumber));
        attributes.put(Attribute.SERIAL_NUMBER, Integer.toString(serialNumber));
    }

    protected final void readFlags(RedoLogRecord record, int fieldPosition, int fieldSize,
                                   Map<Attribute, String> attributes) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 6, "5.13.11");
        int absolutePosition = record.dataOffset() + fieldPosition;
        int flags = byteReader.readUnsignedShort(record.data(), absolutePosition);
        readPrimaryFlags(flags, attributes);

        int flags2 = byteReader.readUnsignedShort(record.data(), absolutePosition + 4);
        readSecondaryFlags(flags2, attributes);
    }

    protected final void readVersion(RedoLogRecord record, int fieldPosition, int fieldSize,
                                     Map<Attribute, String> attributes) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 4, "5.13.12");
        long version = byteReader.readUnsignedInt(
                record.data(), record.dataOffset() + fieldPosition);
        attributes.put(Attribute.VERSION, Long.toString(version));
    }

    protected final void readAuditSessionId(RedoLogRecord record, int fieldPosition, int fieldSize,
                                            Map<Attribute, String> attributes) {
        RedoOpCodeSupport.requireSize(record, fieldSize, 4, "5.13.13");
        long auditSessionId = byteReader.readUnsignedInt(
                record.data(), record.dataOffset() + fieldPosition);
        attributes.put(Attribute.AUDIT_SESSION_ID, Long.toString(auditSessionId));
    }

    private static void readPrimaryFlags(int flags, Map<Attribute, String> attributes) {
        if ((flags & 0x0001) != 0) {
            attributes.put(Attribute.DDL_TRANSACTION, TRUE);
        }
        if ((flags & 0x0002) != 0) {
            attributes.put(Attribute.SPACE_MANAGEMENT_TRANSACTION, TRUE);
        }
        if ((flags & 0x0004) != 0) {
            attributes.put(Attribute.RECURSIVE_TRANSACTION, TRUE);
        }
        if ((flags & 0x0008) != 0) {
            attributes.put(Attribute.LOGMINER_INTERNAL_TRANSACTION, TRUE);
        }
        if ((flags & 0x0010) != 0) {
            attributes.put(Attribute.DB_OPEN_IN_MIGRATE_MODE, TRUE);
        }
        if ((flags & 0x0020) != 0) {
            attributes.put(Attribute.LSBY_IGNORE, TRUE);
        }
        if ((flags & 0x0040) != 0) {
            attributes.put(Attribute.LOGMINER_NO_TX_CHUNKING, TRUE);
        }
        if ((flags & 0x0080) != 0) {
            attributes.put(Attribute.LOGMINER_STEALTH_TRANSACTION, TRUE);
        }
        if ((flags & 0x0100) != 0) {
            attributes.put(Attribute.LSBY_PRESERVE, TRUE);
        }
        if ((flags & 0x0200) != 0) {
            attributes.put(Attribute.LOGMINER_MARKER_TRANSACTION, TRUE);
        }
        if ((flags & 0x0400) != 0) {
            attributes.put(Attribute.TRANSACTION_IN_PRAGMAED_PLSQL, TRUE);
        }
        if ((flags & 0x0800) != 0) {
            attributes.put(Attribute.DISABLED_LOGICAL_REPLICATION_TRANSACTION, TRUE);
        }
        if ((flags & 0x1000) != 0) {
            attributes.put(Attribute.DATAPUMP_IMPORT_TRANSACTION, TRUE);
        }
        if ((flags & 0x8000) != 0) {
            attributes.put(Attribute.TRANSACTION_AUDIT_CV_FLAGS_UNDEFINED, TRUE);
        }
    }

    private static void readSecondaryFlags(int flags, Map<Attribute, String> attributes) {
        if ((flags & 0x0001) != 0) {
            attributes.put(Attribute.FEDERATION_PDB_REPLAY, TRUE);
        }
        if ((flags & 0x0002) != 0) {
            attributes.put(Attribute.PDB_DDL_REPLAY, TRUE);
        }
        if ((flags & 0x0004) != 0) {
            attributes.put(Attribute.LOGMINER_SKIP_TRANSACTION, TRUE);
        }
        if ((flags & 0x0008) != 0) {
            attributes.put(Attribute.SEQ_UPDATE_TRANSACTION, TRUE);
        }
    }
}
