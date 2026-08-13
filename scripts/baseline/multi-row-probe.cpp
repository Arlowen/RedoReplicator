/*
 * RedoReplicator multi-row Builder parity probe for the fixed
 * OpenLogReplicator baseline.
 */
#include <iomanip>
#include <iostream>
#include <sstream>
#include <string>

#include "builder/Builder.h"
#include "common/Ctx.h"
#include "locales/Locales.h"
#include "metadata/Metadata.h"

using namespace OpenLogReplicator;

namespace {
    class ProbeBuilder final : public Builder {
        uint insertCount{0};
        uint deleteCount{0};

        static std::string hex(const uint8_t* data, uint64_t size) {
            std::ostringstream value;
            value << std::hex << std::setfill('0');
            for (uint64_t index = 0; index < size; ++index)
                value << std::setw(2) << static_cast<uint>(data[index]);
            return value.str();
        }

        void printValues(const std::string& prefix, typeSlot slot,
                         Format::VALUE_TYPE type) {
            std::cout << prefix << ".slot=" << slot << '\n';
            for (typeCol column = 0; column < valuesMax; ++column) {
                const typeMask base = static_cast<uint64_t>(column) >> 6;
                const typeMask mask = static_cast<uint64_t>(1)
                        << (column & 0x3F);
                if ((valuesSet[base] & mask) == 0)
                    continue;
                const auto valueType = static_cast<uint>(type);
                const uint8_t* data = values[column][valueType];
                const uint64_t size = sizes[column][valueType];
                std::cout << prefix << ".column" << column << "=";
                if (data == nullptr || size == 0)
                    std::cout << "null\n";
                else
                    std::cout << hex(data, size) << '\n';
            }
        }

        void columnFloat(const std::string&, double) override {}
        void columnDouble(const std::string&, long double) override {}
        void columnString(const std::string&) override {}
        void columnNumber(const std::string&, int, int) override {}
        void columnRaw(const std::string&, const uint8_t*, uint64_t) override {}
        void columnRowId(const std::string&, RowId) override {}
        void columnTimestamp(const std::string&, time_t, uint64_t) override {}
        void columnTimestampTz(const std::string&, time_t, uint64_t,
                               const std::string_view&) override {}

        void processInsert(Seq, Scn, Time, LobCtx*, const XmlCtx*,
                           const DbTable*, typeObj, typeDataObj, typeDba,
                           typeSlot slot, FileOffset) override {
            printValues("insert." + std::to_string(insertCount), slot,
                        Format::VALUE_TYPE::AFTER);
            ++insertCount;
        }

        void processUpdate(Seq, Scn, Time, LobCtx*, const XmlCtx*,
                           const DbTable*, typeObj, typeDataObj, typeDba,
                           typeSlot, FileOffset) override {}

        void processDelete(Seq, Scn, Time, LobCtx*, const XmlCtx*,
                           const DbTable*, typeObj, typeDataObj, typeDba,
                           typeSlot slot, FileOffset) override {
            printValues("delete." + std::to_string(deleteCount), slot,
                        Format::VALUE_TYPE::BEFORE);
            ++deleteCount;
        }

        void processDdl(Seq, Scn, Time, const DbTable*, typeObj) override {}
        void processBeginMessage(Seq, Time) override {}

    public:
        ProbeBuilder(Ctx* ctx, Locales* locales, Metadata* metadata,
                     const Format& format):
                Builder(ctx, locales, metadata, format, 0) {}

        void processCommit() override {}
        void processCheckpoint(Seq, Scn, Time, FileOffset, bool) override {}

        void printCounts() const {
            std::cout << "insert.count=" << insertCount << '\n';
            std::cout << "delete.count=" << deleteCount << '\n';
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

    void write16(uint8_t* data, typePos offset, uint16_t value) {
        data[offset] = static_cast<uint8_t>(value);
        data[offset + 1] = static_cast<uint8_t>(value >> 8);
    }

    void initializeRows(RedoLogRecord& record, uint8_t* data) {
        record.clear();
        record.dataExt = data;
        record.size = 24;
        record.fieldCnt = 1;
        record.fieldPos = 4;
        record.fieldSizesDelta = 0;
        record.rowData = 1;
        record.rowSizesDelta = 16;
        record.slotsDelta = 20;
        record.nRow = 2;
        write16(data, 2, 12);
        data[4] = 0;
        data[5] = 0;
        data[6] = 1;
        data[7] = 2;
        data[8] = 0xC1;
        data[9] = 2;
        data[10] = 0;
        data[11] = 0;
        data[12] = 1;
        data[13] = 2;
        data[14] = 0xC1;
        data[15] = 3;
        write16(data, 16, 6);
        write16(data, 18, 6);
        write16(data, 20, 7);
        write16(data, 22, 9);
    }
}

int main() {
    Ctx ctx;
    ctx.flags = static_cast<uint>(Ctx::REDO_FLAGS::SCHEMALESS);
    ctx.version = RedoLogRecord::REDO_VERSION_19_0;
    Locales locales;
    Metadata metadata(
            &ctx, &locales, "FREEPDB1", Scn::zero(),
            Seq::zero(), "", 0);
    const Format outputFormat = format();
    ProbeBuilder builder(&ctx, &locales, &metadata, outputFormat);

    uint8_t insertData[24]{};
    RedoLogRecord insertUndo;
    insertUndo.clear();
    insertUndo.fileOffset = FileOffset(512);
    RedoLogRecord insertRedo;
    initializeRows(insertRedo, insertData);
    insertRedo.obj = 22;
    insertRedo.dataObj = 33;
    insertRedo.bdba = 100;
    builder.processInsertMultiple(
            Seq::zero(), Scn::zero(), Time(), nullptr, nullptr,
            &insertUndo, &insertRedo, false, false, false);

    uint8_t deleteData[24]{};
    RedoLogRecord deleteUndo;
    initializeRows(deleteUndo, deleteData);
    deleteUndo.obj = 22;
    deleteUndo.fileOffset = FileOffset(512);
    RedoLogRecord deleteRedo;
    deleteRedo.clear();
    deleteRedo.obj = 22;
    deleteRedo.dataObj = 33;
    deleteRedo.bdba = 100;
    builder.processDeleteMultiple(
            Seq::zero(), Scn::zero(), Time(), nullptr, nullptr,
            &deleteUndo, &deleteRedo, false, false, false);

    builder.printCounts();
    return 0;
}
