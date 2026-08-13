/*
 * Java translation derived from OpenLogReplicator: src/common/types/LobId.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

public final class LobId implements Comparable<LobId>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final int LENGTH = 10;
    private static final LobId ZERO = new LobId(new byte[LENGTH]);

    private final byte[] data;

    private LobId(byte[] data) {
        this.data = data;
    }

    public static LobId zero() {
        return ZERO;
    }

    public static LobId of(byte[] data) {
        if (data.length != LENGTH) {
            throw new IllegalArgumentException("LOB ID requires 10 bytes");
        }
        boolean zero = true;
        for (byte value : data) {
            if (value != 0) {
                zero = false;
                break;
            }
        }
        if (zero) {
            return ZERO;
        }
        return new LobId(Arrays.copyOf(data, data.length));
    }

    public byte[] bytes() {
        return Arrays.copyOf(data, data.length);
    }

    public String lower() {
        return toHex(false);
    }

    public String upper() {
        return toHex(true);
    }

    public String narrow() {
        StringBuilder result = new StringBuilder(LENGTH * 2);
        for (byte value : data) {
            result.append(Integer.toHexString(value & 0xFF).toUpperCase());
        }
        return result.toString();
    }

    @Override
    public int compareTo(LobId other) {
        return Arrays.compareUnsigned(data, other.data);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LobId lobId)) {
            return false;
        }
        return Arrays.equals(data, lobId.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return upper();
    }

    private String toHex(boolean uppercase) {
        StringBuilder result = new StringBuilder(LENGTH * 2);
        for (byte value : data) {
            String hex = String.format("%02x", value & 0xFF);
            if (uppercase) {
                hex = hex.toUpperCase();
            }
            result.append(hex);
        }
        return result.toString();
    }
}
