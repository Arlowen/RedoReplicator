/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAttributeOpCodeTest {
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);

    @Test
    void decodesFullSessionInformationRecord() {
        Map<Attribute, String> attributes = new EnumMap<>(Attribute.class);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0513, 0,
                sessionField(0xF123_4567L, 0x9ABC),
                text("CURRENT"),
                text("LOGIN"),
                text("CLIENT-INFO"),
                text("oracle"),
                text("db-host"),
                text("pts/1"),
                text("4242"),
                text("sqlplus"),
                text("batch-load"),
                flagsField(0x1805, 0x0009),
                unsignedIntField(0x171A_2000L),
                unsignedIntField(0xE123_4567L),
                text("client-42"));

        assertTrue(dispatcher.dispatch(record, attributes));

        assertEquals("4045620583", attributes.get(Attribute.SESSION_NUMBER));
        assertEquals("39612", attributes.get(Attribute.SERIAL_NUMBER));
        assertEquals("CURRENT", attributes.get(Attribute.CURRENT_USER_NAME));
        assertEquals("LOGIN", attributes.get(Attribute.LOGIN_USER_NAME));
        assertEquals("CLIENT-INFO", attributes.get(Attribute.CLIENT_INFO));
        assertEquals("oracle", attributes.get(Attribute.OS_USER_NAME));
        assertEquals("db-host", attributes.get(Attribute.MACHINE_NAME));
        assertEquals("pts/1", attributes.get(Attribute.OS_TERMINAL));
        assertEquals("4242", attributes.get(Attribute.OS_PROCESS_ID));
        assertEquals("sqlplus", attributes.get(Attribute.OS_PROGRAM_NAME));
        assertEquals("batch-load", attributes.get(Attribute.TRANSACTION_NAME));
        assertEquals("client-42", attributes.get(Attribute.CLIENT_ID));
        assertEquals("387588096", attributes.get(Attribute.VERSION));
        assertEquals("3777185127", attributes.get(Attribute.AUDIT_SESSION_ID));
        assertEquals("true", attributes.get(Attribute.DDL_TRANSACTION));
        assertEquals("true", attributes.get(Attribute.RECURSIVE_TRANSACTION));
        assertEquals("true", attributes.get(Attribute.DISABLED_LOGICAL_REPLICATION_TRANSACTION));
        assertEquals("true", attributes.get(Attribute.DATAPUMP_IMPORT_TRANSACTION));
        assertEquals("true", attributes.get(Attribute.FEDERATION_PDB_REPLAY));
        assertEquals("true", attributes.get(Attribute.SEQ_UPDATE_TRANSACTION));
    }

    @Test
    void decodesCompactSessionInformationAndSkipsReservedField() {
        Map<Attribute, String> attributes = new EnumMap<>(Attribute.class);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0514, 0,
                sessionField(91, 12),
                text("compact-transaction"),
                flagsField(0x0002, 0x0004),
                unsignedIntField(23),
                unsignedIntField(77),
                text("reserved"),
                text("client-7"),
                text("APP"));

        assertTrue(dispatcher.dispatch(record, attributes));

        assertEquals("91", attributes.get(Attribute.SESSION_NUMBER));
        assertEquals("12", attributes.get(Attribute.SERIAL_NUMBER));
        assertEquals("compact-transaction", attributes.get(Attribute.TRANSACTION_NAME));
        assertEquals("true", attributes.get(Attribute.SPACE_MANAGEMENT_TRANSACTION));
        assertEquals("true", attributes.get(Attribute.LOGMINER_SKIP_TRANSACTION));
        assertEquals("23", attributes.get(Attribute.VERSION));
        assertEquals("77", attributes.get(Attribute.AUDIT_SESSION_ID));
        assertEquals("client-7", attributes.get(Attribute.CLIENT_ID));
        assertEquals("APP", attributes.get(Attribute.LOGIN_USER_NAME));
        assertFalse(attributes.containsValue("reserved"));
    }

    @Test
    void decodesPre19cSessionNumberLayout() {
        RedoOpCodeDispatcher legacyDispatcher = new RedoOpCodeDispatcher(
                ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_18_0);
        byte[] session = RedoOpCodeTestSupport.field(4);
        RedoBinaryTestSupport.writeUnsignedShort(
                session, 0, 0x1234, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                session, 2, 0x5678, ByteOrder.LITTLE_ENDIAN);
        RedoLogRecord record = RedoOpCodeTestSupport.record(0x0513, 0, session);
        Map<Attribute, String> attributes = new EnumMap<>(Attribute.class);

        assertTrue(legacyDispatcher.dispatch(record, attributes));

        assertEquals("4660", attributes.get(Attribute.SESSION_NUMBER));
        assertEquals("22136", attributes.get(Attribute.SERIAL_NUMBER));
    }

    @Test
    void decodesEveryTransactionFlag() {
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0513, 0,
                sessionField(1, 2),
                text(""), text(""), text(""), text(""), text(""),
                text(""), text(""), text(""), text(""),
                flagsField(0x9FFF, 0x000F));
        Map<Attribute, String> attributes = new EnumMap<>(Attribute.class);

        assertTrue(dispatcher.dispatch(record, attributes));

        EnumSet<Attribute> expected = EnumSet.range(
                Attribute.DDL_TRANSACTION, Attribute.SEQ_UPDATE_TRANSACTION);
        expected.add(Attribute.SESSION_NUMBER);
        expected.add(Attribute.SERIAL_NUMBER);
        assertEquals(expected, attributes.keySet());
        for (Attribute attribute : EnumSet.range(
                Attribute.DDL_TRANSACTION, Attribute.SEQ_UPDATE_TRANSACTION)) {
            assertEquals("true", attributes.get(attribute));
        }
    }

    @Test
    void ignoresAttributesWithoutTransactionAndRejectsTruncatedFlags() {
        RedoLogRecord withoutTransaction = RedoOpCodeTestSupport.record(0x0513, 0);
        assertTrue(dispatcher.dispatch(withoutTransaction));

        RedoLogRecord truncated = RedoOpCodeTestSupport.record(
                0x0513, 0,
                sessionField(1, 2),
                text(""), text(""), text(""), text(""), text(""),
                text(""), text(""), text(""), text(""),
                RedoOpCodeTestSupport.field(2));
        Map<Attribute, String> attributes = new EnumMap<>(Attribute.class);
        RedoLogException error = assertThrows(
                RedoLogException.class, () -> dispatcher.dispatch(truncated, attributes));
        assertEquals(50061, error.getErrorCode());
    }

    private static byte[] sessionField(long sessionNumber, int serialNumber) {
        byte[] field = RedoOpCodeTestSupport.field(8);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 2, serialNumber, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, sessionNumber, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] flagsField(int flags, int flags2) {
        byte[] field = RedoOpCodeTestSupport.field(6);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 0, flags, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 4, flags2, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] unsignedIntField(long value) {
        byte[] field = RedoOpCodeTestSupport.field(4);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, value, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
