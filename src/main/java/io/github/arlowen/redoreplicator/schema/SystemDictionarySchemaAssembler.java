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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class SystemDictionarySchemaAssembler {
    private static final long TABLE_PARTITIONED = 1L << 5;
    private static final long TABLE_USER_LOBS = 1L << 18;
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

        List<OracleColumnMetadata> columns = state.columns().stream()
                .filter(column -> column.objectId() == objectId)
                .sorted(Comparator.comparingInt(SysCol::segmentColumn)
                        .thenComparingInt(SysCol::internalColumn))
                .map(this::columnMetadata)
                .toList();
        if (columns.isEmpty()) {
            throw incomplete(tableObject, "SYS.COL$");
        }
        boolean guardColumn = false;
        for (SysCol column : state.columns()) {
            if (column.objectId() == objectId
                    && column.properties().isSet64(COLUMN_GUARD)) {
                guardColumn = true;
            }
        }

        Map<Integer, Integer> guardSegments = new LinkedHashMap<>();
        for (SysECol extendedColumn : state.extendedColumns()) {
            if (extendedColumn.tableObjectId() == objectId) {
                guardSegments.put(
                        extendedColumn.columnNumber(), extendedColumn.guardId());
            }
        }
        if (guardColumn && guardSegments.isEmpty()) {
            throw incomplete(tableObject, "SYS.ECOL$");
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

        List<TablePartition> partitions = tablePartitions(state, objectId);
        if (table.properties().isSet64(TABLE_PARTITIONED)
                && partitions.isEmpty()) {
            throw incomplete(tableObject, "SYS.TABPART$/TABCOMPART$/TABSUBPART$");
        }
        List<LobSchema> lobs = lobs(state, tableObject, user);
        if (table.properties().isSet64(TABLE_USER_LOBS) && lobs.isEmpty()) {
            throw incomplete(tableObject, "SYS.LOB$/LOBFRAG$/LOBCOMPPART$");
        }
        boolean delayedStorageCompressed = state.deferredStorage().stream()
                .anyMatch(storage -> storage.objectId() == objectId
                        && storage.compressed());

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
                delayedStorageCompressed);
        OracleTableSupport.validate(metadata);
        return Optional.of(schemaAssembler.assemble(
                container,
                metadata,
                columns,
                primaryKeyMembership,
                guardSegments,
                defaultCharacterSetId,
                defaultNationalCharacterSetId,
                lobs,
                partitions));
    }

    private static List<TablePartition> tablePartitions(
            SystemDictionaryState state, long objectId) {
        List<TablePartition> partitions = new ArrayList<>();
        state.tablePartitions().stream()
                .filter(partition -> partition.baseObjectId() == objectId)
                .sorted(Comparator.comparingLong(SysTabPart::objectId))
                .map(partition -> new TablePartition(
                        partition.objectId(), partition.dataObjectId()))
                .forEach(partitions::add);
        state.tableCompositePartitions().stream()
                .filter(partition -> partition.baseObjectId() == objectId)
                .sorted(Comparator.comparingLong(SysTabComPart::objectId))
                .forEach(partition -> state.tableSubpartitions().stream()
                        .filter(subpartition -> subpartition.parentObjectId()
                                == partition.objectId())
                        .sorted(Comparator.comparingLong(SysTabSubPart::objectId))
                        .map(subpartition -> new TablePartition(
                                subpartition.objectId(),
                                subpartition.dataObjectId()))
                        .forEach(partitions::add));
        return List.copyOf(partitions);
    }

    private static List<LobSchema> lobs(
            SystemDictionaryState state, SysObj tableObject, SysUser user) {
        List<LobSchema> lobs = new ArrayList<>();
        state.lobs().stream()
                .filter(lob -> lob.objectId() == tableObject.objectId())
                .sorted(Comparator.comparingInt(SysLob::internalColumn))
                .forEach(lob -> lobs.add(lob(state, tableObject, user, lob)));
        return List.copyOf(lobs);
    }

    private static LobSchema lob(SystemDictionaryState state,
                                 SysObj tableObject, SysUser user,
                                 SysLob lob) {
        SysObj lobObject = state.objects().stream()
                .filter(object -> object.objectId() == lob.lobObjectId())
                .findFirst()
                .orElseThrow(() -> incomplete(tableObject,
                        "SYS.OBJ$ for LOBJ# " + lob.lobObjectId()));
        String indexName = String.format(
                "SYS_IL%010dC%05d$$", tableObject.objectId(), lob.internalColumn());
        List<Long> indexes = state.objects().stream()
                .filter(object -> object.ownerId() == user.userId())
                .filter(object -> object.name().equals(indexName))
                .filter(object -> !object.dropped() && object.dataObjectId() != 0)
                .sorted(Comparator.comparingLong(SysObj::objectId))
                .map(SysObj::dataObjectId)
                .toList();

        Map<Long, LobPartition> partitions = new LinkedHashMap<>();
        Set<Long> parents = new LinkedHashSet<>();
        parents.add(lob.lobObjectId());
        state.lobCompositePartitions().stream()
                .filter(partition -> partition.lobObjectId() == lob.lobObjectId())
                .map(SysLobCompPart::partitionObjectId)
                .forEach(parents::add);
        state.lobFragments().stream()
                .filter(fragment -> parents.contains(fragment.parentObjectId()))
                .sorted(Comparator.comparingLong(SysLobFrag::fragmentObjectId))
                .forEach(fragment -> {
                    SysObj fragmentObject = state.objects().stream()
                            .filter(object -> object.objectId()
                                    == fragment.fragmentObjectId())
                            .findFirst()
                            .orElseThrow(() -> incomplete(tableObject,
                                    "SYS.OBJ$ for FRAGOBJ# "
                                            + fragment.fragmentObjectId()));
                    if (fragmentObject.dataObjectId() != 0) {
                        partitions.putIfAbsent(
                                fragmentObject.dataObjectId(),
                                new LobPartition(
                                        fragmentObject.dataObjectId(),
                                        pageSize(state, fragment.tablespaceId())));
                    }
                });
        if (lobObject.dataObjectId() != 0) {
            partitions.putIfAbsent(lobObject.dataObjectId(), new LobPartition(
                    lobObject.dataObjectId(), pageSize(state, lob.tablespaceId())));
        }
        return new LobSchema(
                lob.objectId(),
                lobObject.dataObjectId(),
                lob.lobObjectId(),
                lob.columnNumber(),
                lob.internalColumn(),
                indexes,
                List.copyOf(partitions.values()));
    }

    private static int pageSize(SystemDictionaryState state, long tablespaceId) {
        int blockSize = state.tablespaces().stream()
                .filter(tablespace -> tablespace.tablespaceId() == tablespaceId)
                .map(SysTs::blockSize)
                .findFirst()
                .orElse(0);
        if (blockSize == 16_384) {
            return 16_264;
        }
        if (blockSize == 32_768) {
            return 32_528;
        }
        return 8_132;
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
