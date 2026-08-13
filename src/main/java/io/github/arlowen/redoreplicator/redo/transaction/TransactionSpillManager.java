/*
 * Java translation derived from OpenLogReplicator common/MemoryManager.cpp
 * swapped transaction memory lifecycle.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.TransactionSpillException;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class TransactionSpillManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(
            TransactionSpillManager.class);
    private static final String PREFIX = "transaction-";
    private static final String SUFFIX = ".spill";

    private final Path directory;
    private final Map<Path, TransactionSpillFile> files;

    public TransactionSpillManager(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        files = new LinkedHashMap<>();
        try {
            Files.createDirectories(this.directory);
            try (Stream<Path> children = Files.list(this.directory)) {
                for (Path child : children.toList()) {
                    String name = child.getFileName().toString();
                    if (name.startsWith(PREFIX) && name.endsWith(SUFFIX)) {
                        Files.delete(child);
                    }
                }
            }
        } catch (IOException e) {
            throw failure("Failed to prepare transaction spill directory "
                    + this.directory, e);
        }
    }

    public Path directory() {
        return directory;
    }

    TransactionSpillFile open(int containerId, Xid xid) {
        Path path = directory.resolve(PREFIX
                + Integer.toUnsignedString(containerId) + "-"
                + Long.toUnsignedString(xid.rawValue(), 16) + SUFFIX);
        TransactionSpillFile existing = files.get(path);
        if (existing != null) {
            return existing;
        }
        try {
            TransactionSpillFile file = new TransactionSpillFile(path);
            files.put(path, file);
            return file;
        } catch (IOException e) {
            throw failure("Failed to create transaction spill " + path, e);
        }
    }

    void release(TransactionSpillFile file) {
        file.delete();
        files.remove(file.path());
    }

    @Override
    public void close() {
        for (TransactionSpillFile file
                : new ArrayList<>(files.values())) {
            release(file);
        }
    }

    private static TransactionSpillException failure(
            String message, IOException cause) {
        log.error(message, cause);
        return new TransactionSpillException(message, cause);
    }
}
