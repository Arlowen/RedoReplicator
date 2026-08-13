/*
 * RedoReplicator header layout probe for the fixed OpenLogReplicator baseline.
 *
 * Copyright (C) 2026 RedoReplicator contributors
 * Licensed under the GNU Affero General Public License version 3 or later.
 */
#include <array>
#include <cstdint>
#include <iostream>

#include "src/common/Ctx.h"

using namespace OpenLogReplicator;

static uint16_t checksum(uint8_t* buffer, uint32_t size, bool bigEndian = false) {
    const uint16_t oldChecksum = bigEndian ? Ctx::read16Big(buffer + 14) : Ctx::read16Little(buffer + 14);
    uint64_t sum = 0;
    for (uint32_t index = 0; index < size / 8; ++index, buffer += sizeof(uint64_t))
        sum ^= *reinterpret_cast<const uint64_t*>(buffer);
    sum ^= sum >> 32;
    sum ^= sum >> 16;
    sum ^= oldChecksum;
    return sum & 0xFFFF;
}

int main() {
    constexpr uint32_t blockSize = 512;
    alignas(8) std::array<uint8_t, blockSize * 2> fileHeader{};
    fileHeader[1] = 0x22;
    Ctx::write32Little(fileHeader.data() + 20, blockSize);
    fileHeader[28] = 0x7D;
    fileHeader[29] = 0x7C;
    fileHeader[30] = 0x7B;
    fileHeader[31] = 0x7A;

    uint8_t* block = fileHeader.data() + blockSize;
    block[0] = 1;
    block[1] = 0x22;
    Ctx::write32Little(block + 4, 1);
    Ctx::write32Little(block + 8, 77);
    Ctx::write32Little(block + 20, 0x131C0000);
    Ctx::write32Little(block + 24, 0xF0000001);
    constexpr char sid[] = "ORCL19";
    for (uint32_t index = 0; index < sizeof(sid) - 1; ++index)
        block[28 + index] = sid[index];
    Ctx::write32Little(block + 52, 9);
    Ctx::write32Little(block + 156, 1000);
    Ctx::write32Little(block + 160, 5);
    Ctx::write16Little(block + 176, 2);
    Ctx::writeScnLittle(block + 180, Scn{0x0000123456789ABCULL});
    Ctx::write32Little(block + 188, 989619936);
    Ctx::writeScnLittle(block + 192, Scn{0x000012345678ABCDULL});
    Ctx::write32Little(block + 200, 989619996);
    Ctx::write16Little(block + 14, checksum(block, blockSize));

    std::cout << "file.byteOrder=LITTLE_ENDIAN\n";
    std::cout << "file.blockSize=" << Ctx::read32Little(fileHeader.data() + 20) << '\n';
    std::cout << "file.version=" << Ctx::read32Little(block + 20) << '\n';
    std::cout << "file.sequence=" << Ctx::read32Little(block + 8) << '\n';
    std::cout << "file.databaseId=" << Ctx::read32Little(block + 24) << '\n';
    std::cout << "file.activation=" << Ctx::read32Little(block + 52) << '\n';
    std::cout << "file.blockCount=" << Ctx::read32Little(block + 156) << '\n';
    std::cout << "file.resetlogs=" << Ctx::read32Little(block + 160) << '\n';
    std::cout << "file.thread=" << Ctx::read16Little(block + 176) << '\n';
    std::cout << "file.firstScn=" << Ctx::readScnLittle(block + 180).toString() << '\n';
    std::cout << "file.firstTime=" << Ctx::read32Little(block + 188) << '\n';
    std::cout << "file.nextScn=" << Ctx::readScnLittle(block + 192).toString() << '\n';
    std::cout << "file.nextTime=" << Ctx::read32Little(block + 200) << '\n';
    std::cout << "block.type=" << static_cast<uint32_t>(block[1]) << '\n';
    std::cout << "block.number=" << Ctx::read32Little(block + 4) << '\n';
    std::cout << "block.checksum=" << Ctx::read16Little(block + 14) << '\n';
    std::cout << "block.calculatedChecksum=" << checksum(block, blockSize) << '\n';

    std::array<uint8_t, 600> record{};
    Ctx::write32Little(record.data(), record.size());
    record[4] = 0x05;
    Ctx::write32Little(record.data() + 16, 42);
    Ctx::write16Little(record.data() + 24, 2);
    Ctx::write16Little(record.data() + 26, 3);
    Ctx::write32Little(record.data() + 28, 4);
    Ctx::write32Little(record.data() + 32, record.size());
    Ctx::writeScnLittle(record.data() + 40, Scn{0x0000123456789ABCULL});
    Ctx::write32Little(record.data() + 64, 989619936);

    Ctx::write16Little(record.data() + 6, 0x1234);
    Ctx::write32Little(record.data() + 8, 0x56789000);
    Ctx::write16Little(record.data() + 12, 3);
    uint8_t* vector = record.data() + 68;
    vector[0] = 0x05;
    vector[1] = 0x02;
    Ctx::write16Little(vector + 2, 17);
    Ctx::write32Little(vector + 4, 0xABCD0007);
    Ctx::write32Little(vector + 8, 0x12345678);
    Ctx::writeScnLittle(vector + 12, Scn{0x0000123456788FFFULL});
    vector[20] = 9;
    vector[21] = 0x83;
    Ctx::write16Little(vector + 24, 4);
    Ctx::write16Little(vector + 28, 0x55AA);
    Ctx::write16Little(vector + 32, 4);
    Ctx::write16Little(vector + 34, 496);
    for (uint32_t index = 0; index < 496; ++index)
        vector[36 + index] = index;

    std::cout << "record.size=" << Ctx::read32Little(record.data()) << '\n';
    std::cout << "record.validity=" << static_cast<uint32_t>(record[4]) << '\n';
    std::cout << "record.containerUid=" << Ctx::read32Little(record.data() + 16) << '\n';
    std::cout << "lwn.number=" << Ctx::read16Little(record.data() + 24) << '\n';
    std::cout << "lwn.maximum=" << Ctx::read16Little(record.data() + 26) << '\n';
    std::cout << "lwn.blockCount=" << Ctx::read32Little(record.data() + 28) << '\n';
    std::cout << "lwn.length=" << Ctx::read32Little(record.data() + 32) << '\n';
    std::cout << "lwn.scn=" << Ctx::readScnLittle(record.data() + 40).toString() << '\n';
    std::cout << "lwn.timestamp=" << Ctx::read32Little(record.data() + 64) << '\n';
    const uint64_t memberScn = Ctx::read32Little(record.data() + 8) |
            (static_cast<uint64_t>(Ctx::read16Little(record.data() + 6)) << 32);
    const uint16_t fieldListLength = Ctx::read16Little(vector + 32);
    const uint16_t fieldCount = (fieldListLength - 2) / 2;
    const uint16_t fieldPosition = 32 + ((fieldListLength + 2) & 0xFFFC);
    uint32_t vectorSize = fieldPosition;
    for (uint16_t field = 1; field <= fieldCount; ++field)
        vectorSize += (Ctx::read16Little(vector + 32 + field * 2) + 3) & 0xFFFC;
    std::cout << "record.memberScn=" << Scn{memberScn}.toString() << '\n';
    std::cout << "record.subScn=" << Ctx::read16Little(record.data() + 12) << '\n';
    std::cout << "vector.opCode=" << ((static_cast<uint16_t>(vector[0]) << 8) | vector[1]) << '\n';
    std::cout << "vector.class=" << Ctx::read16Little(vector + 2) << '\n';
    std::cout << "vector.usn=" << (Ctx::read16Little(vector + 2) - 15) / 2 << '\n';
    std::cout << "vector.afn=" << (Ctx::read32Little(vector + 4) & 0xFFFF) << '\n';
    std::cout << "vector.dba=" << Ctx::read32Little(vector + 8) << '\n';
    std::cout << "vector.scn=" << Ctx::readScnLittle(vector + 12).toString() << '\n';
    std::cout << "vector.sequence=" << static_cast<uint32_t>(vector[20]) << '\n';
    std::cout << "vector.type=" << static_cast<uint32_t>(vector[21] & 0x7F) << '\n';
    std::cout << "vector.encrypted=1\n";
    std::cout << "vector.container=" << Ctx::read16Little(vector + 24) << '\n';
    std::cout << "vector.flags=" << Ctx::read16Little(vector + 28) << '\n';
    std::cout << "vector.fieldCount=" << fieldCount << '\n';
    std::cout << "vector.fieldPosition=" << fieldPosition << '\n';
    std::cout << "vector.fieldSize=" << Ctx::read16Little(vector + 34) << '\n';
    std::cout << "vector.size=" << vectorSize << '\n';
    std::cout << "vector.fileOffset=" << (100 * blockSize + 16 + 68) << '\n';

    std::array<uint8_t, 36> beginTransaction{};
    Ctx::write16Little(beginTransaction.data(), 0x1234);
    Ctx::write32Little(beginTransaction.data() + 4, 0x89ABCDEF);
    Ctx::write16Little(beginTransaction.data() + 16, 0x0108);
    std::array<uint8_t, 4> pdb{};
    Ctx::write32Little(pdb.data(), 0xF0000001);
    const Xid beginXid{7, Ctx::read16Little(beginTransaction.data()),
            Ctx::read32Little(beginTransaction.data() + 4)};
    std::cout << "opcode0502.xid=" << beginXid.toString() << '\n';
    std::cout << "opcode0502.flags=" << Ctx::read16Little(beginTransaction.data() + 16) << '\n';
    std::cout << "opcode0502.databaseId=" << Ctx::read32Little(pdb.data()) << '\n';

    std::array<uint8_t, 20> commit{};
    Ctx::write16Little(commit.data(), 0x5678);
    Ctx::write32Little(commit.data() + 4, 0x10203040);
    commit[16] = 0x06;
    const Xid commitXid{8, Ctx::read16Little(commit.data()),
            Ctx::read32Little(commit.data() + 4)};
    std::cout << "opcode0504.xid=" << commitXid.toString() << '\n';
    std::cout << "opcode0504.flags=" << static_cast<uint32_t>(commit[16]) << '\n';

    std::array<uint8_t, 24> ktuBlock{};
    Ctx::write32Little(ktuBlock.data(), 0xF1000001);
    Ctx::write32Little(ktuBlock.data() + 4, 0xE2000002);
    ktuBlock[16] = 0x0B;
    ktuBlock[17] = 0x01;
    ktuBlock[18] = 0x22;
    Ctx::write16Little(ktuBlock.data() + 20, 0x010C);
    std::cout << "opcodeKtu.objectId=" << Ctx::read32Little(ktuBlock.data()) << '\n';
    std::cout << "opcodeKtu.dataObjectId=" << Ctx::read32Little(ktuBlock.data() + 4) << '\n';
    std::cout << "opcodeKtu.opCode=" << ((static_cast<uint16_t>(ktuBlock[16]) << 8) | ktuBlock[17]) << '\n';
    std::cout << "opcodeKtu.slot=" << static_cast<uint32_t>(ktuBlock[18]) << '\n';
    std::cout << "opcodeKtu.flags=" << Ctx::read16Little(ktuBlock.data() + 20) << '\n';

    std::array<uint8_t, 24> ktbRedo{};
    ktbRedo[0] = 0x01;
    ktbRedo[1] = 0x08;
    Ctx::write16Little(ktbRedo.data() + 8, 0x1234);
    Ctx::write16Little(ktbRedo.data() + 10, 0x5678);
    Ctx::write32Little(ktbRedo.data() + 12, 0x9ABCDEF0);
    const Xid ktbXid{static_cast<typeUsn>(Ctx::read16Little(ktbRedo.data() + 8)),
            Ctx::read16Little(ktbRedo.data() + 10),
            Ctx::read32Little(ktbRedo.data() + 12)};
    std::cout << "opcodeKtb.xid=" << ktbXid.toString() << '\n';

    std::array<uint8_t, 48> kdo{};
    Ctx::write32Little(kdo.data(), 0xF1000001);
    kdo[10] = 0x02;
    kdo[11] = 0x41;
    kdo[16] = 0x0C;
    kdo[18] = 3;
    Ctx::write16Little(kdo.data() + 40, 9);
    Ctx::write16Little(kdo.data() + 42, 0x1234);
    kdo[45] = 0x02;
    uint32_t kdoColumnData = 0;
    for (uint32_t column = 0; column < kdo[18]; ++column) {
        if ((kdo[45 + column / 8] & (1U << (column % 8))) == 0)
            kdoColumnData = column + 1;
    }
    std::cout << "opcodeKdo.blockDba=" << Ctx::read32Little(kdo.data()) << '\n';
    std::cout << "opcodeKdo.operation=" << static_cast<uint32_t>(kdo[10]) << '\n';
    std::cout << "opcodeKdo.flags=" << static_cast<uint32_t>(kdo[11]) << '\n';
    std::cout << "opcodeKdo.rowFlags=" << static_cast<uint32_t>(kdo[16]) << '\n';
    std::cout << "opcodeKdo.columnCount=" << static_cast<uint32_t>(kdo[18]) << '\n';
    std::cout << "opcodeKdo.columnDataCount=" << kdoColumnData << '\n';
    std::cout << "opcodeKdo.sizeDelta=" << Ctx::read16Little(kdo.data() + 40) << '\n';
    std::cout << "opcodeKdo.slot=" << Ctx::read16Little(kdo.data() + 42) << '\n';
    std::cout << "opcodeKdo.nullsOffset=45\n";

    std::array<uint8_t, 8> sessionAttribute{};
    Ctx::write16Little(sessionAttribute.data() + 2, 0x9ABC);
    Ctx::write32Little(sessionAttribute.data() + 4, 0xF1234567);
    std::cout << "opcode0513.sessionNumber=" << Ctx::read32Little(sessionAttribute.data() + 4) << '\n';
    std::cout << "opcode0513.serialNumber=" << Ctx::read16Little(sessionAttribute.data() + 2) << '\n';

    std::array<uint8_t, 6> transactionFlags{};
    Ctx::write16Little(transactionFlags.data(), 0x1805);
    Ctx::write16Little(transactionFlags.data() + 4, 0x0009);
    const uint16_t flags = Ctx::read16Little(transactionFlags.data());
    const uint16_t flags2 = Ctx::read16Little(transactionFlags.data() + 4);
    std::cout << std::boolalpha;
    std::cout << "opcode0513.ddlTransaction=" << ((flags & 0x0001) != 0) << '\n';
    std::cout << "opcode0513.recursiveTransaction=" << ((flags & 0x0004) != 0) << '\n';
    std::cout << "opcode0513.disabledLogicalReplication=" << ((flags & 0x0800) != 0) << '\n';
    std::cout << "opcode0513.datapumpImport=" << ((flags & 0x1000) != 0) << '\n';
    std::cout << "opcode0513.federationPdbReplay=" << ((flags2 & 0x0001) != 0) << '\n';
    std::cout << "opcode0513.sequenceUpdate=" << ((flags2 & 0x0008) != 0) << '\n';

    std::array<uint8_t, 4> versionAttribute{};
    Ctx::write32Little(versionAttribute.data(), 0x171A2000);
    std::cout << "opcode0513.version=" << Ctx::read32Little(versionAttribute.data()) << '\n';
    Ctx::write32Little(versionAttribute.data(), 0xE1234567);
    std::cout << "opcode0513.auditSessionId=" << Ctx::read32Little(versionAttribute.data()) << '\n';

    alignas(8) std::array<uint8_t, blockSize * 2> bigHeader{};
    bigHeader[1] = 0x22;
    Ctx::write32Big(bigHeader.data() + 20, blockSize);
    bigHeader[28] = 0x7A;
    bigHeader[29] = 0x7B;
    bigHeader[30] = 0x7C;
    bigHeader[31] = 0x7D;

    uint8_t* bigBlock = bigHeader.data() + blockSize;
    bigBlock[0] = 1;
    bigBlock[1] = 0x22;
    Ctx::write32Big(bigBlock + 4, 1);
    Ctx::write32Big(bigBlock + 8, 77);
    Ctx::write32Big(bigBlock + 20, 0x171A2000);
    Ctx::write32Big(bigBlock + 24, 0xF0000001);
    for (uint32_t index = 0; index < sizeof(sid) - 1; ++index)
        bigBlock[28 + index] = sid[index];
    Ctx::write32Big(bigBlock + 52, 9);
    Ctx::write32Big(bigBlock + 156, 1000);
    Ctx::write32Big(bigBlock + 160, 5);
    Ctx::write16Big(bigBlock + 176, 2);
    Ctx::writeScnBig(bigBlock + 180, Scn{0x0000123456789ABCULL});
    Ctx::write32Big(bigBlock + 188, 989619936);
    Ctx::writeScnBig(bigBlock + 192, Scn{0x000012345678ABCDULL});
    Ctx::write32Big(bigBlock + 200, 989619996);
    Ctx::write16Big(bigBlock + 14, checksum(bigBlock, blockSize, true));
    // The stored big-endian checksum is a fixed point of Reader.cpp's
    // native-word fold and byte-order-aware checksum field read.
    Ctx::write16Big(bigBlock + 14, checksum(bigBlock, blockSize, true));

    std::cout << "big.file.byteOrder=BIG_ENDIAN\n";
    std::cout << "big.file.blockSize=" << Ctx::read32Big(bigHeader.data() + 20) << '\n';
    std::cout << "big.file.version=" << Ctx::read32Big(bigBlock + 20) << '\n';
    std::cout << "big.file.sequence=" << Ctx::read32Big(bigBlock + 8) << '\n';
    std::cout << "big.file.firstScn=" << Ctx::readScnBig(bigBlock + 180).toString() << '\n';
    std::cout << "big.block.checksum=" << Ctx::read16Big(bigBlock + 14) << '\n';
    std::cout << "big.block.calculatedChecksum=" << checksum(bigBlock, blockSize, true) << '\n';
    return 0;
}
