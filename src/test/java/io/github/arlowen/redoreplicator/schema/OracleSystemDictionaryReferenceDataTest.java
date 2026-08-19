/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.common.RowId;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OracleSystemDictionaryReferenceDataTest {
    private static final RowId USER_ROW_ID = RowId.of(300, 10, 1);
    private static final RowId TABLESPACE_ROW_ID = RowId.of(300, 10, 2);

    @Test
    void loadsAllNonSysUsersAndTablespacesAtReplayScn() throws Exception {
        SystemDictionaryState state = new OracleSystemDictionaryReader()
                .loadReferenceData(connection(), Scn.of(500));

        assertEquals(List.of(new SysUser(
                USER_ROW_ID, 12, "APP", IntX.zero())), state.users());
        assertEquals(List.of(new SysTs(
                TABLESPACE_ROW_ID, 7, "USERS", 16_384)),
                state.tablespaces());
    }

    private static Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                OracleSystemDictionaryReferenceDataTest.class
                        .getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareStatement")) {
                        return statement((String) arguments[0]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement statement(String sql) {
        return (PreparedStatement) Proxy.newProxyInstance(
                OracleSystemDictionaryReferenceDataTest.class
                        .getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("executeQuery")) {
                        return resultSet(rows(sql));
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static List<Object[]> rows(String sql) {
        if (sql.contains("ORDER BY U.USER#")) {
            return List.<Object[]>of(new Object[]{
                    USER_ROW_ID.toString(), 12L, "APP", BigDecimal.ZERO});
        }
        if (sql.contains("ORDER BY T.TS#")) {
            return List.<Object[]>of(new Object[]{
                    TABLESPACE_ROW_ID.toString(), 7L, "USERS", 16_384});
        }
        return List.of();
    }

    private static ResultSet resultSet(List<Object[]> rows) {
        int[] current = {-1};
        return (ResultSet) Proxy.newProxyInstance(
                OracleSystemDictionaryReferenceDataTest.class
                        .getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("next")) {
                        current[0]++;
                        return current[0] < rows.size();
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    Object value = rows.get(current[0])[
                            (Integer) arguments[0] - 1];
                    return switch (method.getName()) {
                        case "getString" -> value.toString();
                        case "getLong" -> ((Number) value).longValue();
                        case "getInt" -> ((Number) value).intValue();
                        case "getBigDecimal" -> (BigDecimal) value;
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }
}
