/*
 * Java translation derived from OpenLogReplicator: src/common/DbIncarnation.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

public final class DbIncarnation {
    private final long incarnation;
    private final Scn resetlogsScn;
    private final Scn priorResetlogsScn;
    private final String status;
    private final long resetlogs;
    private final long priorIncarnation;
    private final boolean current;

    public DbIncarnation(long incarnation, Scn resetlogsScn, Scn priorResetlogsScn,
                         String status, long resetlogs, long priorIncarnation) {
        this.incarnation = requireUnsignedInt(incarnation, "incarnation");
        this.resetlogsScn = resetlogsScn;
        this.priorResetlogsScn = priorResetlogsScn;
        this.status = status;
        this.resetlogs = requireUnsignedInt(resetlogs, "resetlogs");
        this.priorIncarnation = requireUnsignedInt(priorIncarnation, "prior incarnation");
        current = "CURRENT".equals(status);
    }

    public long incarnation() {
        return incarnation;
    }

    public Scn resetlogsScn() {
        return resetlogsScn;
    }

    public Scn priorResetlogsScn() {
        return priorResetlogsScn;
    }

    public String status() {
        return status;
    }

    public long resetlogs() {
        return resetlogs;
    }

    public long priorIncarnation() {
        return priorIncarnation;
    }

    public boolean isCurrent() {
        return current;
    }

    @Override
    public String toString() {
        return "(" + incarnation + ", " + resetlogsScn + ", " + priorResetlogsScn
                + ", " + status + ", " + resetlogs + ", " + priorIncarnation + ")";
    }

    private static long requireUnsignedInt(long value, String field) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException(field + " must be an unsigned 32-bit value");
        }
        return value;
    }
}
