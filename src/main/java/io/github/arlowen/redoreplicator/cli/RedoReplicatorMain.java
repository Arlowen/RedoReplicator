/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.cli;

import picocli.CommandLine;

public final class RedoReplicatorMain {
    private RedoReplicatorMain() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(
                new RedoReplicatorCommand()).execute(args);
        System.exit(exitCode);
    }
}
