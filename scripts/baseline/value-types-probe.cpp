/*
 * RedoReplicator parity probe for the fixed OpenLogReplicator baseline.
 *
 * Copyright (C) 2026 RedoReplicator contributors
 * Licensed under the GNU Affero General Public License version 3 or later.
 */
#include <iostream>
#include <limits>
#include <sstream>

#include "src/common/Attribute.h"
#include "src/common/DbIncarnation.h"
#include "src/common/LobKey.h"
#include "src/common/RedoLogRecord.h"
#include "src/common/exception/BootException.h"
#include "src/common/exception/ConfigurationException.h"
#include "src/common/exception/RedoLogException.h"
#include "src/common/exception/RuntimeException.h"
#include "src/common/types/IntX.h"
#include "src/common/types/Data.h"
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
    int64_t timezone;
    std::cout << "data.timezone.prc="
              << Data::parseTimezone("PRC", timezone) << ":" << timezone << '\n';
    std::cout << "data.timezone.pst="
              << Data::parseTimezone("PST8PDT", timezone) << ":" << timezone << '\n';
    std::cout << "data.timezone.negative="
              << Data::parseTimezone("-05:30", timezone) << ":" << timezone << '\n';
    std::cout << "data.timezone.invalid="
              << Data::parseTimezone("Asia/Shanghai", timezone) << '\n';
    constexpr const char* timezoneAliases[]{
        "Etc/GMT-14", "Etc/GMT-13", "Etc/GMT-12", "Etc/GMT-11",
        "HST", "Etc/GMT-10", "Etc/GMT-9", "PST", "PST8PDT",
        "Etc/GMT-8", "MST", "MST7MDT", "Etc/GMT-7", "CST",
        "CST6CDT", "Etc/GMT-6", "EST", "EST5EDT", "Etc/GMT-5",
        "Etc/GMT-4", "Etc/GMT-3", "Etc/GMT-2", "Etc/GMT-1",
        "GMT", "Etc/GMT", "Greenwich", "Etc/Greenwich", "GMT0",
        "Etc/GMT0", "GMT+0", "Etc/GMT-0", "Etc/GMT+0", "UTC",
        "Etc/UTC", "UCT", "Etc/UCT", "Universal", "Etc/Universal",
        "WET", "MET", "CET", "Etc/GMT+1", "EET", "Etc/GMT+2",
        "Etc/GMT+3", "Etc/GMT+4", "Etc/GMT+5", "Etc/GMT+6",
        "Etc/GMT+7", "PRC", "ROC", "Etc/GMT+8", "Etc/GMT+9",
        "Etc/GMT+10", "Etc/GMT+11", "Etc/GMT+12"
    };
    std::cout << "data.timezone.aliases=";
    for (const char* alias: timezoneAliases) {
        Data::parseTimezone(alias, timezone);
        std::cout << timezone << ',';
    }
    std::cout << '\n';
    std::cout << "data.timezone.formatPositive=" << Data::timezoneToString(19800) << '\n';
    std::cout << "data.timezone.formatNegative=" << Data::timezoneToString(-45000) << '\n';

    const time_t adEpoch = Data::valuesToEpoch(2024, 1, 29, 12, 34, 56, 8 * 60 * 60);
    const time_t leapEpoch = Data::valuesToEpoch(2000, 1, 29, 0, 0, 0, 0);
    const time_t bcEpoch = Data::valuesToEpoch(0, 0, 1, 0, 0, 0, 0);
    char isoBuffer[32];
    std::cout << "data.epoch.ad=" << adEpoch << '\n';
    std::cout << "data.epoch.leap=" << leapEpoch << '\n';
    std::cout << "data.epoch.bc=" << bcEpoch << '\n';
    Data::epochToIso8601(adEpoch, isoBuffer, true, true);
    std::cout << "data.iso.ad=" << isoBuffer << '\n';
    Data::epochToIso8601(leapEpoch, isoBuffer, false, false);
    std::cout << "data.iso.leap=" << isoBuffer << '\n';
    Data::epochToIso8601(bcEpoch, isoBuffer, true, true);
    std::cout << "data.iso.bc=" << isoBuffer << '\n';

    std::ostringstream escaped;
    Data::writeEscapeValue(escaped, std::string{"A\t\n\b\f\r\"\\\x01Z", 11});
    std::cout << "data.escape=" << escaped.str() << '\n';
    std::cout << "data.escape.hex=";
    constexpr char hex[]{"0123456789abcdef"};
    for (const unsigned char value: escaped.str())
        std::cout << hex[value >> 4] << hex[value & 0x0F];
    std::cout << '\n';
    std::cout << "data.map16=" << Data::map16(10) << Data::map16U(15) << '\n';
    std::cout << "data.map64=" << Data::map64(62) << Data::map64(63) << '\n';
    Data::epochToIso8601(-210831897600L, isoBuffer, true, true);
    std::cout << "data.iso.minimum=" << isoBuffer << '\n';
    Data::epochToIso8601(253402300799L, isoBuffer, true, true);
    std::cout << "data.iso.maximum=" << isoBuffer << '\n';
    try {
        Data::epochToIso8601(253402300800L, isoBuffer, true, true);
    } catch (const RuntimeException& exception) {
        std::cout << "data.iso.invalidCode=" << exception.code << '\n';
    }
    Data::checkName(std::string(1023, 'A'));
    std::cout << "data.name.maximum=ok\n";
    try {
        Data::checkName(std::string(1024, 'A'));
    } catch (const DataException& exception) {
        std::cout << "data.name.invalidCode=" << exception.code << '\n';
    }

    const uint8_t lobKeyFirstBytes[]{0, 0, 0, 1, 2, 3, 4, 5, 6, 7};
    const uint8_t lobKeySecondBytes[]{0, 0, 0, 1, 2, 3, 4, 5, 6, 8};
    const LobKey lobKeyFirst(LobId(lobKeyFirstBytes), 16);
    const LobKey lobKeySame(LobId(lobKeyFirstBytes), 16);
    const LobKey lobKeyNextPage(LobId(lobKeyFirstBytes), 17);
    const LobKey lobKeyNextId(LobId(lobKeySecondBytes), 1);
    std::cout << std::boolalpha;
    std::cout << "lobkey.equal=" << (lobKeyFirst == lobKeySame) << '\n';
    std::cout << "lobkey.pageLess=" << (lobKeyFirst < lobKeyNextPage) << '\n';
    std::cout << "lobkey.idLess=" << (lobKeyNextPage < lobKeyNextId) << '\n';
    std::cout << "lobkey.different=" << (lobKeyFirst != lobKeyNextPage) << '\n';
    std::cout << std::noboolalpha;

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

    std::cout << "attribute.count=" << Attribute::fromString().size() << '\n';
    std::cout << "attribute.first=" << Attribute::toString(Attribute::KEY::VERSION) << '\n';
    std::cout << "attribute.last=" << Attribute::toString(Attribute::KEY::SEQ_UPDATE_TRANSACTION) << '\n';
    std::cout << "attribute.reverse=" << std::dec << static_cast<uint>(Attribute::fromString().at("client id")) << '\n';

    const DbIncarnation incarnation{0xFFFFFFFFU, Scn{100}, Scn{50}, "CURRENT", 200, 0xFFFFFFFEU};
    std::cout << "incarnation.formatted=" << incarnation << '\n';

    const BootException bootException{10001, "boot failed"};
    const ConfigurationException configurationException{10002, "configuration failed"};
    const RedoLogException redoLogException{10003, "redo failed"};
    const RuntimeException runtimeException{10004, "runtime failed", 55};
    std::cout << "exception.boot=" << bootException.code << ":" << bootException << '\n';
    std::cout << "exception.configuration=" << configurationException.code << ":" << configurationException << '\n';
    std::cout << "exception.redo=" << redoLogException.code << ":" << redoLogException << '\n';
    std::cout << "exception.runtime=" << runtimeException.code << ":" << runtimeException.supCode << ":" << runtimeException << '\n';
    return 0;
}
