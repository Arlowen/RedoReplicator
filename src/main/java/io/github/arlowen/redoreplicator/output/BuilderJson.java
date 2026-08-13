/*
 * Java translation derived from OpenLogReplicator BuilderJson in
 * src/builder/BuilderJson.cpp and src/builder/BuilderJson.h.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.arlowen.redoreplicator.redo.common.FileOffset;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.DecodedRedoRow;
import io.github.arlowen.redoreplicator.redo.transaction.RedoColumnValue;
import io.github.arlowen.redoreplicator.redo.transaction.RedoRowOperation;
import io.github.arlowen.redoreplicator.schema.DdlSchemaChange;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

public final class BuilderJson {
    private static final BigInteger NANOS_PER_SECOND =
            BigInteger.valueOf(1_000_000_000L);

    private final ObjectMapper objectMapper;
    private final ObjectWriter objectWriter;
    private final OracleJsonValueDecoder valueDecoder;
    private final String databaseName;
    private final IntFunction<String> transactionDatabaseResolver;
    private final long hostTimezoneSeconds;

    private Scn lwnScn = Scn.none();
    private long lwnIndex;

    public BuilderJson(
            OracleJsonValueDecoder valueDecoder,
            String databaseName,
            long hostTimezoneSeconds) {
        this(valueDecoder, databaseName, ignored -> databaseName,
                hostTimezoneSeconds);
    }

    public BuilderJson(
            OracleJsonValueDecoder valueDecoder,
            String databaseName,
            IntFunction<String> transactionDatabaseResolver,
            long hostTimezoneSeconds) {
        this.valueDecoder = Objects.requireNonNull(
                valueDecoder, "valueDecoder");
        this.databaseName = Objects.requireNonNull(
                databaseName, "databaseName");
        this.transactionDatabaseResolver = Objects.requireNonNull(
                transactionDatabaseResolver, "transactionDatabaseResolver");
        this.hostTimezoneSeconds = hostTimezoneSeconds;
        objectMapper = new ObjectMapper();
        objectMapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        objectWriter = objectMapper.writer();
    }

    public List<byte[]> buildTransaction(
            CommittedRedoTransaction transaction,
            List<? extends RedoJsonChange> changes)
            throws JsonProcessingException {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(changes, "changes");
        if (changes.isEmpty()) {
            return List.of();
        }

        beginLwn(transaction.beginPosition().scn());
        List<byte[]> messages = new ArrayList<>(changes.size() + 2);
        messages.add(serialize(begin(transaction)));
        for (RedoJsonChange change : changes) {
            messages.add(serialize(change(transaction, change)));
        }
        messages.add(serialize(commit(transaction)));
        return List.copyOf(messages);
    }

    public byte[] buildCheckpoint(
            Scn scn,
            RedoTime timestamp,
            Seq sequence,
            FileOffset fileOffset,
            boolean redo) throws JsonProcessingException {
        Objects.requireNonNull(scn, "scn");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(fileOffset, "fileOffset");
        beginLwn(scn);
        ObjectNode message = header(scn, timestamp, true, null);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("op", "chkpt");
        payload.put("seq", unsigned(sequence.value()));
        payload.put("offset", unsigned(fileOffset.value()));
        if (redo) {
            payload.put("redo", true);
        }
        appendPayload(message, payload);
        return serialize(message);
    }

    private ObjectNode begin(CommittedRedoTransaction transaction) {
        ObjectNode message = header(
                transaction.beginPosition().scn(),
                transaction.beginTimestamp(), true, transaction);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("op", "begin");
        appendPayload(message, payload);
        return message;
    }

    private ObjectNode change(
            CommittedRedoTransaction transaction,
            RedoJsonChange change) {
        ObjectNode message = header(
                transaction.commitPosition().scn(),
                transaction.commitTimestamp(), false, transaction);
        ObjectNode payload;
        if (change instanceof RedoJsonDmlChange dmlChange) {
            payload = dml(dmlChange.row());
        } else {
            RedoJsonDdlChange ddlChange = (RedoJsonDdlChange) change;
            payload = ddl(ddlChange.change());
        }
        appendPayload(message, payload);
        return message;
    }

    private ObjectNode commit(CommittedRedoTransaction transaction) {
        ObjectNode message = header(
                transaction.commitPosition().scn(),
                transaction.commitTimestamp(), false, transaction);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("op", "commit");
        appendPayload(message, payload);
        return message;
    }

    private ObjectNode dml(DecodedRedoRow row) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("op", operation(row.operation()));
        appendSchema(payload, row.table().owner(), row.table().name());
        if (row.operation() != RedoRowOperation.INSERT) {
            payload.set("before", values(row.before()));
        }
        if (row.operation() != RedoRowOperation.DELETE) {
            payload.set("after", values(row.after()));
        }
        return payload;
    }

    private ObjectNode ddl(DdlSchemaChange change) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("op", "ddl");
        appendSchema(payload, change.owner(), change.table());
        payload.put("sql", change.ddlText());
        return payload;
    }

    private ObjectNode values(Map<String, RedoColumnValue> values) {
        ObjectNode object = objectMapper.createObjectNode();
        for (Map.Entry<String, RedoColumnValue> entry : values.entrySet()) {
            object.set(entry.getKey(), valueDecoder.decode(entry.getValue()));
        }
        return object;
    }

    private ObjectNode header(
            Scn scn,
            RedoTime timestamp,
            boolean first,
            CommittedRedoTransaction transaction) {
        lwnIndex++;
        ObjectNode message = objectMapper.createObjectNode();
        if (first) {
            message.put("scn", unsigned(scn));
            long epochSeconds = timestamp.toEpochSeconds(
                    hostTimezoneSeconds);
            message.put("tm", BigInteger.valueOf(epochSeconds)
                    .multiply(NANOS_PER_SECOND));
        }
        message.put("c_scn", unsigned(lwnScn));
        message.put("c_idx", lwnIndex);
        if (transaction != null) {
            message.put("xid", transaction.xid().toString());
        }
        String messageDatabase = databaseName;
        if (transaction != null) {
            messageDatabase = transactionDatabaseResolver.apply(
                    transaction.containerId());
        }
        message.put("db", messageDatabase);
        return message;
    }

    private void beginLwn(Scn scn) {
        if (lwnScn.equals(scn)) {
            return;
        }
        lwnScn = scn;
        lwnIndex = 0;
    }

    private byte[] serialize(ObjectNode message)
            throws JsonProcessingException {
        return objectWriter.writeValueAsBytes(message);
    }

    private static void appendPayload(
            ObjectNode message, ObjectNode payload) {
        ArrayNode payloads = message.putArray("payload");
        payloads.add(payload);
    }

    private static void appendSchema(
            ObjectNode payload, String owner, String table) {
        ObjectNode schema = payload.putObject("schema");
        schema.put("owner", owner);
        schema.put("table", table);
    }

    private static String operation(RedoRowOperation operation) {
        return switch (operation) {
            case INSERT -> "c";
            case UPDATE -> "u";
            case DELETE -> "d";
        };
    }

    private static BigInteger unsigned(Scn scn) {
        return new BigInteger(scn.toDecimalString());
    }

    private static BigInteger unsigned(long value) {
        return new BigInteger(Long.toUnsignedString(value));
    }
}
