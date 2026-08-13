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

#include "common/types/Xid.h"
#include "locales/CharacterSet.h"
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

    uint64_t singleByteMapDigest(const CharacterSet* characterSet) {
        uint64_t hash = 14695981039346656037ULL;
        for (uint64_t byte = 0; byte <= 0xFF; ++byte) {
            const uint8_t encoded[]{static_cast<uint8_t>(byte)};
            const uint8_t* current = encoded;
            uint64_t remaining = 1;
            const typeUnicode codePoint = characterSet->decode(
                    nullptr, Xid(), current, remaining);
            for (int shift = 24; shift >= 0; shift -= 8) {
                hash ^= (codePoint >> shift) & 0xFF;
                hash *= 1099511628211ULL;
            }
        }
        return hash;
    }
}

int main(int argc, char** argv) {
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
    return 0;
}
