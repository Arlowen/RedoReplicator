/*
 * RedoReplicator compressed-row output probe for the fixed
 * OpenLogReplicator baseline.
 */
#include <iomanip>
#include <iostream>

#include "builder/Builder.h"
#include "common/Ctx.h"
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

        void columnRaw(const std::string& name, const uint8_t* data,
                       uint64_t size) override {
            std::cout << "column.name=" << name << '\n';
            std::cout << "column.hex=" << std::hex << std::setfill('0');
            for (uint64_t index = 0; index < size; ++index)
                std::cout << std::setw(2) << static_cast<uint>(data[index]);
            std::cout << '\n';
        }

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
                Builder(ctx, locales, metadata, format, 0) {}

        void processCommit() override {}
        void processCheckpoint(Seq, Scn, Time, FileOffset, bool) override {}

        void printCompressed(const uint8_t* data, uint32_t size) {
            processValue(nullptr, nullptr, nullptr, 0, data, size,
                         FileOffset(512), true, true);
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
}

int main() {
    Ctx ctx;
    Locales locales;
    Metadata metadata(
            &ctx, &locales, "FREEPDB1", Scn::zero(), Seq::zero(), "", 0);
    const Format outputFormat = format();
    ProbeBuilder builder(&ctx, &locales, &metadata, outputFormat);
    const uint8_t data[]{0x01, 0x02, 0x03};
    builder.printCompressed(data, sizeof(data));
    return 0;
}
