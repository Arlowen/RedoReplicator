/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.state;

import io.github.arlowen.redoreplicator.error.ConfigurationException;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StateStore {
    private static final Logger log = LoggerFactory.getLogger(StateStore.class);

    private final Connection connection;

    StateStore(Connection connection) {
        this.connection = connection;
    }

    public Optional<RuntimeState> loadRuntimeState() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM runtime_state WHERE id = 1");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            RedoPosition durable = readPosition(resultSet, "durable_");
            Optional<RedoPosition> lowWatermark = Optional.empty();
            if (resultSet.getBigDecimal("low_watermark_scn") != null) {
                lowWatermark = Optional.of(readPosition(resultSet, "low_watermark_"));
            }
            return Optional.of(new RuntimeState(
                    resultSet.getLong("database_id"),
                    resultSet.getLong("incarnation"),
                    resultSet.getLong("resetlogs_id"),
                    durable,
                    lowWatermark,
                    resultSet.getLong("jsonl_file_number"),
                    resultSet.getLong("jsonl_fsync_offset"),
                    resultSet.getString("config_fingerprint"),
                    resultSet.getObject("updated_at", OffsetDateTime.class)));
        }
    }

    public Optional<TableSchemaVersion> findSchemaAt(String container, String owner,
                                                     String table, Scn targetScn)
            throws SQLException {
        String sql = "SELECT * FROM table_schema_history"
                + " WHERE container_name = ? AND owner_name = ? AND table_name = ?"
                + " AND effective_scn <= ?"
                + " ORDER BY effective_scn DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, container);
            statement.setString(2, owner);
            statement.setString(3, table);
            statement.setBigDecimal(4, unsigned(targetScn.rawValue()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readSchemaVersion(resultSet));
            }
        }
    }

    public List<TableSchemaVersion> findSchemasAt(Scn targetScn)
            throws SQLException {
        String sql = "SELECT * FROM ("
                + " SELECT H.*, ROW_NUMBER() OVER ("
                + " PARTITION BY container_name, owner_name, table_name"
                + " ORDER BY effective_scn DESC, id DESC) AS version_rank"
                + " FROM table_schema_history H WHERE effective_scn <= ?"
                + ") WHERE version_rank = 1"
                + " ORDER BY container_name, owner_name, table_name";
        List<TableSchemaVersion> versions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, unsigned(targetScn.rawValue()));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    versions.add(readSchemaVersion(resultSet));
                }
            }
        }
        return List.copyOf(versions);
    }

    public void validateDatabaseIdentity(DatabaseIdentity identity) throws SQLException {
        Optional<RuntimeState> persistedState = loadRuntimeState();
        if (persistedState.isEmpty()) {
            return;
        }
        RuntimeState state = persistedState.get();
        if (state.databaseId() != identity.databaseId()
                || state.incarnation() != identity.incarnation()
                || state.resetlogsId() != identity.resetlogsId()) {
            throw new ConfigurationException(10001,
                    "State database belongs to a different Oracle database incarnation");
        }
    }

    public void commitLwn(RuntimeState runtimeState,
                          List<TableSchemaVersion> schemaVersions) throws SQLException {
        commit(runtimeState, schemaVersions, null);
    }

    public void rewind(RuntimeState runtimeState,
                       List<TableSchemaVersion> schemaVersions,
                       Scn targetScn) throws SQLException {
        commit(runtimeState, schemaVersions, targetScn);
    }

    private void commit(RuntimeState runtimeState,
                        List<TableSchemaVersion> schemaVersions,
                        Scn discardSchemasAfter) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (discardSchemasAfter != null) {
                deleteSchemaVersionsAfter(discardSchemasAfter);
            }
            for (TableSchemaVersion schemaVersion : schemaVersions) {
                insertSchemaVersion(schemaVersion);
            }
            saveRuntimeState(runtimeState);
            connection.commit();
        } catch (SQLException e) {
            String msg = "Failed to commit LWN state";
            log.error(msg, e);
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private void deleteSchemaVersionsAfter(Scn targetScn)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM table_schema_history WHERE effective_scn > ?")) {
            statement.setBigDecimal(1, unsigned(targetScn.rawValue()));
            statement.executeUpdate();
        }
    }

    private void saveRuntimeState(RuntimeState state) throws SQLException {
        String sql = "MERGE INTO runtime_state ("
                + "id, database_id, incarnation, resetlogs_id,"
                + "durable_scn, durable_thread, durable_sequence, durable_offset,"
                + "low_watermark_scn, low_watermark_thread,"
                + "low_watermark_sequence, low_watermark_offset,"
                + "jsonl_file_number, jsonl_fsync_offset, config_fingerprint, updated_at"
                + ") KEY(id) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, state.databaseId());
            statement.setLong(2, state.incarnation());
            statement.setLong(3, state.resetlogsId());
            writePosition(statement, 4, state.durablePosition());
            if (state.lowWatermarkPosition().isPresent()) {
                writePosition(statement, 8, state.lowWatermarkPosition().get());
            } else {
                statement.setNull(8, Types.NUMERIC);
                statement.setNull(9, Types.INTEGER);
                statement.setNull(10, Types.BIGINT);
                statement.setNull(11, Types.NUMERIC);
            }
            statement.setLong(12, state.jsonlFileNumber());
            statement.setLong(13, state.jsonlFsyncOffset());
            statement.setString(14, state.configFingerprint());
            statement.setObject(15, state.updatedAt());
            statement.executeUpdate();
        }
    }

    private void insertSchemaVersion(TableSchemaVersion version) throws SQLException {
        String sql = "INSERT INTO table_schema_history ("
                + "container_name, owner_name, table_name, object_id, data_object_id,"
                + "effective_scn, schema_json, ddl_type, ddl_text, source, drop_tombstone"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, version.container());
            statement.setString(2, version.owner());
            statement.setString(3, version.table());
            statement.setLong(4, version.objectId());
            statement.setLong(5, version.dataObjectId());
            statement.setBigDecimal(6, unsigned(version.effectiveScn().rawValue()));
            statement.setString(7, version.schemaJson());
            statement.setString(8, version.ddlType());
            statement.setString(9, version.ddlText());
            statement.setString(10, version.source().databaseValue());
            statement.setBoolean(11, version.dropTombstone());
            statement.executeUpdate();
        }
    }

    private static RedoPosition readPosition(ResultSet resultSet, String prefix)
            throws SQLException {
        Scn scn = Scn.of(parseUnsigned(resultSet.getBigDecimal(prefix + "scn")));
        int thread = resultSet.getInt(prefix + "thread");
        Seq sequence = Seq.of(resultSet.getLong(prefix + "sequence"));
        FileOffset offset = FileOffset.of(
                parseUnsigned(resultSet.getBigDecimal(prefix + "offset")));
        return new RedoPosition(scn, thread, sequence, offset);
    }

    private static TableSchemaVersion readSchemaVersion(ResultSet resultSet)
            throws SQLException {
        return new TableSchemaVersion(
                resultSet.getString("container_name"),
                resultSet.getString("owner_name"),
                resultSet.getString("table_name"),
                resultSet.getLong("object_id"),
                resultSet.getLong("data_object_id"),
                Scn.of(parseUnsigned(resultSet.getBigDecimal("effective_scn"))),
                resultSet.getString("schema_json"),
                resultSet.getString("ddl_type"),
                resultSet.getString("ddl_text"),
                SchemaSource.fromDatabaseValue(resultSet.getString("source")),
                resultSet.getBoolean("drop_tombstone"));
    }

    private static void writePosition(PreparedStatement statement, int startIndex,
                                      RedoPosition position) throws SQLException {
        statement.setBigDecimal(startIndex, unsigned(position.scn().rawValue()));
        statement.setInt(startIndex + 1, position.thread());
        statement.setLong(startIndex + 2, position.sequence().value());
        statement.setBigDecimal(startIndex + 3, unsigned(position.offset().value()));
    }

    private static BigDecimal unsigned(long value) {
        return new BigDecimal(Long.toUnsignedString(value));
    }

    private static long parseUnsigned(BigDecimal value) {
        return Long.parseUnsignedLong(value.toPlainString());
    }
}
