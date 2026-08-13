/*
 * Java translation derived from OpenLogReplicator src/metadata/Schema.cpp.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OracleSchemaAssembler {
    private static final long HIDDEN = 1L << 5;
    private static final long STORED_AS_LOB = 1L << 7;
    private static final long SYSTEM_GENERATED = 1L << 8;
    private static final long NESTED = 1L << 10;
    private static final long UNUSED = 1L << 15;
    private static final long ADDED = 1L << 30;
    private static final long GUARD = 1L << 39;

    public TableSchema assemble(String container, OracleTableMetadata table,
                                List<OracleColumnMetadata> rawColumns,
                                Map<Integer, Integer> primaryKeyMembership,
                                Map<Integer, Integer> guardSegments,
                                long defaultCharacterSetId,
                                long defaultNationalCharacterSetId,
                                List<LobSchema> lobs,
                                List<TablePartition> partitions) {
        Map<Integer, String> xmlBaseColumnNames = new HashMap<>();
        for (OracleColumnMetadata column : rawColumns) {
            if (column.segmentColumn() == 0) {
                xmlBaseColumnNames.put(column.columnNumber(), column.name());
            }
        }

        List<ColumnSchema> columns = new ArrayList<>();
        for (OracleColumnMetadata column : rawColumns) {
            if (column.segmentColumn() == 0) {
                continue;
            }
            boolean systemGenerated = hasProperty(column, SYSTEM_GENERATED);
            boolean xmlType = systemGenerated
                    && xmlBaseColumnNames.containsKey(column.columnNumber());
            String columnName = column.name();
            if (xmlType) {
                columnName = xmlBaseColumnNames.get(column.columnNumber());
            }

            long charsetId = resolveCharacterSet(
                    column, defaultCharacterSetId, defaultNationalCharacterSetId);
            int primaryKeyCount = primaryKeyMembership.getOrDefault(
                    column.internalColumn(), 0);
            int guardSegment = guardSegments.getOrDefault(column.internalColumn(), -1);
            columns.add(new ColumnSchema(
                    column.columnNumber(),
                    guardSegment,
                    column.segmentColumn(),
                    column.internalColumn(),
                    columnName,
                    OracleColumnType.fromCode(column.typeCode()),
                    column.length(),
                    column.precision(),
                    column.scale(),
                    charsetId,
                    primaryKeyCount,
                    column.nullFlag() == 0,
                    hasProperty(column, HIDDEN) && !xmlType,
                    hasProperty(column, STORED_AS_LOB),
                    systemGenerated,
                    hasProperty(column, NESTED),
                    hasProperty(column, UNUSED),
                    hasProperty(column, ADDED),
                    hasProperty(column, GUARD),
                    xmlType));
        }

        return new TableSchema(
                container,
                table.owner(),
                table.name(),
                table.objectId(),
                table.dataObjectId(),
                table.userId(),
                table.clusterColumns(),
                table.options(),
                columns,
                lobs,
                partitions);
    }

    private static long resolveCharacterSet(OracleColumnMetadata column,
                                            long defaultCharacterSetId,
                                            long defaultNationalCharacterSetId) {
        if (column.charsetForm() == 1) {
            if (column.typeCode() == OracleColumnType.CLOB.code()) {
                return defaultNationalCharacterSetId;
            }
            return defaultCharacterSetId;
        }
        if (column.charsetForm() == 2) {
            return defaultNationalCharacterSetId;
        }
        return column.charsetId();
    }

    private static boolean hasProperty(OracleColumnMetadata column, long property) {
        return (column.propertyBits() & property) != 0;
    }
}
