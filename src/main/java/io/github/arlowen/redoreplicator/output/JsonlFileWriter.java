/*
 * Java translation derived from OpenLogReplicator file output in
 * src/writer/WriterFile.cpp and src/writer/WriterFile.h.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class JsonlFileWriter implements AutoCloseable {
    private static final Pattern OUTPUT_FILE = Pattern.compile(
            "redo-(\\d+)\\.jsonl");
    private static final byte[] NEW_LINE = {(byte) '\n'};

    private final Path outputDirectory;
    private final long maximumFileBytes;

    private long fileNumber;
    private long fileOffset;
    private FileChannel channel;
    private boolean closed;

    private JsonlFileWriter(
            Path outputDirectory, long maximumFileBytes) {
        this.outputDirectory = outputDirectory;
        this.maximumFileBytes = maximumFileBytes;
    }

    public static JsonlFileWriter open(
            Path outputDirectory,
            long maximumFileBytes,
            Optional<JsonlPosition> durablePosition) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(durablePosition, "durablePosition");
        if (maximumFileBytes <= 0) {
            throw new IllegalArgumentException(
                    "maximumFileBytes must be positive");
        }
        Path directory = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        JsonlFileWriter writer = new JsonlFileWriter(
                directory, maximumFileBytes);
        writer.initialize(durablePosition);
        return writer;
    }

    public JsonlPosition writeAndSync(List<byte[]> messages)
            throws IOException {
        requireOpen();
        Objects.requireNonNull(messages, "messages");
        for (byte[] message : messages) {
            validateMessage(message);
            long lineBytes = message.length + 1L;
            if (fileOffset > 0
                    && fileOffset + lineBytes > maximumFileBytes) {
                rotate();
            }
            write(message);
            write(NEW_LINE);
            fileOffset += lineBytes;
        }
        channel.force(true);
        return position();
    }

    public JsonlPosition position() {
        requireOpen();
        return new JsonlPosition(fileNumber, fileOffset);
    }

    public Path currentFile() {
        requireOpen();
        return outputFile(fileNumber);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        channel.close();
    }

    private void initialize(Optional<JsonlPosition> durablePosition)
            throws IOException {
        long highestFileNumber = findHighestFileNumber();
        if (durablePosition.isEmpty()) {
            fileNumber = highestFileNumber + 1;
            if (fileNumber == 0) {
                fileNumber = 1;
            }
            openCurrentFile(0);
            return;
        }

        JsonlPosition durable = durablePosition.orElseThrow();
        restoreDurableFile(durable);
        if (highestFileNumber > durable.fileNumber()) {
            fileNumber = highestFileNumber + 1;
            openCurrentFile(0);
            return;
        }
        fileNumber = durable.fileNumber();
        openCurrentFile(durable.fsyncOffset());
    }

    private void restoreDurableFile(JsonlPosition durable)
            throws IOException {
        Path durableFile = outputFile(durable.fileNumber());
        if (!Files.exists(durableFile)) {
            if (durable.fsyncOffset() > 0) {
                throw new IOException(
                        "JSONL durable file is missing: " + durableFile);
            }
            return;
        }
        long size = Files.size(durableFile);
        if (size < durable.fsyncOffset()) {
            throw new IOException(
                    "JSONL file " + durableFile + " has size " + size
                            + " below durable offset "
                            + durable.fsyncOffset());
        }
        if (size > durable.fsyncOffset()) {
            try (FileChannel durableChannel = FileChannel.open(
                    durableFile, StandardOpenOption.WRITE)) {
                durableChannel.truncate(durable.fsyncOffset());
                durableChannel.force(true);
            }
        }
    }

    private long findHighestFileNumber() throws IOException {
        long highest = 0;
        try (Stream<Path> paths = Files.list(outputDirectory)) {
            for (Path path : paths.toList()) {
                Matcher matcher = OUTPUT_FILE.matcher(
                        path.getFileName().toString());
                if (!matcher.matches() || !Files.isRegularFile(path)) {
                    continue;
                }
                long candidate;
                try {
                    candidate = Long.parseLong(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (candidate > highest) {
                    highest = candidate;
                }
            }
        }
        return highest;
    }

    private void rotate() throws IOException {
        channel.force(true);
        channel.close();
        fileNumber++;
        openCurrentFile(0);
    }

    private void openCurrentFile(long offset) throws IOException {
        Path file = outputFile(fileNumber);
        channel = FileChannel.open(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        channel.position(offset);
        fileOffset = offset;
    }

    private void write(byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private Path outputFile(long number) {
        return outputDirectory.resolve(
                "redo-" + String.format("%06d", number) + ".jsonl");
    }

    private static void validateMessage(byte[] message) {
        Objects.requireNonNull(message, "message");
        if (message.length == 0) {
            throw new IllegalArgumentException(
                    "JSONL message must not be empty");
        }
        for (byte value : message) {
            if (value == '\n' || value == '\r') {
                throw new IllegalArgumentException(
                        "JSONL message contains a raw line break");
            }
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("JSONL writer is closed");
        }
    }
}
