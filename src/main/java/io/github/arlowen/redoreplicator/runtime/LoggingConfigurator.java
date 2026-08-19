/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingConfigurator {
    private LoggingConfigurator() {
    }

    public static void apply(String level) {
        LoggerContext context = (LoggerContext) LoggerFactory
                .getILoggerFactory();
        context.getLogger(Logger.ROOT_LOGGER_NAME)
                .setLevel(Level.valueOf(level));
    }
}
