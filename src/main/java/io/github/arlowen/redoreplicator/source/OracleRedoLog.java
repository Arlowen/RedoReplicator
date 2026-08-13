/*
 * Java translation derived from OpenLogReplicator online archive discovery in
 * src/replicator/ReplicatorOnline.cpp and ReplicatorOnline.h.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.source;

import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public record OracleRedoLog(
        OracleRedoLogKind kind,
        int thread,
        Seq sequence,
        Scn firstScn,
        Scn nextScn,
        String status,
        String oraclePath,
        Path localPath) {
    public OracleRedoLog {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(firstScn, "firstScn");
        Objects.requireNonNull(nextScn, "nextScn");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(oraclePath, "oraclePath");
        Objects.requireNonNull(localPath, "localPath");
    }

    public boolean covers(Scn scn) {
        return firstScn.compareTo(scn) <= 0 && nextScn.compareTo(scn) > 0;
    }

    public boolean isReadable() {
        if (!Files.isRegularFile(localPath)) {
            return false;
        }
        try (SeekableByteChannel ignored = Files.newByteChannel(
                localPath, StandardOpenOption.READ)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
