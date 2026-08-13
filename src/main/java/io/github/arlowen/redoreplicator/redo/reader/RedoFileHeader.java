/*
 * Java translation derived from OpenLogReplicator redo header handling in
 * src/reader/Reader.cpp
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.reader;

import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;

import java.nio.ByteOrder;

public final class RedoFileHeader {
    private final boolean empty;
    private final ByteOrder byteOrder;
    private final int blockSize;
    private final long compatibleVersion;
    private final String version;
    private final Seq sequence;
    private final long databaseId;
    private final String databaseSid;
    private final long activation;
    private final long blockCount;
    private final long resetlogs;
    private final int thread;
    private final Scn firstScn;
    private final RedoTime firstTime;
    private final Scn nextScn;
    private final RedoTime nextTime;

    public RedoFileHeader(boolean empty, ByteOrder byteOrder, int blockSize,
                          long compatibleVersion, String version, Seq sequence,
                          long databaseId, String databaseSid, long activation,
                          long blockCount, long resetlogs, int thread, Scn firstScn,
                          RedoTime firstTime, Scn nextScn, RedoTime nextTime) {
        this.empty = empty;
        this.byteOrder = byteOrder;
        this.blockSize = blockSize;
        this.compatibleVersion = compatibleVersion;
        this.version = version;
        this.sequence = sequence;
        this.databaseId = databaseId;
        this.databaseSid = databaseSid;
        this.activation = activation;
        this.blockCount = blockCount;
        this.resetlogs = resetlogs;
        this.thread = thread;
        this.firstScn = firstScn;
        this.firstTime = firstTime;
        this.nextScn = nextScn;
        this.nextTime = nextTime;
    }

    public boolean isEmpty() {
        return empty;
    }

    public ByteOrder byteOrder() {
        return byteOrder;
    }

    public int blockSize() {
        return blockSize;
    }

    public long compatibleVersion() {
        return compatibleVersion;
    }

    public String version() {
        return version;
    }

    public Seq sequence() {
        return sequence;
    }

    public long databaseId() {
        return databaseId;
    }

    public String databaseSid() {
        return databaseSid;
    }

    public long activation() {
        return activation;
    }

    public long blockCount() {
        return blockCount;
    }

    public long resetlogs() {
        return resetlogs;
    }

    public int thread() {
        return thread;
    }

    public Scn firstScn() {
        return firstScn;
    }

    public RedoTime firstTime() {
        return firstTime;
    }

    public Scn nextScn() {
        return nextScn;
    }

    public RedoTime nextTime() {
        return nextTime;
    }
}
