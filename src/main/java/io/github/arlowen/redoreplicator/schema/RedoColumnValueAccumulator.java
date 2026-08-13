/*
 * Java translation derived from OpenLogReplicator Builder::valueSet.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;

import java.io.ByteArrayOutputStream;

final class RedoColumnValueAccumulator {
    private final OracleColumnType type;
    private final String columnName;

    private byte[] complete;
    private byte[] first;
    private byte[] middle;
    private byte[] last;
    private boolean nullValue;
    private boolean completeSet;

    RedoColumnValueAccumulator(ColumnSchema column) {
        type = column.type();
        columnName = column.name();
    }

    void add(byte[] data, boolean isNull, int fragmentBits) {
        if (isNull && fragmentBits != 0) {
            throw invalid("NULL value is split across row pieces");
        }
        if (fragmentBits == 0) {
            if (completeSet || hasFragments()) {
                throw invalid("column value occurs more than once");
            }
            completeSet = true;
            nullValue = isNull;
            complete = data.clone();
            return;
        }
        if (completeSet) {
            throw invalid("complete and fragmented values are mixed");
        }
        if (fragmentBits == RedoLogRecord.FB_N) {
            first = setFragment(first, data, "first");
            return;
        }
        if (fragmentBits == (RedoLogRecord.FB_P | RedoLogRecord.FB_N)) {
            middle = setFragment(middle, data, "middle");
            return;
        }
        if (fragmentBits == RedoLogRecord.FB_P) {
            last = setFragment(last, data, "last");
            return;
        }
        throw invalid("unsupported row fragment flags: " + fragmentBits);
    }

    SystemDictionaryValue finish() {
        if (completeSet) {
            if (nullValue || complete.length == 0) {
                return SystemDictionaryValue.nullValue(type);
            }
            return SystemDictionaryValue.of(type, complete);
        }
        if (first == null || last == null) {
            throw invalid("fragmented value is incomplete");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(first);
        if (middle != null) {
            output.writeBytes(middle);
        }
        output.writeBytes(last);
        byte[] data = output.toByteArray();
        if (data.length == 0) {
            return SystemDictionaryValue.nullValue(type);
        }
        return SystemDictionaryValue.of(type, data);
    }

    private byte[] setFragment(byte[] current, byte[] data, String position) {
        if (current != null) {
            throw invalid(position + " row fragment occurs more than once");
        }
        return data.clone();
    }

    private boolean hasFragments() {
        return first != null || middle != null || last != null;
    }

    private RedoLogException invalid(String reason) {
        return new RedoLogException(50014,
                "Invalid redo value for dictionary column " + columnName
                        + ": " + reason);
    }
}
