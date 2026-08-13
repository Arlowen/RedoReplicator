/*
 * Java translation derived from OpenLogReplicator Builder::processDdl in
 * src/builder/Builder.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.schema.DdlSchemaChange;
import io.github.arlowen.redoreplicator.schema.SchemaCatalog;
import io.github.arlowen.redoreplicator.schema.TableSchema;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;

final class RedoDdlAssembler {
    private final Charset databaseCharacterSet;
    private final ByteArrayOutputStream sql = new ByteArrayOutputStream();

    private int nextSequence;
    private int fragmentCount;
    private int ddlType;
    private long objectId;
    private String owner;

    RedoDdlAssembler(Charset databaseCharacterSet) {
        this.databaseCharacterSet = Objects.requireNonNull(
                databaseCharacterSet, "databaseCharacterSet");
    }

    Optional<RedoJsonDdlChange> accept(
            RedoLogRecord record,
            Scn commitScn,
            SchemaCatalog schemaCatalog,
            SchemaCatalog previousSchemaCatalog) {
        int sequence = record.ddlSequence;
        int count = record.ddlCount;
        if (sequence <= 0 || count <= 0 || sequence > count) {
            throw invalid(record, "invalid DDL fragment sequence "
                    + sequence + "/" + count);
        }
        if (sequence == 1) {
            if (nextSequence != 0) {
                throw invalid(record,
                        "a new DDL starts before the previous DDL completes");
            }
            start(record, count);
        } else {
            validateContinuation(record, sequence, count);
        }
        appendFragment(record, sequence);
        nextSequence++;
        if (sequence != count) {
            return Optional.empty();
        }
        if (sql.size() == 0) {
            throw invalid(record, "completed DDL contains no SQL text");
        }
        RedoJsonDdlChange change = complete(
                record, commitScn, schemaCatalog,
                previousSchemaCatalog);
        reset();
        return Optional.of(change);
    }

    void finish() {
        if (nextSequence != 0) {
            throw new RedoLogException(50057,
                    "Committed transaction contains incomplete DDL fragments: "
                            + (nextSequence - 1) + "/" + fragmentCount);
        }
    }

    private void start(RedoLogRecord record, int count) {
        nextSequence = 1;
        fragmentCount = count;
        ddlType = record.ddlType;
        objectId = record.obj;
        owner = "";
        sql.reset();
    }

    private void validateContinuation(
            RedoLogRecord record, int sequence, int count) {
        if (nextSequence == 0 || sequence != nextSequence
                || count != fragmentCount || record.ddlType != ddlType
                || record.obj != objectId) {
            throw invalid(record, "unexpected DDL fragment "
                    + sequence + "/" + count);
        }
    }

    private void appendFragment(RedoLogRecord record, int sequence) {
        byte[] payload1 = payload(
                record, record.ddlPayload1, record.ddlPayload1Size,
                "DDL field 2");
        if (sequence == 1) {
            owner = new String(payload1, databaseCharacterSet);
        } else {
            sql.write(payload1, 0, payload1.length);
        }
        if (record.ddlPayload2Size > 0) {
            byte[] payload2 = payload(
                    record, record.ddlPayload2,
                    record.ddlPayload2Size, "DDL field 8");
            sql.write(payload2, 0, payload2.length - 1);
        }
    }

    private RedoJsonDdlChange complete(
            RedoLogRecord record,
            Scn commitScn,
            SchemaCatalog schemaCatalog,
            SchemaCatalog previousSchemaCatalog) {
        Optional<TableSchema> resolved = schemaCatalog.findByObjectId(
                objectId);
        if (resolved.isEmpty()) {
            resolved = previousSchemaCatalog.findByObjectId(objectId);
        }
        TableSchema table = resolved.orElseThrow(() -> invalid(record,
                "DDL object " + objectId
                        + " has no proven table schema"));
        if (!owner.isEmpty() && !owner.equals(table.owner())) {
            throw invalid(record, "DDL owner " + owner
                    + " does not match " + table.qualifiedName());
        }
        DdlSchemaChange change = new DdlSchemaChange(
                table.container(), table.owner(), table.name(), ddlType,
                sql.toString(databaseCharacterSet), commitScn);
        return new RedoJsonDdlChange(change);
    }

    private void reset() {
        nextSequence = 0;
        fragmentCount = 0;
        ddlType = 0;
        objectId = 0;
        owner = null;
        sql.reset();
    }

    private static byte[] payload(
            RedoLogRecord record,
            int position,
            int size,
            String field) {
        if (size <= 0 || position < 0 || position > record.size - size) {
            throw invalid(record, field + " is outside the redo record");
        }
        byte[] value = new byte[size];
        System.arraycopy(record.data(), record.dataOffset() + position,
                value, 0, size);
        return value;
    }

    private static RedoLogException invalid(
            RedoLogRecord record, String detail) {
        return new RedoLogException(50057,
                detail + " at offset " + record.fileOffset);
    }
}
