/*
 * RedoReplicator parity probe for the fixed OpenLogReplicator baseline.
 *
 * Copyright (C) 2026 RedoReplicator contributors
 * Licensed under the GNU Affero General Public License version 3 or later.
 */
#include <iostream>
#include <limits>
#include <sstream>

#include "src/common/RedoLogRecord.h"
#include "src/common/types/IntX.h"
#include "src/common/types/FileOffset.h"
#include "src/common/types/LobId.h"
#include "src/common/types/RowId.h"
#include "src/common/types/Scn.h"
#include "src/common/types/Seq.h"
#include "src/common/types/Time.h"
#include "src/common/types/Types.h"
#include "src/common/types/Xid.h"

using namespace OpenLogReplicator;

namespace OpenLogReplicator {
    IntX IntX::BASE10[IntX::DIGITS][10];
}

int main() {
    const Scn scn{0x123456789ABCDEF0ULL};
    std::cout << "scn.to48=" << scn.to48() << '\n';
    std::cout << "scn.to64=" << scn.to64() << '\n';
    std::cout << "scn.to64d=" << scn.to64D() << '\n';
    std::cout << "scn.hex12=" << scn.toStringHex12() << '\n';
    std::cout << "scn.hex16=" << scn.toStringHex16() << '\n';
    std::cout << "scn.decimal=" << scn.toString() << '\n';
    std::cout << "scn.none=" << Scn::none().toString() << '\n';

    const Seq sequence{0xFFFFFFFFU};
    std::cout << "seq.decimal=" << sequence.toString() << '\n';
    std::cout << "seq.hex=" << sequence.toStringHex(8) << '\n';

    const FileOffset fileOffset{31, 512};
    std::cout << "offset.decimal=" << fileOffset.toString() << '\n';
    std::cout << "offset.hex=" << fileOffset.toStringHex(8) << '\n';
    std::cout << "offset.block=" << fileOffset.getBlock(512) << '\n';
    FileOffset maximumOffset{std::numeric_limits<uint64_t>::max()};
    std::cout << "offset.maximum=" << maximumOffset.toString() << '\n';
    std::cout << "offset.maximumHex=" << maximumOffset.toStringHex(16) << '\n';
    maximumOffset += 1;
    std::cout << "offset.wrapped=" << maximumOffset.toString() << '\n';

    const Xid xid{2, 0x12, 0x4162};
    std::cout << "xid.classic=" << xid.toString() << '\n';

    const RowId rowId{100, (3U << 22) | 200U, 5};
    char rowIdHex[RowId::SIZE + 1];
    rowId.toHex(rowIdHex);
    std::cout << "rowid.extended=" << rowId.toString() << '\n';
    std::cout << "rowid.hex=" << rowIdHex << '\n';

    constexpr typeUba undoBlockAddress = 0x00ABCDEF12345678ULL;
    std::cout << "uba.formatted=" << PRINTUBA(undoBlockAddress) << '\n';

    IntX::initializeBASE10();
    std::string intXError;
    IntX intX;
    constexpr char maxIntX[] = "340282366920938463463374607431768211455";
    intX.setStr(maxIntX, sizeof(maxIntX) - 1, intXError);
    std::cout << "intx.maximum=" << intX.toString() << '\n';
    intX += IntX{1};
    std::cout << "intx.wrapped=" << intX.toString() << '\n';

    const uint8_t lobIdData[LobId::LENGTH]{0x01, 0x0A, 0x00, 0xFF, 0x10, 0x20, 0x03, 0x40, 0x05, 0x60};
    const LobId lobId{lobIdData};
    std::cout << "lobid.lower=" << lobId.lower() << '\n';
    std::cout << "lobid.upper=" << lobId.upper() << '\n';
    std::cout << "lobid.narrow=" << lobId.narrow() << '\n';

    constexpr uint32_t timeValue = (((((2018 - 1988) * 12 + (10 - 1)) * 31 + (15 - 1)) * 24 + 22) * 60 + 25) * 60 + 36;
    const Time redoTime{timeValue};
    std::cout << "time.raw=" << std::dec << redoTime.getVal() << '\n';
    std::cout << "time.formatted=" << redoTime << '\n';
    std::cout << "time.epochPlus8=" << redoTime.toEpoch(8 * 60 * 60) << '\n';

    RedoLogRecord record;
    record.clear();
    record.scnRecord = Scn{0x123456789ABCDEF0ULL};
    record.scn = Scn{0x000056789ABCDEF0ULL};
    record.subScn = 7;
    record.xid = Xid{2, 0x12, 0x4162};
    record.opCode = 0x0B02;
    record.cls = 1;
    record.rbl = 2;
    record.seq = 3;
    record.typ = 4;
    record.dbId = 0xFFFFFFFFU;
    record.conId = -1;
    record.flgRecord = 5;
    record.recordObj = 6;
    record.recordDataObj = 7;
    record.nRow = 8;
    record.afn = 9;
    record.size = 100;
    record.dba = 0xABCDEF01U;
    record.bdba = 0x10203040U;
    record.obj = 11;
    record.dataObj = 12;
    record.usn = -2;
    record.slt = 13;
    record.flg = 14;
    record.opc = 0x0B01;
    record.op = 15;
    record.cc = 16;
    record.slot = 17;
    record.flags = 0xA0;
    record.fb = 0x0C;
    std::cout << "record.formatted=" << record.toString() << '\n';
    return 0;
}
