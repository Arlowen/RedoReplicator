/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.redo.parser;

import io.github.arlowen.redoreplicator.error.RedoLogException;
import io.github.arlowen.redoreplicator.redo.RedoBinaryTestSupport;
import io.github.arlowen.redoreplicator.redo.common.RedoLogRecord;
import io.github.arlowen.redoreplicator.redo.common.RedoTime;
import io.github.arlowen.redoreplicator.redo.common.Scn;
import io.github.arlowen.redoreplicator.redo.common.Seq;
import io.github.arlowen.redoreplicator.redo.transaction.CommittedRedoTransaction;
import io.github.arlowen.redoreplicator.redo.transaction.RedoTransactionBuffer;
import io.github.arlowen.redoreplicator.schema.ColumnSchema;
import io.github.arlowen.redoreplicator.schema.OracleColumnType;
import io.github.arlowen.redoreplicator.schema.SysUser;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryRedoBridge;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryRedoChange;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryState;
import io.github.arlowen.redoreplicator.schema.SystemDictionaryTable;
import io.github.arlowen.redoreplicator.schema.SystemTransactionManager;
import io.github.arlowen.redoreplicator.schema.TableSchema;
import io.github.arlowen.redoreplicator.schema.TableSchemaJsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedoParserTest {
    private static final long OBJECT_ID = 100;
    private static final long DATA_OBJECT_ID = 101;
    private static final long BLOCK_ADDRESS = 0xF100_0001L;
    private static final int SLOT = 7;

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesAndCommitsSystemDictionaryTransaction() throws Exception {
        try (RedoTransactionBuffer buffer = new RedoTransactionBuffer(
                temporaryDirectory, 1)) {
            RedoParser parser = new RedoParser(
                    ByteOrder.LITTLE_ENDIAN,
                    RedoLogRecord.REDO_VERSION_19_0,
                    512,
                    buffer);
            AssembledLwn data = lwn(
                    vector(0x0502, 1, beginField()),
                    vector(0x0501, 1,
                            undoBlock(), ktuBlock(), ktbNoOperation(),
                            deleteRowPiece(), supplementalHeader()),
                    vector(0x0B02, 0,
                            ktbNoOperation(), insertRowPiece(),
                            number(12),
                            "APP".getBytes(StandardCharsets.UTF_8)));

            assertTrue(parser.process(data, Seq.of(10), 1).isEmpty());
            assertEquals(1, buffer.spilledTransactionCount());
            assertEquals(1, spillFileCount());

            List<CommittedRedoTransaction> committed = parser.process(
                    lwn(vector(0x0504, 1, commitField())),
                    Seq.of(10), 1);

            assertEquals(1, committed.size());
            CommittedRedoTransaction transaction = committed.get(0);
            assertEquals("0x0001.002.00000003",
                    transaction.xid().toString());
            assertEquals(1, transaction.rowGroups().size());
            assertEquals(Scn.of(200), transaction.commitPosition().scn());
            assertTrue(parser.transactionBuffer().lowWatermark().isEmpty());
            assertEquals(0, spillFileCount());
            RedoLogRecord spilledUndo = transaction.entries().get(0).first();
            RedoLogRecord spilledRedo = transaction.entries().get(0)
                    .second().orElseThrow();
            assertEquals(spilledUndo.size, spilledUndo.data().length);
            assertEquals(spilledRedo.size, spilledRedo.data().length);
            assertEquals(0, spilledUndo.dataOffset());
            assertEquals(0, spilledRedo.dataOffset());

            SystemDictionaryRedoBridge bridge =
                    new SystemDictionaryRedoBridge(ByteOrder.LITTLE_ENDIAN);
            SystemDictionaryRedoChange change = bridge.decode(
                    SystemDictionaryTable.USER,
                    userTable(),
                    transaction.rowGroups().get(0));
            SystemTransactionManager manager = new SystemTransactionManager(
                    SystemDictionaryState.empty(),
                    "FREEPDB1", 873, 2000,
                    StandardCharsets.UTF_8,
                    new TableSchemaJsonCodec());
            manager.apply(change.xid(), change.change());
            SysUser user = manager.commit(
                    change.xid(), transaction.commitPosition().scn())
                    .dictionaryState().users().get(0);

            assertEquals(12, user.userId());
            assertEquals("APP", user.name());
        }
    }

    private long spillFileCount() throws Exception {
        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            return files.count();
        }
    }

    @Test
    void stopsOnUnknownAndEncryptedVectors() {
        RedoParser parser = new RedoParser(
                ByteOrder.LITTLE_ENDIAN,
                RedoLogRecord.REDO_VERSION_19_0,
                512);
        RedoLogException unknown = assertThrows(
                RedoLogException.class,
                () -> parser.process(
                        lwn(vector(0x7F7F, 0)), Seq.of(10), 1));
        assertEquals(50057, unknown.getErrorCode());

        byte[] encryptedVector = vector(0x0502, 1, beginField());
        encryptedVector[21] = (byte) RedoLogRecord.TYP_ENCRYPTED_TABLESPACE;
        RedoLogException encrypted = assertThrows(
                RedoLogException.class,
                () -> parser.process(
                        lwn(encryptedVector), Seq.of(10), 1));
        assertEquals(50057, encrypted.getErrorCode());
        assertTrue(encrypted.getMessage().contains("Encrypted"));
    }

    private static AssembledLwn lwn(byte[]... vectors) {
        byte[] recordData = redoRecord(vectors);
        LwnMember member = new LwnMember(
                16, Scn.of(200), recordData.length, 100, 1);
        AssembledRedoRecord record = new AssembledRedoRecord(
                member, recordData);
        return new AssembledLwn(
                100, 101, Scn.of(200), RedoTime.of(2),
                List.of(), List.of(record));
    }

    private static byte[] redoRecord(byte[]... vectors) {
        int size = 24;
        for (byte[] vector : vectors) {
            size += vector.length;
        }
        byte[] data = new byte[size];
        RedoBinaryTestSupport.writeUnsignedInt(
                data, 0, size, ByteOrder.LITTLE_ENDIAN);
        data[4] = 0x01;
        int offset = 24;
        for (byte[] vector : vectors) {
            System.arraycopy(vector, 0, data, offset, vector.length);
            offset += vector.length;
        }
        return data;
    }

    private static byte[] vector(
            int opCode, int undoSegment, byte[]... fields) {
        int fieldListLength = 2 + fields.length * 2;
        int fieldPosition = 32 + ((fieldListLength + 2) & 0xFFFC);
        int size = fieldPosition;
        for (byte[] field : fields) {
            size += align4(field.length);
        }
        byte[] vector = new byte[size];
        vector[0] = (byte) (opCode >>> 8);
        vector[1] = (byte) opCode;
        RedoBinaryTestSupport.writeUnsignedShort(
                vector, 2, 15 + undoSegment * 2,
                ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                vector, 32, fieldListLength, ByteOrder.LITTLE_ENDIAN);
        int position = fieldPosition;
        for (int index = 0; index < fields.length; index++) {
            byte[] field = fields[index];
            RedoBinaryTestSupport.writeUnsignedShort(
                    vector, 34 + index * 2,
                    field.length, ByteOrder.LITTLE_ENDIAN);
            System.arraycopy(field, 0, vector, position, field.length);
            position += align4(field.length);
        }
        return vector;
    }

    private static int align4(int size) {
        return (size + 3) & 0xFFFC;
    }

    private static byte[] beginField() {
        byte[] field = new byte[32];
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 0, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, 3, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] commitField() {
        byte[] field = new byte[20];
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 0, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, 3, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] undoBlock() {
        byte[] field = new byte[20];
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 8, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 10, 2, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 12, 3, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] ktuBlock() {
        byte[] field = new byte[24];
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, OBJECT_ID, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 4, DATA_OBJECT_ID, ByteOrder.LITTLE_ENDIAN);
        field[16] = 0x0B;
        field[17] = 0x01;
        return field;
    }

    private static byte[] ktbNoOperation() {
        byte[] field = new byte[8];
        field[0] = 0x06;
        return field;
    }

    private static byte[] deleteRowPiece() {
        byte[] field = kdoBase(20, RedoLogRecord.OP_DRP);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 16, SLOT, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] supplementalHeader() {
        byte[] field = new byte[26];
        field[1] = (byte) RedoLogRecord.FB_L;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 8, 1, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 20, BLOCK_ADDRESS, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 24, SLOT, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] insertRowPiece() {
        byte[] field = kdoBase(48, RedoLogRecord.OP_IRP);
        field[16] = (byte) RedoLogRecord.FB_F;
        field[18] = 2;
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 40, 100, ByteOrder.LITTLE_ENDIAN);
        RedoBinaryTestSupport.writeUnsignedShort(
                field, 42, SLOT, ByteOrder.LITTLE_ENDIAN);
        return field;
    }

    private static byte[] kdoBase(int size, int operation) {
        byte[] field = new byte[size];
        RedoBinaryTestSupport.writeUnsignedInt(
                field, 0, BLOCK_ADDRESS, ByteOrder.LITTLE_ENDIAN);
        field[10] = (byte) operation;
        return field;
    }

    private static byte[] number(int value) {
        return new byte[]{(byte) 0xC1, (byte) (value + 1)};
    }

    private static TableSchema userTable() {
        return new TableSchema(
                "FREEPDB1", "SYS", "USER$",
                OBJECT_ID, DATA_OBJECT_ID, 0, 0, 0,
                List.of(
                        column(1, "USER#", OracleColumnType.NUMBER),
                        column(2, "NAME", OracleColumnType.VARCHAR)),
                List.of(), List.of());
    }

    private static ColumnSchema column(
            int segmentColumn, String name, OracleColumnType type) {
        return new ColumnSchema(
                segmentColumn, -1, segmentColumn, segmentColumn,
                name, type, 128, -1, -1, 0, 0,
                true, false, false, false, false,
                false, false, false, false);
    }
}
