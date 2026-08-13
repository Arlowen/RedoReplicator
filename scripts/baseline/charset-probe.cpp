/*
 * RedoReplicator character-set parity probe for the fixed
 * OpenLogReplicator baseline.
 */
#include <iomanip>
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
}

int main() {
    Locales locales;
    locales.initialize();

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
    return 0;
}
