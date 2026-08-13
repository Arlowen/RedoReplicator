/*
 * Java translation derived from OpenLogReplicator: src/common/types/RowId.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

import io.github.arlowen.redoreplicator.error.DataException;

public final class RowId implements Comparable<RowId> {
    public static final int SIZE = 18;
    private static final char[] ORACLE_BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private final int dataObject;
    private final int dataBlockAddress;
    private final int slot;

    private RowId(int dataObject, int dataBlockAddress, int slot) {
        this.dataObject = dataObject;
        this.dataBlockAddress = dataBlockAddress;
        this.slot = slot;
    }

    public static RowId of(long dataObject, long dataBlockAddress, int slot) {
        if (dataObject < 0 || dataObject > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Data object must be an unsigned 32-bit value");
        }
        if (dataBlockAddress < 0 || dataBlockAddress > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Data block address must be an unsigned 32-bit value");
        }
        if (slot < 0 || slot > 0xFFFF) {
            throw new IllegalArgumentException("Row slot must be an unsigned 16-bit value");
        }
        return new RowId((int) dataObject, (int) dataBlockAddress, slot);
    }

    public static RowId parse(String text) {
        if (text.length() != SIZE) {
            throw new DataException(20008, "row ID incorrect size: " + text);
        }

        int dataObject = decode(text, 0, 6);
        int absoluteFileNumber = decode(text, 6, 3) & 0xFFFF;
        int block = decode(text, 9, 6);
        int dataBlockAddress = block | (absoluteFileNumber << 22);
        int slot = decode(text, 15, 3) & 0xFFFF;
        return new RowId(dataObject, dataBlockAddress, slot);
    }

    public static RowId fromBinary(byte[] bytes) {
        if (bytes.length != 12) {
            throw new IllegalArgumentException("Binary row ID requires 12 bytes");
        }

        int dataObject = readIntBigEndian(bytes, 0);
        int slot = ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
        int absoluteFileNumber = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        int dataBlockAddress = readIntBigEndian(bytes, 8) | (absoluteFileNumber << 22);
        return new RowId(dataObject, dataBlockAddress, slot);
    }

    public long dataObject() {
        return Integer.toUnsignedLong(dataObject);
    }

    public long dataBlockAddress() {
        return Integer.toUnsignedLong(dataBlockAddress);
    }

    public int slot() {
        return slot;
    }

    public String toHexString() {
        return String.format("%08x.%04x.%04x", dataBlockAddress, dataObject & 0xFFFF, slot);
    }

    @Override
    public int compareTo(RowId other) {
        int comparison = Integer.compareUnsigned(dataObject, other.dataObject);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compareUnsigned(dataBlockAddress, other.dataBlockAddress);
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compareUnsigned(slot, other.slot);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RowId rowId)) {
            return false;
        }
        return dataObject == rowId.dataObject
                && dataBlockAddress == rowId.dataBlockAddress
                && slot == rowId.slot;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(dataObject);
        result = 31 * result + Integer.hashCode(dataBlockAddress);
        return 31 * result + Integer.hashCode(slot);
    }

    @Override
    public String toString() {
        int absoluteFileNumber = dataBlockAddress >>> 22;
        int block = dataBlockAddress & 0x003F_FFFF;
        char[] result = new char[SIZE];
        encode(dataObject, result, 0, 6);
        encode(absoluteFileNumber, result, 6, 3);
        encode(block, result, 9, 6);
        encode(slot, result, 15, 3);
        return new String(result);
    }

    private static int decode(String text, int offset, int length) {
        int result = 0;
        for (int index = 0; index < length; index++) {
            result = (result << 6) | decode(text.charAt(offset + index));
        }
        return result;
    }

    private static int decode(char value) {
        if (value >= 'A' && value <= 'Z') {
            return value - 'A';
        }
        if (value >= 'a' && value <= 'z') {
            return value - 'a' + 26;
        }
        if (value >= '0' && value <= '9') {
            return value - '0' + 52;
        }
        if (value == '+') {
            return 62;
        }
        if (value == '/') {
            return 63;
        }
        return 0;
    }

    private static void encode(int value, char[] target, int offset, int length) {
        for (int index = length - 1; index >= 0; index--) {
            target[offset + index] = ORACLE_BASE64[value & 0x3F];
            value >>>= 6;
        }
    }

    private static int readIntBigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}
