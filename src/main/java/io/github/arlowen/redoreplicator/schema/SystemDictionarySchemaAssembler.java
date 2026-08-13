/*
 * Java translation derived from OpenLogReplicator src/metadata/Schema.cpp buildMaps.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.DataException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SystemDictionarySchemaAssembler {
    private static final long TABLE_PARTITIONED = 1L << 5;
    private static final long TABLE_USER_LOBS = 1L << 18;
    private static final long TABLE_DELAYED_SEGMENT = 1L << 34;
    private static final long COLUMN_GUARD = 1L << 39;

    private final OracleSchemaAssembler schemaAssembler;

    SystemDictionarySchemaAssembler() {
        schemaAssembler = new OracleSchemaAssembler();
    }

    Optional<TableSchema> assemble(SystemDictionaryState state,
                                   String container, long objectId,
                                   long defaultCharacterSetId,
                                   long defaultNationalCharacterSetId) {
        Optional<SysObj> object = state.objects().stream()
                .filter(candidate -> candidate.objectId() == objectId)
                .findFirst();
        if (object.isEmpty() || object.get().dropped() || !object.get().table()) {
            return Optional.empty();
        }
        SysObj tableObject = object.get();
        if (tableObject.temporary()) {
            throw unsupported(tableObject, "temporary table");
        }
        SysUser user = state.users().stream()
                .filter(candidate -> candidate.userId() == tableObject.ownerId())
                .findFirst()
                .orElseThrow(() -> incomplete(tableObject, "SYS.USER$"));
        SysTab table = state.tables().stream()
                .filter(candidate -> candidate.objectId() == objectId)
                .findFirst()
                .orElseThrow(() -> incomplete(tableObject, "SYS.TAB$"));

        if (table.properties().isSet64(TABLE_PARTITIONED)) {
            throw incomplete(tableObject, "SYS.TABPART$/TABCOMPART$/TABSUBPART$");
        }
        if (table.properties().isSet64(TABLE_USER_LOBS)) {
            throw incomplete(tableObject, "SYS.LOB$/LOBFRAG$/LOBCOMPPART$");
        }
        if (table.flags().isSet64(TABLE_DELAYED_SEGMENT)) {
            throw incomplete(tableObject, "SYS.DEFERRED_STG$");
        }

        List<OracleColumnMetadata> columns = state.columns().stream()
                .filter(column -> column.objectId() == objectId)
                .sorted(Comparator.comparingInt(SysCol::segmentColumn)
                        .thenComparingInt(SysCol::internalColumn))
                .map(this::columnMetadata)
                .toList();
        if (columns.isEmpty()) {
            throw incomplete(tableObject, "SYS.COL$");
        }
        for (SysCol column : state.columns()) {
            if (column.objectId() == objectId
                    && column.properties().isSet64(COLUMN_GUARD)) {
                throw incomplete(tableObject, "SYS.ECOL$");
            }
        }

        Map<Long, SysCDef> constraints = new LinkedHashMap<>();
        for (SysCDef constraint : state.constraints()) {
            if (constraint.objectId() == objectId) {
                constraints.put(constraint.constraintId(), constraint);
            }
        }
        Map<Integer, Integer> primaryKeyMembership = new LinkedHashMap<>();
        for (SysCCol constraintColumn : state.constraintColumns()) {
            if (constraintColumn.objectId() != objectId) {
                continue;
            }
            SysCDef constraint = constraints.get(constraintColumn.constraintId());
            if (constraint == null) {
                throw incomplete(tableObject, "SYS.CDEF$ constraint "
                        + constraintColumn.constraintId());
            }
            if (constraint.primaryKey()) {
                primaryKeyMembership.merge(
                        constraintColumn.internalColumn(), 1, Integer::sum);
            }
        }

        OracleTableMetadata metadata = new OracleTableMetadata(
                user.name(),
                tableObject.name(),
                tableObject.objectId(),
                table.dataObjectId(),
                user.userId(),
                table.clusterColumns(),
                0,
                tableObject.flags().low(),
                table.flags().low(),
                table.properties().low(),
                false);
        OracleTableSupport.validate(metadata);
        return Optional.of(schemaAssembler.assemble(
                container,
                metadata,
                columns,
                primaryKeyMembership,
                Map.of(),
                defaultCharacterSetId,
                defaultNationalCharacterSetId,
                List.of(),
                List.of()));
    }

    private OracleColumnMetadata columnMetadata(SysCol column) {
        return new OracleColumnMetadata(
                column.columnNumber(),
                column.segmentColumn(),
                column.internalColumn(),
                column.name(),
                column.typeCode(),
                column.length(),
                column.precision(),
                column.scale(),
                column.charsetForm(),
                column.charsetId(),
                column.nullFlag(),
                column.properties().low());
    }

    private static DataException incomplete(SysObj table, String missing) {
        return new DataException(
                50071,
                "System dictionary cannot prove complete schema for object "
                        + table.objectId() + " (" + table.name() + "): missing " + missing);
    }

    private static DataException unsupported(SysObj table, String reason) {
        return new DataException(
                50030,
                "Table " + table.name() + " is unsupported: " + reason);
    }
}
