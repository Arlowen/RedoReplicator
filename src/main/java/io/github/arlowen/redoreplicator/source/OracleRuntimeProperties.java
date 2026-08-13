/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.Objects;

public record OracleRuntimeProperties(
        long databaseCharacterSetId,
        long nationalCharacterSetId,
        Charset databaseCharacterSet,
        ZoneId databaseTimeZone) {
    public OracleRuntimeProperties {
        Objects.requireNonNull(databaseCharacterSet, "databaseCharacterSet");
        Objects.requireNonNull(databaseTimeZone, "databaseTimeZone");
    }
}
