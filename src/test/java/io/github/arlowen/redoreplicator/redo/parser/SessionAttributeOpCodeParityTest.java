/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.Attribute;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionAttributeOpCodeParityTest {
    private static final String FIXTURE =
            "/fixtures/redo-header/openlogreplicator-6bc92bc1.properties";
    private final RedoOpCodeDispatcher dispatcher = new RedoOpCodeDispatcher(
            ByteOrder.LITTLE_ENDIAN, RedoLogRecord.REDO_VERSION_19_0);
    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws IOException {
        baseline = new Properties();
        try (InputStream input = SessionAttributeOpCodeParityTest.class
                .getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedSessionAndFlagFields() {
        byte[] session = RedoOpCodeTestSupport.field(8);
        RedoBinaryTestSupport.writeUnsignedShort(
                session, 2, 0x9ABC, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                session, 4, 0xF123_4567L, ByteOrder.LITTLE_ENDIAN);
        byte[] flags = RedoOpCodeTestSupport.field(6);
        RedoBinaryTestSupport.writeUnsignedShort(
                flags, 0, 0x1805, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                flags, 4, 0x0009, ByteOrder.LITTLE_ENDIAN);
        byte[] version = unsignedIntField(0x171A_2000L);
        byte[] auditSessionId = unsignedIntField(0xE123_4567L);
        RedoLogRecord record = RedoOpCodeTestSupport.record(
                0x0513, 0,
                session,
                emptyField(), emptyField(), emptyField(), emptyField(), emptyField(),
                emptyField(), emptyField(), emptyField(), emptyField(),
                flags, version, auditSessionId);
        Map<Attribute, String> attributes = new EnumMap<>(Attribute.class);

        dispatcher.dispatch(record, attributes);

        assertProperty(attributes, Attribute.SESSION_NUMBER, "sessionNumber");
        assertProperty(attributes, Attribute.SERIAL_NUMBER, "serialNumber");
        assertProperty(attributes, Attribute.DDL_TRANSACTION, "ddlTransaction");
        assertProperty(attributes, Attribute.RECURSIVE_TRANSACTION, "recursiveTransaction");
        assertProperty(attributes, Attribute.DISABLED_LOGICAL_REPLICATION_TRANSACTION,
                "disabledLogicalReplication");
        assertProperty(attributes, Attribute.DATAPUMP_IMPORT_TRANSACTION, "datapumpImport");
        assertProperty(attributes, Attribute.FEDERATION_PDB_REPLAY, "federationPdbReplay");
        assertProperty(attributes, Attribute.SEQ_UPDATE_TRANSACTION, "sequenceUpdate");
        assertProperty(attributes, Attribute.VERSION, "version");
        assertProperty(attributes, Attribute.AUDIT_SESSION_ID, "auditSessionId");
    }

    private void assertProperty(Map<Attribute, String> attributes,
                                Attribute attribute, String propertySuffix) {
        assertEquals(baseline.getProperty("opcode0513." + propertySuffix),
                attributes.get(attribute));
    }

    private static byte[] unsignedIntField(long value) {
        byte[] field = RedoOpCodeTestSupport.field(4);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, value, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] emptyField() {
        return RedoOpCodeTestSupport.field(0);
    }
}
