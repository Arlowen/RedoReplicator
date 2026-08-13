/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import io.github.arlowen.redoreplicator.redo.parser.ParsedLwn;
import io.github.arlowen.redoreplicator.source.OracleRedoLog;
import io.github.arlowen.redoreplicator.state.RuntimeState;

@FunctionalInterface
interface RedoStatusConsumer {
    void update(
            OracleRedoLog redoLog,
            ParsedLwn lwn,
            RuntimeState runtimeState);
}
