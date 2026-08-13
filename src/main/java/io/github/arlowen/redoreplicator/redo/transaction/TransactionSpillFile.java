/*
 * Java translation derived from OpenLogReplicator TransactionChunk swapped
 * memory behavior in common/MemoryManager.cpp and parser/TransactionBuffer.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.TransactionSpillException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.LobId;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.common.Xid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

final class TransactionSpillFile {
    private static final Logger log = LoggerFactory.getLogger(
            TransactionSpillFile.class);
    private static final int MAGIC = 0x52525350;
    private static final int VERSION = 1;
    private static final int HEADER_SIZE = 8;
    private static final int LENGTH_SIZE = 4;

    private final Path path;
    private final FileChannel channel;
    private int entryCount;

    TransactionSpillFile(Path path) throws IOException {
        this.path = path;
        channel = FileChannel.open(path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        header.putInt(MAGIC);
        header.putInt(VERSION);
        header.flip();
        writeFully(header, 0);
    }

    void append(RedoTransactionEntry entry) {
        try {
            byte[] payload = encode(entry);
            long position = channel.size();
            ByteBuffer length = ByteBuffer.allocate(LENGTH_SIZE);
            length.putInt(payload.length);
            length.flip();
            writeFully(length, position);
            writeFully(ByteBuffer.wrap(payload), position + LENGTH_SIZE);
            length.rewind();
            writeFully(length,
                    position + LENGTH_SIZE + payload.length);
            entryCount++;
        } catch (IOException e) {
            throw failure("Failed to append transaction spill " + path, e);
        }
    }

    RedoTransactionEntry readLast() {
        try {
            long entryStart = lastEntryStart();
            int length = readLength(entryStart);
            return decode(readBytes(entryStart + LENGTH_SIZE, length));
        } catch (IOException e) {
            throw failure("Failed to read transaction spill tail " + path, e);
        }
    }

    void removeLast() {
        try {
            channel.truncate(lastEntryStart());
            entryCount--;
        } catch (IOException e) {
            throw failure("Failed to truncate transaction spill " + path, e);
        }
    }

    List<RedoTransactionEntry> readAll() {
        try {
            validateHeader();
            long size = channel.size();
            long position = HEADER_SIZE;
            List<RedoTransactionEntry> entries = new ArrayList<>(entryCount);
            while (position < size) {
                int length = readLength(position);
                long footerPosition = position + LENGTH_SIZE + length;
                if (footerPosition > size - LENGTH_SIZE) {
                    throw new IOException("Incomplete transaction spill entry");
                }
                if (readLength(footerPosition) != length) {
                    throw new IOException("Transaction spill length does not match");
                }
                entries.add(decode(readBytes(
                        position + LENGTH_SIZE, length)));
                position = footerPosition + LENGTH_SIZE;
            }
            if (entries.size() != entryCount) {
                throw new IOException("Transaction spill entry count does not match");
            }
            return List.copyOf(entries);
        } catch (IOException e) {
            throw failure("Failed to read transaction spill " + path, e);
        }
    }

    void delete() {
        try {
            channel.close();
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw failure("Failed to delete transaction spill " + path, e);
        }
    }

    Path path() {
        return path;
    }

    private long lastEntryStart() throws IOException {
        if (entryCount == 0) {
            throw new IOException("Transaction spill is empty");
        }
        long size = channel.size();
        if (size < HEADER_SIZE + LENGTH_SIZE * 2L) {
            throw new IOException("Transaction spill tail is incomplete");
        }
        int length = readLength(size - LENGTH_SIZE);
        long entryStart = size - LENGTH_SIZE - length - LENGTH_SIZE;
        if (length < 0 || entryStart < HEADER_SIZE
                || readLength(entryStart) != length) {
            throw new IOException("Transaction spill tail length does not match");
        }
        return entryStart;
    }

    private void validateHeader() throws IOException {
        if (channel.size() < HEADER_SIZE
                || readInt(0) != MAGIC || readInt(4) != VERSION) {
            throw new IOException("Invalid transaction spill header");
        }
    }

    private int readLength(long position) throws IOException {
        int length = readInt(position);
        if (length < 0) {
            throw new IOException("Negative transaction spill entry length");
        }
        return length;
    }

    private int readInt(long position) throws IOException {
        ByteBuffer value = ByteBuffer.allocate(Integer.BYTES);
        readFully(value, position);
        value.flip();
        return value.getInt();
    }

    private byte[] readBytes(long position, int length) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(length);
        readFully(data, position);
        return data.array();
    }

    private void writeFully(ByteBuffer buffer, long position)
            throws IOException {
        while (buffer.hasRemaining()) {
            position += channel.write(buffer, position);
        }
    }

    private void readFully(ByteBuffer buffer, long position)
            throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) {
                throw new IOException("Unexpected end of transaction spill");
            }
            position += read;
        }
    }

    private static byte[] encode(RedoTransactionEntry entry)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(entry);
        }
        return bytes.toByteArray();
    }

    private static RedoTransactionEntry decode(byte[] payload)
            throws IOException {
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(payload))) {
            input.setObjectInputFilter(TransactionSpillFile::filter);
            Object value = input.readObject();
            if (!(value instanceof RedoTransactionEntry entry)) {
                throw new IOException("Unexpected transaction spill value");
            }
            return entry;
        } catch (ClassNotFoundException e) {
            throw new IOException("Transaction spill class is unavailable", e);
        }
    }

    private static ObjectInputFilter.Status filter(
            ObjectInputFilter.FilterInfo info) {
        Class<?> type = info.serialClass();
        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        boolean allowed = type == RedoTransactionEntry.class
                || type == RedoLogRecord.class
                || type == FileOffset.class
                || type == LobId.class
                || type == RedoTime.class
                || type == Scn.class
                || type == Seq.class
                || type == Xid.class
                || type == byte[].class;
        if (allowed) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        return ObjectInputFilter.Status.REJECTED;
    }

    private static TransactionSpillException failure(
            String message, IOException cause) {
        log.error(message, cause);
        return new TransactionSpillException(message, cause);
    }
}
