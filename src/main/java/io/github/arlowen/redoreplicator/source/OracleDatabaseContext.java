/*
 * Java translation derived from OpenLogReplicator ReplicatorOnline database metadata.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.state.DatabaseIdentity;

import java.util.Objects;

public record OracleDatabaseContext(DatabaseIdentity identity, Scn currentScn,
                                    String databaseName, String containerName,
                                    String version, boolean containerDatabase,
                                    String logMode, boolean forceLogging,
                                    boolean minimalSupplementalLogging) {
    public OracleDatabaseContext {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(currentScn, "currentScn");
        Objects.requireNonNull(databaseName, "databaseName");
        Objects.requireNonNull(containerName, "containerName");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(logMode, "logMode");
    }

    public void validateSupportedSource() {
        if (!version.startsWith("19.") && !version.startsWith("23.26.")) {
            throw new ConfigurationException(
                    10002,
                    "Unsupported Oracle version " + version
                            + "; RedoReplicator supports Oracle 19c and 26ai Free");
        }
        if (!"ARCHIVELOG".equals(logMode)) {
            throw new ConfigurationException(
                    10003,
                    "Oracle database must use ARCHIVELOG mode");
        }
        if (!forceLogging) {
            throw new ConfigurationException(
                    10004,
                    "Oracle database FORCE LOGGING is not enabled");
        }
        if (!minimalSupplementalLogging) {
            throw new ConfigurationException(
                    10005,
                    "Oracle minimal supplemental logging is not enabled");
        }
    }
}
