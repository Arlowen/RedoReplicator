/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.error.BootException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class RuntimeLock implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(RuntimeLock.class);
    private static final String LOCK_FILE_NAME = "redo-replicator.lock";

    private final FileChannel channel;
    private final FileLock lock;

    private RuntimeLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static RuntimeLock acquire(Path stateDirectory) {
        Path directory = stateDirectory.toAbsolutePath().normalize();
        Path lockFile = directory.resolve(LOCK_FILE_NAME);
        FileChannel channel = null;
        try {
            Files.createDirectories(directory);
            channel = FileChannel.open(
                    lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                lock = null;
            }
            if (lock == null) {
                channel.close();
                throw new BootException(10010,
                        "Another RedoReplicator process holds " + lockFile);
            }
            writeProcessId(channel);
            return new RuntimeLock(channel, lock);
        } catch (IOException e) {
            closeAfterFailure(channel, e);
            String msg = "Failed to acquire RedoReplicator runtime lock "
                    + lockFile;
            log.error(msg, e);
            throw new BootException(10010, msg);
        }
    }

    @Override
    public void close() {
        IOException failure = null;
        try {
            channel.truncate(0);
            channel.force(true);
        } catch (IOException e) {
            failure = e;
        }
        try {
            lock.release();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            channel.close();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            log.error("Failed to release RedoReplicator runtime lock", failure);
        }
    }

    private static void writeProcessId(FileChannel channel) throws IOException {
        byte[] processId = (ProcessHandle.current().pid() + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8);
        channel.truncate(0);
        channel.position(0);
        ByteBuffer buffer = ByteBuffer.wrap(processId);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        channel.force(true);
    }

    private static void closeAfterFailure(
            FileChannel channel, IOException failure) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException closeError) {
            failure.addSuppressed(closeError);
        }
    }
}
