/*
 * Java translation derived from OpenLogReplicator Builder::processValue and
 * Builder::parseLob.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.output;

import io.github.arlowen.redoreplicator.redo.lob.RedoLobContext;
import io.github.arlowen.redoreplicator.redo.transaction.DecodedRedoRow;
import io.github.arlowen.redoreplicator.redo.transaction.RedoColumnValue;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;

import java.util.LinkedHashMap;
import java.util.Map;

final class RedoLobValueResolver {
    private final OracleLobLocatorDecoder locatorDecoder =
            new OracleLobLocatorDecoder();

    DecodedRedoRow resolve(DecodedRedoRow row, RedoLobContext context) {
        Map<String, RedoColumnValue> before = resolve(row.before(), context);
        Map<String, RedoColumnValue> after = resolve(row.after(), context);
        return new DecodedRedoRow(
                row.operation(), row.table(), row.rowId(), row.fileOffset(),
                before, after);
    }

    private Map<String, RedoColumnValue> resolve(
            Map<String, RedoColumnValue> values,
            RedoLobContext context) {
        Map<String, RedoColumnValue> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, RedoColumnValue> entry : values.entrySet()) {
            RedoColumnValue value = entry.getValue();
            if (value.nullValue() || !isLob(value.type())) {
                resolved.put(entry.getKey(), value);
                continue;
            }
            byte[] data = locatorDecoder.decode(value.data(), context);
            resolved.put(entry.getKey(), RedoColumnValue.reconstructedLob(
                    value.type(), value.charsetId(), data));
        }
        return resolved;
    }

    private static boolean isLob(OracleColumnType type) {
        return type == OracleColumnType.CLOB || type == OracleColumnType.BLOB;
    }
}
