/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.testkit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JsonlComparator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonlComparator() {
    }

    public static List<String> compare(Path expectedPath, Path actualPath) throws IOException {
        List<String> differences = new ArrayList<>();

        try (BufferedReader expectedReader = Files.newBufferedReader(expectedPath, StandardCharsets.UTF_8);
             BufferedReader actualReader = Files.newBufferedReader(actualPath, StandardCharsets.UTF_8)) {
            long lineNumber = 1;
            while (true) {
                String expectedLine = expectedReader.readLine();
                String actualLine = actualReader.readLine();

                if (expectedLine == null && actualLine == null) {
                    break;
                }
                if (expectedLine == null) {
                    differences.add("Actual output has extra content starting at line " + lineNumber);
                    break;
                }
                if (actualLine == null) {
                    differences.add("Actual output ended before expected line " + lineNumber);
                    break;
                }

                JsonNode expected = parse(expectedPath, lineNumber, expectedLine);
                JsonNode actual = parse(actualPath, lineNumber, actualLine);
                if (!expected.equals(actual)) {
                    differences.add("JSON differs at line " + lineNumber
                            + System.lineSeparator() + "expected: " + expected
                            + System.lineSeparator() + "actual:   " + actual);
                }
                lineNumber++;
            }
        }

        return differences;
    }

    private static JsonNode parse(Path path, long lineNumber, String line) throws IOException {
        if (line.isBlank()) {
            throw new IOException(path + " contains a blank line at " + lineNumber);
        }

        try {
            JsonNode node = OBJECT_MAPPER.readTree(line);
            if (!node.isObject()) {
                throw new IOException(path + " must contain a JSON object at line " + lineNumber);
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new IOException(path + " contains invalid JSON at line " + lineNumber, e);
        }
    }
}
