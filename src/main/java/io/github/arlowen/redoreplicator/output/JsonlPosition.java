/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

public record JsonlPosition(long fileNumber, long fsyncOffset) {
    public JsonlPosition {
        if (fileNumber <= 0) {
            throw new IllegalArgumentException(
                    "JSONL file number must be positive");
        }
        if (fsyncOffset < 0) {
            throw new IllegalArgumentException(
                    "JSONL fsync offset must not be negative");
        }
    }
}
