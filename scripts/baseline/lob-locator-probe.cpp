/*
 * RedoReplicator LOB-locator probe for the fixed OpenLogReplicator baseline.
 */
#include <algorithm>
#include <iomanip>
#include <iostream>
#include <map>
#include <string>
#include <vector>

#include "builder/Builder.h"
#include "common/Ctx.h"
#include "common/LobKey.h"
#include "locales/Locales.h"
#include "metadata/Metadata.h"

using namespace OpenLogReplicator;

namespace {
    class ProbeBuilder final : public Builder {
        void columnFloat(const std::string&, double) override {}
        void columnDouble(const std::string&, long double) override {}
        void columnString(const std::string&) override {}
        void columnNumber(const std::string&, int, int) override {}
        void columnRowId(const std::string&, RowId) override {}
        void columnTimestamp(const std::string&, time_t, uint64_t) override {}
        void columnTimestampTz(const std::string&, time_t, uint64_t,
                               const std::string_view&) override {}
        void columnRaw(const std::string&, const uint8_t*, uint64_t) override {}

        void processInsert(Seq, Scn, Time, LobCtx*, const XmlCtx*,
                           const DbTable*, typeObj, typeDataObj, typeDba,
                           typeSlot, FileOffset) override {}
        void processUpdate(Seq, Scn, Time, LobCtx*, const XmlCtx*,
                           const DbTable*, typeObj, typeDataObj, typeDba,
                           typeSlot, FileOffset) override {}
        void processDelete(Seq, Scn, Time, LobCtx*, const XmlCtx*,
                           const DbTable*, typeObj, typeDataObj, typeDba,
                           typeSlot, FileOffset) override {}
        void processDdl(Seq, Scn, Time, const DbTable*, typeObj) override {}
        void processBeginMessage(Seq, Time) override {}

    public:
        ProbeBuilder(Ctx* ctx, Locales* locales, Metadata* metadata,
                     const Format& format):
                Builder(ctx, locales, metadata, format, 0) {
            valueBuffer = new char[VALUE_BUFFER_MIN];
            valueBufferSize = VALUE_BUFFER_MIN;
        }

        void processCommit() override {}
        void processCheckpoint(Seq, Scn, Time, FileOffset, bool) override {}

        void printInline(const std::string& label,
                         const std::vector<uint8_t>& locator) {
            std::map<LobKey, uint8_t*> orphaned;
            LobCtx lobCtx;
            lobCtx.orphanedLobs = &orphaned;
            const bool parsed = parseLob(
                    &lobCtx, locator.data(), locator.size(), 0, 100,
                    FileOffset(512), false, false);
            std::cout << label << ".ok=" << (parsed ? "true" : "false")
                      << '\n';
            std::cout << label << ".hex=" << std::hex << std::setfill('0');
            for (uint64_t index = 0; index < valueSize; ++index) {
                std::cout << std::setw(2)
                          << static_cast<uint>(valueBuffer[index] & 0xFF);
            }
            std::cout << '\n';
        }
    };

    Format format() {
        return Format(
                Format::DB_FORMAT::DEFAULT,
                Format::ATTRIBUTES_FORMAT::DEFAULT,
                Format::INTERVAL_DTS_FORMAT::UNIX_NANO,
                Format::INTERVAL_YTM_FORMAT::MONTHS,
                Format::MESSAGE_FORMAT::DEFAULT,
                Format::RID_FORMAT::TEXT,
                Format::REDO_THREAD_FORMAT::SKIP,
                Format::XID_FORMAT::TEXT_HEX,
                Format::TIMESTAMP_FORMAT::UNIX_NANO,
                Format::TIMESTAMP_FORMAT::UNIX_NANO,
                Format::TIMESTAMP_TZ_FORMAT::UNIX_NANO_STRING,
                Format::TIMESTAMP_TYPE::DEFAULT,
                Format::CHAR_FORMAT::UTF8,
                Format::SCN_FORMAT::NUMERIC,
                Format::SCN_TYPE::DEFAULT,
                Format::UNKNOWN_FORMAT::QUESTION_MARK,
                Format::SCHEMA_FORMAT::DEFAULT,
                Format::COLUMN_FORMAT::CHANGED,
                Format::UNKNOWN_TYPE::SHOW,
                Format::USER_TYPE::DEFAULT);
    }

    void write16Big(std::vector<uint8_t>& data, size_t offset,
                    uint16_t value) {
        data[offset] = static_cast<uint8_t>(value >> 8);
        data[offset + 1] = static_cast<uint8_t>(value);
    }

    std::vector<uint8_t> fixedLocator(
            const std::vector<uint8_t>& value) {
        std::vector<uint8_t> locator(36 + value.size());
        locator[5] = 0x04;
        write16Big(locator, 20, value.size() + 16);
        write16Big(locator, 22, 0x0100);
        write16Big(locator, 28, value.size());
        std::copy(value.begin(), value.end(), locator.begin() + 36);
        return locator;
    }

    std::vector<uint8_t> variableLocator(
            const std::vector<uint8_t>& value) {
        std::vector<uint8_t> locator(30 + value.size());
        locator[5] = 0x04;
        write16Big(locator, 20, value.size() + 10);
        write16Big(locator, 22, 0x0800);
        locator[28] = static_cast<uint8_t>(value.size());
        std::copy(value.begin(), value.end(), locator.begin() + 30);
        return locator;
    }
}

int main() {
    Ctx ctx;
    Locales locales;
    Metadata metadata(
            &ctx, &locales, "FREEPDB1", Scn::zero(), Seq::zero(), "", 0);
    const Format outputFormat = format();
    ProbeBuilder builder(&ctx, &locales, &metadata, outputFormat);
    builder.printInline("fixed", fixedLocator({0x01, 0x02, 0x03}));
    builder.printInline("variable", variableLocator({0x41, 0x42, 0x43, 0x44}));
    builder.printInline("empty", variableLocator({}));
    return 0;
}
