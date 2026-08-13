/*
 * Java translation derived from OpenLogReplicator Transaction::flush row
 * operation classification.
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.transaction;

import io.github.arlowen.redoreplicator.error.RedoLogException;

public enum RedoRowOperation {
    INSERT,
    UPDATE,
    DELETE;

    public static RedoRowOperation append(
            RedoRowOperation current, int opCode) {
        boolean insert = opCode == 0x0B02;
        boolean delete = opCode == 0x0B03;
        boolean update = opCode == 0x0B05 || opCode == 0x0B06
                || opCode == 0x0B08 || opCode == 0x0B10
                || opCode == 0x0B16;
        if (!insert && !delete && !update) {
            throw new RedoLogException(50057,
                    "Unsupported row opcode 0x"
                            + Integer.toHexString(opCode));
        }
        if (current == null) {
            if (insert) {
                return INSERT;
            }
            if (delete) {
                return DELETE;
            }
            return UPDATE;
        }
        if (current == INSERT) {
            if (delete || opCode == 0x0B05
                    || opCode == 0x0B06 || opCode == 0x0B08) {
                return UPDATE;
            }
            return INSERT;
        }
        if (current == DELETE) {
            if (insert || opCode == 0x0B05
                    || opCode == 0x0B06 || opCode == 0x0B08) {
                return UPDATE;
            }
            return DELETE;
        }
        return UPDATE;
    }
}
