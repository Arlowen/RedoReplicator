/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.charset.CharacterSet;
import io.github.arlowen.redoreplicator.charset.Locales;

import java.time.ZoneId;
import java.util.Objects;

public record OracleRuntimeProperties(
        long databaseCharacterSetId,
        long nationalCharacterSetId,
        CharacterSet databaseCharacterSet,
        Locales locales,
        ZoneId databaseTimeZone) {
    public OracleRuntimeProperties {
        Objects.requireNonNull(databaseCharacterSet, "databaseCharacterSet");
        Objects.requireNonNull(locales, "locales");
        Objects.requireNonNull(databaseTimeZone, "databaseTimeZone");
    }
}
