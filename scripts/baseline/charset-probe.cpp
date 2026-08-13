/*
 * RedoReplicator character-set parity probe for the fixed
 * OpenLogReplicator baseline.
 */
#include <iomanip>
#include <cstdint>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "common/Ctx.h"
#include "common/types/Xid.h"
#include "locales/CharacterSet.h"
#include "locales/CharacterSet16bit.h"
#include "locales/CharacterSet8bit.h"
#include "locales/Locales.h"

using namespace OpenLogReplicator;

namespace {
    std::string decode(const CharacterSet* characterSet,
                       const std::vector<uint8_t>& bytes) {
        const uint8_t* current = bytes.data();
        uint64_t remaining = bytes.size();
        std::ostringstream value;
        value << std::hex << std::nouppercase;
        bool first = true;

        while (remaining > 0) {
            const typeUnicode codePoint = characterSet->decode(
                    nullptr, Xid(), current, remaining);
            if (!first)
                value << '.';
            value << codePoint;
            first = false;
        }
        return value.str();
    }

    void print(Locales& locales, uint64_t id, const std::string& key,
               const std::vector<uint8_t>& bytes) {
        std::cout << key << '=' << decode(locales.characterMap.at(id), bytes)
                  << '\n';
    }

    uint64_t zhs16gbkMapDigest(const CharacterSet* characterSet) {
        uint64_t hash = 14695981039346656037ULL;
        for (uint64_t byte1 = 0x81; byte1 <= 0xFE; ++byte1) {
            for (uint64_t byte2 = 0x40; byte2 <= 0xFE; ++byte2) {
                const uint8_t bytes[]{static_cast<uint8_t>(byte1),
                                      static_cast<uint8_t>(byte2)};
                const uint8_t* current = bytes;
                uint64_t remaining = 2;
                const typeUnicode codePoint = characterSet->decode(
                        nullptr, Xid(), current, remaining);
                for (int shift = 24; shift >= 0; shift -= 8) {
                    hash ^= (codePoint >> shift) & 0xFF;
                    hash *= 1099511628211ULL;
                }
            }
        }
        return hash;
    }

    uint64_t singleByteMapDigest(
            const CharacterSet* characterSet, const Ctx* ctx = nullptr) {
        uint64_t hash = 14695981039346656037ULL;
        for (uint64_t byte = 0; byte <= 0xFF; ++byte) {
            const uint8_t encoded[]{static_cast<uint8_t>(byte)};
            const uint8_t* current = encoded;
            uint64_t remaining = 1;
            const typeUnicode codePoint = characterSet->decode(
                    ctx, Xid(), current, remaining);
            for (int shift = 24; shift >= 0; shift -= 8) {
                hash ^= (codePoint >> shift) & 0xFF;
                hash *= 1099511628211ULL;
            }
        }
        return hash;
    }

    uint64_t bytePairMapDigest(
            const CharacterSet* characterSet, const Ctx* ctx) {
        uint64_t hash = 14695981039346656037ULL;
        for (uint64_t byte1 = 0; byte1 <= 0xFF; ++byte1) {
            for (uint64_t byte2 = 0; byte2 <= 0xFF; ++byte2) {
                const uint8_t encoded[]{static_cast<uint8_t>(byte1),
                                        static_cast<uint8_t>(byte2)};
                const uint8_t* current = encoded;
                uint64_t remaining = 2;
                typeUnicode codePoints[2];
                uint64_t count = 0;
                while (remaining > 0) {
                    codePoints[count++] = characterSet->decode(
                            ctx, Xid(), current, remaining);
                }
                hash ^= count;
                hash *= 1099511628211ULL;
                for (uint64_t index = 0; index < count; ++index) {
                    for (int shift = 24; shift >= 0; shift -= 8) {
                        hash ^= (codePoints[index] >> shift) & 0xFF;
                        hash *= 1099511628211ULL;
                    }
                }
            }
        }
        return hash;
    }

    uint64_t japaneseEucTripleMapDigest(
            const CharacterSet* characterSet, const Ctx* ctx) {
        uint64_t hash = 14695981039346656037ULL;
        for (uint64_t byte2 = 0xA1; byte2 <= 0xFE; ++byte2) {
            for (uint64_t byte3 = 0xA1; byte3 <= 0xFE; ++byte3) {
                const uint8_t encoded[]{0x8F,
                                        static_cast<uint8_t>(byte2),
                                        static_cast<uint8_t>(byte3)};
                const uint8_t* current = encoded;
                uint64_t remaining = 3;
                const typeUnicode codePoint = characterSet->decode(
                        ctx, Xid(), current, remaining);
                for (int shift = 24; shift >= 0; shift -= 8) {
                    hash ^= (codePoint >> shift) & 0xFF;
                    hash *= 1099511628211ULL;
                }
            }
        }
        return hash;
    }
}

int main(int argc, char** argv) {
    Ctx ctx;
    ctx.logLevel = Ctx::LOG::SILENT;
    Locales locales;
    locales.initialize();

    if (argc == 2 && std::string(argv[1]) == "--dump-zhs16gbk-map") {
        const CharacterSet* characterSet = locales.characterMap.at(852);
        for (uint64_t byte1 = 0x81; byte1 <= 0xFE; ++byte1) {
            for (uint64_t byte2 = 0x40; byte2 <= 0xFE; ++byte2) {
                const uint8_t bytes[]{static_cast<uint8_t>(byte1),
                                      static_cast<uint8_t>(byte2)};
                const uint8_t* current = bytes;
                uint64_t remaining = 2;
                const typeUnicode codePoint = characterSet->decode(
                        nullptr, Xid(), current, remaining);
                std::cout << std::hex << std::setfill('0') << std::setw(2)
                          << byte1 << std::setw(2) << byte2 << '\t'
                          << codePoint << '\n';
            }
        }
        return 0;
    }

    print(locales, 873, "al32.ascii", {0x41});
    print(locales, 873, "al32.bmp", {0xE4, 0xB8, 0xAD});
    print(locales, 873, "al32.supplementary", {0xF0, 0x9F, 0x98, 0x80});
    print(locales, 873, "al32.sequence", {0x41, 0xE4, 0xB8, 0xAD});

    print(locales, 871, "utf8.ascii", {0x41});
    print(locales, 871, "utf8.bmp", {0xE4, 0xB8, 0xAD});
    print(locales, 871, "utf8.cesu8", {0xED, 0xA0, 0xBD, 0xED, 0xB8, 0x80});
    print(locales, 871, "utf8.sequence", {0x41, 0xE4, 0xB8, 0xAD});

    print(locales, 2000, "al16.bmp", {0x4E, 0x2D});
    print(locales, 2000, "al16.supplementary", {0xD8, 0x3D, 0xDE, 0x00});
    print(locales, 2000, "al16.sequence", {0x00, 0x41, 0x4E, 0x2D});

    print(locales, 852, "zhs16gbk.ascii", {0x41});
    print(locales, 852, "zhs16gbk.euro", {0x80});
    print(locales, 852, "zhs16gbk.chinese", {0xD6, 0xD0, 0xCE, 0xC4});
    std::cout << "zhs16gbk.map_fnv1a64=" << std::hex << std::setfill('0')
              << std::setw(16)
              << zhs16gbkMapDigest(locales.characterMap.at(852)) << '\n';

    print(locales, 178, "we8mswin1252.ascii", {0x41});
    print(locales, 178, "we8mswin1252.euro", {0x80});
    print(locales, 178, "we8mswin1252.controls", {0x81, 0x8D, 0x9D});
    std::cout << "we8mswin1252.map_fnv1a64=" << std::hex
              << std::setfill('0') << std::setw(16)
              << singleByteMapDigest(locales.characterMap.at(178)) << '\n';

    const uint64_t sevenBitIds[]{
        1, 11, 13, 14, 15, 16, 17, 21,
        202, 203, 204, 206, 205, 207
    };
    for (const uint64_t id : sevenBitIds) {
        std::cout << "seven." << std::dec << id << ".map_fnv1a64="
                  << std::hex << std::setfill('0') << std::setw(16)
                  << singleByteMapDigest(locales.characterMap.at(id)) << '\n';
    }

    for (const auto& [id, characterSet] : locales.characterMap) {
        if (dynamic_cast<const CharacterSet8bit*>(characterSet) == nullptr)
            continue;
        std::cout << "eight." << std::dec << id << ".name="
                  << characterSet->name << '\n';
        std::cout << "eight." << std::dec << id << ".map_fnv1a64="
                  << std::hex << std::setfill('0') << std::setw(16)
                  << singleByteMapDigest(characterSet) << '\n';
    }

    const uint64_t sixteenBitIds[]{829, 840, 846, 850, 865, 866, 867, 868};
    for (const uint64_t id : sixteenBitIds) {
        const CharacterSet* characterSet = locales.characterMap.at(id);
        std::cout << "sixteen." << std::dec << id << ".name="
                  << characterSet->name << '\n';
        std::cout << "sixteen." << std::dec << id
                  << ".single_fnv1a64=" << std::hex << std::setfill('0')
                  << std::setw(16) << singleByteMapDigest(
                          characterSet, &ctx) << '\n';
        std::cout << "sixteen." << std::dec << id
                  << ".pair_fnv1a64=" << std::hex << std::setfill('0')
                  << std::setw(16) << bytePairMapDigest(
                          characterSet, &ctx) << '\n';
    }

    const uint64_t eastAsianIds[]{830, 831, 832, 834, 837, 838, 845};
    for (const uint64_t id : eastAsianIds) {
        const CharacterSet* characterSet = locales.characterMap.at(id);
        std::cout << "east." << std::dec << id << ".name="
                  << characterSet->name << '\n';
        std::cout << "east." << std::dec << id
                  << ".single_fnv1a64=" << std::hex << std::setfill('0')
                  << std::setw(16) << singleByteMapDigest(
                          characterSet, &ctx) << '\n';
        std::cout << "east." << std::dec << id
                  << ".pair_fnv1a64=" << std::hex << std::setfill('0')
                  << std::setw(16) << bytePairMapDigest(
                          characterSet, &ctx) << '\n';
        if (id == 830 || id == 831 || id == 837) {
            std::cout << "east." << std::dec << id
                      << ".triple_fnv1a64=" << std::hex
                      << std::setfill('0') << std::setw(16)
                      << japaneseEucTripleMapDigest(
                              characterSet, &ctx) << '\n';
        }
    }
    return 0;
}
