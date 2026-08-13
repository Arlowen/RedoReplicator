/*
 * Java translation derived from OpenLogReplicator src/builder/Builder.h parseNumber.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.value;

import io.github.arlowen.redoreplicator.error.RedoLogException;

import java.math.BigDecimal;

public final class OracleNumberDecoder {
    private static final int POSITIVE_EXPONENT_BASE = 0xC0;
    private static final int NEGATIVE_EXPONENT_BASE = 0x3F;
    private static final int NEGATIVE_TERMINATOR = 0x66;

    public BigDecimal decode(byte[] data) {
        if (data.length == 0) {
            throw invalidNumber();
        }
        int exponent = data[0] & 0xFF;
        if (exponent == 0x80) {
            if (data.length != 1) {
                throw invalidNumber();
            }
            return BigDecimal.ZERO;
        }
        if (exponent > 0x80) {
            return decodePositive(data, exponent);
        }
        if (exponent < 0x80) {
            return decodeNegative(data, exponent);
        }
        throw invalidNumber();
    }

    private static BigDecimal decodePositive(byte[] data, int exponent) {
        if (data.length < 2) {
            throw invalidNumber();
        }
        int power = exponent - POSITIVE_EXPONENT_BASE - 1;
        BigDecimal result = BigDecimal.ZERO;
        for (int index = 1; index < data.length; index++) {
            int pair = (data[index] & 0xFF) - 1;
            if (pair < 0 || pair > 99) {
                throw invalidNumber();
            }
            result = result.add(BigDecimal.valueOf(pair).scaleByPowerOfTen(power * 2));
            power--;
        }
        return result;
    }

    private static BigDecimal decodeNegative(byte[] data, int exponent) {
        int last = data.length;
        if (last > 1 && (data[last - 1] & 0xFF) == NEGATIVE_TERMINATOR) {
            last--;
        }
        if (last < 2) {
            throw invalidNumber();
        }
        int power = NEGATIVE_EXPONENT_BASE - exponent - 1;
        BigDecimal result = BigDecimal.ZERO;
        for (int index = 1; index < last; index++) {
            int pair = 101 - (data[index] & 0xFF);
            if (pair < 0 || pair > 99) {
                throw invalidNumber();
            }
            result = result.add(BigDecimal.valueOf(pair).scaleByPowerOfTen(power * 2));
            power--;
        }
        return result.negate();
    }

    private static RedoLogException invalidNumber() {
        return new RedoLogException(50009, "Error parsing Oracle NUMBER value");
    }
}
