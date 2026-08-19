/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LobSchemaParityTest {
    private static final String FIXTURE =
            "/fixtures/value-types/openlogreplicator-6bc92bc1.properties";

    private Properties baseline;

    @BeforeEach
    void loadBaseline() throws Exception {
        baseline = new Properties();
        try (InputStream input = getClass().getResourceAsStream(FIXTURE)) {
            assertNotNull(input);
            baseline.load(input);
        }
    }

    @Test
    void matchesPinnedLobMetadataBehavior() {
        LobSchema lob = new LobSchema(
                100, 101, 200, 2, 3,
                List.of(301L), List.of(new LobPartition(401, 16_264)));

        assertEquals(baseline.getProperty("dblob.formatted"),
                lob.toString());
        assertEquals(baseline.getProperty("dblob.defaultPageSize"),
                Integer.toString(lob.pageSize(999)));
        assertEquals(baseline.getProperty("dblob.partitionPageSize"),
                Integer.toString(lob.pageSize(401)));
        assertEquals(baseline.getProperty("dblob.indexCount"),
                Integer.toString(lob.indexDataObjectIds().size()));
        assertEquals(baseline.getProperty("dblob.partitionCount"),
                Integer.toString(lob.partitions().size()));
    }
}
