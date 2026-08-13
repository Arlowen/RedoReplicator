/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OracleDatabaseContextTest {
    @Test
    void acceptsOracle19cAnd26aiFree() {
        assertDoesNotThrow(() -> context("19.25.0.0.0").validateSupportedSource());
        assertDoesNotThrow(() -> context("23.26.0.0.0").validateSupportedSource());
    }

    @Test
    void rejectsUnsupportedVersionAndMissingLoggingSettings() {
        ConfigurationException version = assertThrows(
                ConfigurationException.class,
                () -> context("21.3.0.0.0").validateSupportedSource());
        assertEquals(10002, version.getErrorCode());

        OracleDatabaseContext noArchive = new OracleDatabaseContext(
                new DatabaseIdentity(1, 2, 3),
                Scn.of(4),
                "FREE",
                "FREEPDB1",
                "23.26.0.0.0",
                true,
                "NOARCHIVELOG",
                true,
                true);
        assertEquals(10003, assertThrows(
                ConfigurationException.class,
                noArchive::validateSupportedSource).getErrorCode());
    }

    @Test
    void rejectsMissingForceAndSupplementalLoggingIndependently() {
        OracleDatabaseContext noForce = new OracleDatabaseContext(
                new DatabaseIdentity(1, 2, 3), Scn.of(4),
                "FREE", "FREEPDB1", "23.26.0.0.0", true,
                "ARCHIVELOG", false, true);
        OracleDatabaseContext noSupplemental = new OracleDatabaseContext(
                new DatabaseIdentity(1, 2, 3), Scn.of(4),
                "FREE", "FREEPDB1", "23.26.0.0.0", true,
                "ARCHIVELOG", true, false);

        assertEquals(10004, assertThrows(
                ConfigurationException.class,
                noForce::validateSupportedSource).getErrorCode());
        assertEquals(10005, assertThrows(
                ConfigurationException.class,
                noSupplemental::validateSupportedSource).getErrorCode());
    }

    private static OracleDatabaseContext context(String version) {
        return new OracleDatabaseContext(
                new DatabaseIdentity(1, 2, 3),
                Scn.of(4),
                "FREE",
                "FREEPDB1",
                version,
                true,
                "ARCHIVELOG",
                true,
                true);
    }
}
