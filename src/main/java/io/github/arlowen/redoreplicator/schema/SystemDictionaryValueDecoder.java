/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.schema;

import io.github.arlowen.redoreplicator.charset.CharacterSet;
import io.github.arlowen.redoreplicator.error.DataException;
import io.github.arlowen.redoreplicator.redo.common.IntX;
import io.github.arlowen.redoreplicator.redo.value.OracleNumberDecoder;

import java.math.BigDecimal;
import java.math.BigInteger;

final class SystemDictionaryValueDecoder {
    private final OracleNumberDecoder numberDecoder;
    private final CharacterSet characterSet;

    SystemDictionaryValueDecoder(CharacterSet characterSet) {
        numberDecoder = new OracleNumberDecoder();
        this.characterSet = characterSet;
    }

    long unsignedLong(SystemDictionaryValue value, String column,
                      long nullDefault) {
        if (value.nullValue()) {
            return nullDefault;
        }
        requireType(value, OracleColumnType.NUMBER, column);
        BigInteger integer = exactInteger(value, column);
        if (integer.signum() < 0 || integer.bitLength() > 63) {
            throw invalidValue(column);
        }
        return integer.longValue();
    }

    int integer(SystemDictionaryValue value, String column,
                int nullDefault) {
        if (value.nullValue()) {
            return nullDefault;
        }
        requireType(value, OracleColumnType.NUMBER, column);
        try {
            return exactInteger(value, column).intValueExact();
        } catch (ArithmeticException e) {
            throw invalidValue(column);
        }
    }

    IntX intX(SystemDictionaryValue value, String column) {
        if (value.nullValue()) {
            return IntX.zero();
        }
        requireType(value, OracleColumnType.NUMBER, column);
        BigInteger integer = exactInteger(value, column);
        if (integer.signum() < 0 || integer.bitLength() > 128) {
            throw invalidValue(column);
        }
        return IntX.parseDecimal(integer.toString());
    }

    String text(SystemDictionaryValue value, String column,
                int maxLength) {
        if (value.nullValue()) {
            return "";
        }
        requireType(value, OracleColumnType.VARCHAR, column);
        String text = characterSet.decode(value.data());
        if (text.length() > maxLength) {
            throw new DataException(50020,
                    "Dictionary value is too long for column " + column);
        }
        return text;
    }

    private BigInteger exactInteger(SystemDictionaryValue value, String column) {
        BigDecimal decimal = numberDecoder.decode(value.data());
        try {
            return decimal.toBigIntegerExact();
        } catch (ArithmeticException e) {
            throw invalidValue(column);
        }
    }

    private static void requireType(SystemDictionaryValue value,
                                    OracleColumnType expected, String column) {
        if (value.type() != expected) {
            throw new DataException(50019,
                    "Dictionary column " + column + " expected " + expected
                            + " but found " + value.type());
        }
    }

    private static DataException invalidValue(String column) {
        return new DataException(50020,
                "Dictionary column " + column + " contains an invalid numeric value");
    }
}
