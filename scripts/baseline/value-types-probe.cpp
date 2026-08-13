/*
 * RedoReplicator parity probe for the fixed OpenLogReplicator baseline.
 *
 * Copyright (C) 2026 RedoReplicator contributors
 * Licensed under the GNU Affero General Public License version 3 or later.
 */
#include <iostream>
#include <limits>
#include <sstream>

#include "src/common/types/FileOffset.h"
#include "src/common/types/RowId.h"
#include "src/common/types/Scn.h"
#include "src/common/types/Seq.h"
#include "src/common/types/Types.h"
#include "src/common/types/Xid.h"

using namespace OpenLogReplicator;

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
    return 0;
}
