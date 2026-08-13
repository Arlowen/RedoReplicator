/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import java.io.IOException;
import java.sql.SQLException;

@FunctionalInterface
interface RedoBatchSource {
    RedoThreadBatch read() throws IOException, SQLException;
}
