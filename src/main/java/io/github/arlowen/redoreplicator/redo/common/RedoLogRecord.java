/*
 * Java translation derived from OpenLogReplicator: src/common/RedoLogRecord.h
 *
 * Copyright (C) 2018-2026 Adam Leszczynski (aleszczynski@bersler.com)
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.common;

public final class RedoLogRecord {
    public static final int FB_N = 0x01;
    public static final int FB_P = 0x02;
    public static final int FB_L = 0x04;
    public static final int FB_F = 0x08;
    public static final int FB_D = 0x10;
    public static final int FB_H = 0x20;
    public static final int FB_C = 0x40;
    public static final int FB_K = 0x80;

    public static final long INVALID_LOB_PAGE_NO = 0xFFFF_FFFFL;

    public static final int OP_IUR = 0x01;
    public static final int OP_IRP = 0x02;
    public static final int OP_DRP = 0x03;
    public static final int OP_LKR = 0x04;
    public static final int OP_URP = 0x05;
    public static final int OP_ORP = 0x06;
    public static final int OP_MFC = 0x07;
    public static final int OP_CFA = 0x08;
    public static final int OP_CKI = 0x09;
    public static final int OP_SKL = 0x0A;
    public static final int OP_QMI = 0x0B;
    public static final int OP_QMD = 0x0C;
    public static final int OP_DSC = 0x0E;
    public static final int OP_LMN = 0x10;
    public static final int OP_LLB = 0x11;
    public static final int OP_019 = 0x13;
    public static final int OP_SHK = 0x14;
    public static final int OP_021 = 0x15;
    public static final int OP_CMP = 0x16;
    public static final int OP_DCU = 0x17;
    public static final int OP_MRK = 0x18;
    public static final int OP_ROWDEPENDENCIES = 0x40;

    public static final long REDO_VERSION_12_1 = 0x0C10_0000L;
    public static final long REDO_VERSION_12_2 = 0x0C20_0000L;
    public static final long REDO_VERSION_18_0 = 0x1200_0000L;
    public static final long REDO_VERSION_19_0 = 0x1300_0000L;
    public static final long REDO_VERSION_23_0 = 0x1700_0000L;

    public static final int TYP_ENCRYPTED_TABLESPACE = 0x80;

    private byte[] data;
    private int dataOffset;

    public FileOffset fileOffset;
    public Xid xid;
    public Seq sequence;
    public Scn scnRecord;
    public Scn scn;
    public RedoTime timestamp;
    public long dbId;
    public int subScn;
    public int conId;
    public long dba;
    public long bdba;
    public long obj;
    public long dataObj;
    public int size;

    public int col;
    public int fieldCnt;
    public int fieldPos;
    public int rowData;
    public int slotsDelta;
    public int rowSizesDelta;
    public int fieldSizesDelta;
    public int nullsDelta;
    public int colNumsDelta;
    public int typ;
    public int nRow;
    public int flg;
    public int opCode;
    public int opc;
    public int slot;
    public int sizeDelt;
    public int op;
    public int ccData;
    public int cc;
    public int flags;
    public int fb;

    public int suppLogFb;
    public int suppLogCC;
    public int suppLogBefore;
    public int suppLogAfter;
    public int suppLogSlot;
    public long suppLogBdba;
    public int suppLogRowData;
    public int suppLogNumsDelta;
    public int suppLogLenDelta;
    public int usn;
    public long dba0;
    public long dba1;
    public long dba2;
    public long dba3;

    public long lobPageNo;
    public long lobPageSize;
    public long lobSizePages;
    public int lobOffset;
    public int lobData;
    public int indKey;
    public int indKeyData;
    public int lobSizeRest;
    public int lobDataSize;
    public int indKeySize;
    public int indKeyDataSize;
    public int indKeyDataCode;
    public LobId lobId;
    public boolean compressed;
    public boolean encryptedTablespace;

    public long vectorNo;
    public int slt;
    public int cls;
    public int rbl;
    public int flgRecord;
    public int thread;
    public int afn;
    public int seq;

    public long recordObj;
    public long recordDataObj;

    public int ddlType;
    public int ddlObjectType;
    public int ddlSequence;
    public int ddlCount;
    public int ddlPayload1;
    public int ddlPayload1Size;
    public int ddlPayload2;
    public int ddlPayload2Size;

    public RedoLogRecord() {
        clear();
    }

    public void attachData(byte[] data, int offset, int length) {
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new IndexOutOfBoundsException("Redo record data range is outside the source buffer");
        }
        this.data = data;
        dataOffset = offset;
        size = length;
    }

    public byte[] data() {
        return data;
    }

    public int dataOffset() {
        return dataOffset;
    }

    public byte byteAt(int relativeOffset) {
        if (relativeOffset < 0 || relativeOffset >= size) {
            throw new IndexOutOfBoundsException("Redo record byte offset is outside the record");
        }
        return data[dataOffset + relativeOffset];
    }

    public void clear() {
        data = null;
        dataOffset = 0;
        fileOffset = FileOffset.zero();
        xid = Xid.zero();
        sequence = Seq.zero();
        scnRecord = Scn.zero();
        scn = Scn.zero();
        timestamp = RedoTime.zero();
        dbId = 0;
        subScn = 0;
        conId = 0;
        dba = 0;
        bdba = 0;
        obj = 0;
        dataObj = 0;
        size = 0;

        col = 0;
        fieldCnt = 0;
        fieldPos = 0;
        rowData = 0;
        slotsDelta = 0;
        rowSizesDelta = 0;
        fieldSizesDelta = 0;
        nullsDelta = 0;
        colNumsDelta = 0;
        typ = 0;
        nRow = 0;
        flg = 0;
        opCode = 0;
        opc = 0;
        slot = 0;
        sizeDelt = 0;
        op = 0;
        ccData = 0;
        cc = 0;
        flags = 0;
        fb = 0;

        suppLogFb = 0;
        suppLogCC = 0;
        suppLogBefore = 0;
        suppLogAfter = 0;
        suppLogSlot = 0;
        suppLogBdba = 0;
        suppLogRowData = 0;
        suppLogNumsDelta = 0;
        suppLogLenDelta = 0;
        usn = 0;
        dba0 = 0;
        dba1 = 0;
        dba2 = 0;
        dba3 = 0;

        lobPageNo = 0;
        lobPageSize = 0;
        lobSizePages = 0;
        lobOffset = 0;
        lobData = 0;
        indKey = 0;
        indKeyData = 0;
        lobSizeRest = 0;
        lobDataSize = 0;
        indKeySize = 0;
        indKeyDataSize = 0;
        indKeyDataCode = 0;
        lobId = LobId.zero();
        compressed = false;
        encryptedTablespace = false;

        vectorNo = 0;
        slt = 0;
        cls = 0;
        rbl = 0;
        flgRecord = 0;
        thread = 0;
        afn = 0;
        seq = 0;
        recordObj = 0;
        recordDataObj = 0;

        ddlType = 0;
        ddlObjectType = 0;
        ddlSequence = 0;
        ddlCount = 0;
        ddlPayload1 = 0;
        ddlPayload1Size = 0;
        ddlPayload2 = 0;
        ddlPayload2Size = 0;
    }

    @Override
    public String toString() {
        return "O scn: " + scnRecord.to64()
                + " scn: " + scn.toDecimalString()
                + " subScn: " + subScn
                + " xid: " + xid
                + " op: " + String.format("%04x", opCode)
                + " cls: " + cls
                + " rbl: " + rbl
                + " seq: " + seq
                + " typ: " + typ
                + " dbId: " + dbId
                + " conId: " + conId
                + " flgRecord: " + flgRecord
                + " robj: " + recordObj
                + " rdataObj: " + recordDataObj
                + " nrow: " + nRow
                + " afn: " + afn
                + " size: " + size
                + " dba: 0x" + Long.toHexString(dba)
                + " bdba: 0x" + Long.toHexString(bdba)
                + " obj: " + obj
                + " dataobj: " + dataObj
                + " usn: " + usn
                + " slt: " + slt
                + " flg: " + flg
                + " opc: 0x" + Integer.toHexString(opc)
                + " op: " + op
                + " cc: " + cc
                + " slot: " + slot
                + " flags: 0x" + Integer.toHexString(flags)
                + " fb: 0x" + Integer.toHexString(fb);
    }
}
