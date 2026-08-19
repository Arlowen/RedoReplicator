/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.runtime;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfiguratorTest {
    @Test
    void appliesConfiguredRootLevel() {
        LoggerContext context = (LoggerContext) LoggerFactory
                .getILoggerFactory();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Level previous = root.getLevel();
        try {
            LoggingConfigurator.apply("DEBUG");
            assertEquals(Level.DEBUG, root.getLevel());
        } finally {
            root.setLevel(previous);
        }
    }

    @Test
    void keepsTenCompressedOneHundredMegabyteFiles() throws Exception {
        String foreground = Files.readString(Path.of("conf/logback.xml"));
        String background = Files.readString(
                Path.of("conf/logback-background.xml"));

        for (String configuration : new String[]{foreground, background}) {
            assertTrue(configuration.contains("<maxFileSize>100MB</maxFileSize>"));
            assertTrue(configuration.contains("<maxIndex>10</maxIndex>"));
            assertTrue(configuration.contains("%i.log.gz"));
        }
        assertTrue(foreground.contains("ConsoleAppender"));
        assertFalse(background.contains("ConsoleAppender"));
    }
}
