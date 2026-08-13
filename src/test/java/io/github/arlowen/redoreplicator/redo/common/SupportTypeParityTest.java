/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import io.github.arlowen.redoreplicator.error.BootException;
import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.error.RedoRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SupportTypeParityTest {
    private static final String FIXTURE =
            "/fixtures/value-types/openlogreplicator-6bc92bc1.properties";
    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws IOException {
        baseline = new Properties();
        try (InputStream input = SupportTypeParityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(input, "Missing parity fixture " + FIXTURE);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedAttributeAndIncarnationOutput() {
        assertEquals(baseline.getProperty("attribute.count"), Integer.toString(Attribute.values().length));
        assertEquals(baseline.getProperty("attribute.first"), Attribute.VERSION.toString());
        assertEquals(baseline.getProperty("attribute.last"), Attribute.SEQ_UPDATE_TRANSACTION.toString());
        assertEquals(baseline.getProperty("attribute.reverse"),
                Integer.toString(Attribute.fromString().get("client id").ordinal()));

        DbIncarnation incarnation = new DbIncarnation(
                0xFFFF_FFFFL, Scn.of(100), Scn.of(50), "CURRENT", 200, 0xFFFF_FFFEL);
        assertEquals(baseline.getProperty("incarnation.formatted"), incarnation.toString());
    }

    @Test
    void matchesPinnedExceptionOutput() {
        BootException boot = new BootException(10001, "boot failed");
        ConfigurationException configuration = new ConfigurationException(10002, "configuration failed");
        RedoLogException redo = new RedoLogException(10003, "redo failed");
        RedoRuntimeException runtime = new RedoRuntimeException(10004, "runtime failed", 55);

        assertEquals(baseline.getProperty("exception.boot"), boot.getErrorCode() + ":" + boot.getMessage());
        assertEquals(baseline.getProperty("exception.configuration"),
                configuration.getErrorCode() + ":" + configuration.getMessage());
        assertEquals(baseline.getProperty("exception.redo"), redo.getErrorCode() + ":" + redo.getMessage());
        assertEquals(baseline.getProperty("exception.runtime"), runtime.getErrorCode() + ":"
                + runtime.getSupplementalCode() + ":" + runtime.getMessage());
    }
}
