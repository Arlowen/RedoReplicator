/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.redo.common.FileOffset;

import java.util.List;
import java.util.Objects;

public record RedoReadBatch(
        RedoReadStatus status,
        FileOffset startOffset,
        List<byte[]> blocks) {
    public RedoReadBatch {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startOffset, "startOffset");
        blocks = List.copyOf(blocks);
        if (status == RedoReadStatus.DATA && blocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "DATA redo batch must contain blocks");
        }
        if (status != RedoReadStatus.DATA && !blocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Only DATA redo batch can contain blocks");
        }
    }
}
