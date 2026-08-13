/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.testkit;

import java.nio.file.Path;
import java.util.List;

public final class JsonlComparatorMain {
    private JsonlComparatorMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: JsonlComparatorMain <expected.jsonl> <actual.jsonl>");
            System.exit(2);
        }

        Path expected = Path.of(args[0]);
        Path actual = Path.of(args[1]);
        List<String> differences = JsonlComparator.compare(expected, actual);
        if (!differences.isEmpty()) {
            for (String difference : differences) {
                System.err.println(difference);
            }
            System.exit(1);
        }

        System.out.println("JSONL output is structurally equal");
    }
}
