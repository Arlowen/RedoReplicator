/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.state.TableSchemaVersion;

import java.util.Objects;
import java.util.Optional;

public record SchemaResolution(SchemaResolutionStatus status,
                               Optional<TableSchemaVersion> version,
                               String detail) {
    public SchemaResolution {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(detail, "detail");
        if (status == SchemaResolutionStatus.PRESENT && version.isEmpty()) {
            throw new IllegalArgumentException("A present schema requires a version");
        }
    }

    public static SchemaResolution present(TableSchemaVersion version) {
        return new SchemaResolution(
                SchemaResolutionStatus.PRESENT,
                Optional.of(version),
                "Complete table schema is available");
    }

    public static SchemaResolution notPresent(Optional<TableSchemaVersion> version,
                                              String detail) {
        return new SchemaResolution(
                SchemaResolutionStatus.NOT_PRESENT,
                version,
                detail);
    }

    public static SchemaResolution unproven(String detail) {
        return new SchemaResolution(
                SchemaResolutionStatus.UNPROVEN,
                Optional.empty(),
                detail);
    }

    public SchemaResolution requireProven() {
        if (status == SchemaResolutionStatus.UNPROVEN) {
            throw new DataException(50071, detail);
        }
        return this;
    }
}
