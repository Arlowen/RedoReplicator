# RedoReplicator

RedoReplicator is an in-progress JDK 17 translation of OpenLogReplicator's Oracle redo change data capture engine. The implementation target and acceptance gates are defined in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

The current implementation covers parts of stages 3, 4 and 6. Parsed redo vectors
now enter an in-memory transaction buffer keyed by Oracle transaction slot. The
buffer supports interleaved transactions, begin/commit, complete rollback,
savepoint partial rollback, commit-order delivery, row-piece grouping and an
in-memory
low-watermark. Transactions can spill decoded entries to `data/tmp`-style
scratch files under a global memory limit; commit reads them in order, partial
rollback truncates the tail, and startup removes stale spill files before
low-watermark replay. Multi-block undo is merged across redo records, including
middle fragments and split field buffers, then decoded again before pairing it
with the business redo vector. Binary-vector integration tests cover that path
through transaction spill, partial rollback and commit. Runtime YAML/CLI wiring
now supplies the spill limit. The default CLI command opens H2, initializes the
selected-table and SYS dictionaries at the replay SCN, discovers redo
continuously and commits each complete LWN through JSONL fsync and the H2 safe
position. Quick multi-row INSERT and DELETE are expanded in original slot order
for both user JSON and SYS dictionary transactions. Text values retain their
dictionary `charsetId`; AL32UTF8, Oracle UTF8/CESU-8, AL16UTF16, ZHS16GBK,
all 14 upstream 7-bit character sets, all 101 upstream 8-bit character sets and
all eight generic 16-bit character sets, all six Japanese EUC/SJIS identities
and KO16KSCCS use upstream-compatible decoders across user values, DDL and SYS
dictionary changes, including NCHAR/NVARCHAR values. Inline BLOB/CLOB locators
are decoded directly. In-index and classic out-of-row locators reconstruct
direct-loader pages, KDLI fill fragments and `0A02/0A08/0A12` page indexes,
including orphan pages later bound to a parent transaction and transactions
spilled to disk. KDLI list-map chains and their incremental updates are also
reconstructed. The complete charset catalog and XDB dictionary families are not
complete, so the project is not production-ready yet. As in the upstream
Builder, Oracle compressed user rows are preserved losslessly as one RAW
`COMPRESSED` field rather than presented as decoded logical columns.

Strict YAML loading and the Picocli startup preflight are available. The loader
rejects unknown or duplicate keys, invalid table regular expressions, duplicate
redo path prefixes and state/output directories outside the installation root.
It resolves `state.transactionMemoryMb` into the transaction spill buffer under
`data/tmp`, uses the longest matching Oracle redo path prefix, and warns when
the configuration file is not mode `0600`. `--validate` connects with the
configured ordinary Oracle account and checks supported version, logging mode,
dictionary access, redo/archive mappings and local read access without starting
the capture loop.

Redo catalog discovery now reads the current incarnation's archived logs and
online log members with their redo thread, sequence and SCN range. The planner
selects an archive covering the configured start SCN for every active thread,
prefers continuous archived sequences before online redo, polls temporarily
missing files, and stops on a proven gap, timeout or database identity change.
A synchronous JDK `FileChannel` reader validates the selected file identity,
header, block number, sequence and checksum, resumes at a block-aligned offset,
distinguishes archive completion from online wait, and stops on truncation or
online overwrite. A streaming parser retains incomplete LWN blocks across read
batches, exposes each complete LWN with its safe position and low-watermark,
preserves pre-start transaction state and filters output by commit SCN. A
per-thread stream keeps open transactions across archive sequence switches,
waits for online redo growth and advances to the exact next sequence only after
the current file finishes. Startup uses the configured/current SCN only when H2
has no state; recovery ignores YAML, validates the Oracle identity and replays
from the earliest open-transaction low-watermark or the durable file offset.
RAC and multiple active redo threads remain out of scope; the runtime currently
requires a single active thread. The JSONL file layer itself now writes
`redo-000001.jsonl` style files, rolls only
between complete messages, fsyncs each LWN batch, truncates an uncommitted tail
to H2's safe byte offset, and refuses to start when the file is shorter than
that offset. User-table redo pairs can now be assembled into
typed before/after column bytes with row identity, supplemental images,
multi-piece value merging and primary-key placeholders. Compressed user-row
payloads retain their complete bytes as the upstream `COMPRESSED` RAW field.
Inline BLOB/CLOB locators are converted to their complete binary or text value;
in-index and classic out-of-row locators use verified transaction page indexes,
page counts and tail lengths, and stop capture if any referenced page or byte
range is missing. The 12+ style-1 extent, style-2 KDLI list-page and legacy
extent locator forms are reconstructed with byte-complete length checks. Other
typed bytes can
be converted to the fixed native JSON scalar forms for
text, NUMBER, DATE/TIMESTAMP, RAW, binary floating point, intervals, UROWID and
BOOLEAN. The fixed native JSON Builder emits separate begin, ordered DML/DDL
and commit messages plus optional checkpoint heartbeats, always including the
database name, and its byte messages are covered through JSONL fsync. Committed
user transactions
can now retain DML/DDL order from their redo entries, assemble supplemental row
pieces, aggregate numbered DDL fragments and feed the Builder directly. Their
table catalog is loaded from the latest live H2 schema versions at the requested
SCN, with pre-commit fallback for dropped objects. Missing schema, incomplete
DDL and unsupported row formats stop processing instead of silently losing a
change. Committed rows for all fifteen translated SYS dictionary tables are
routed through the system transaction overlay, never emitted as user JSON, and
publish complete schema versions or drop tombstones at commit. Startup-side
catalog loading requires the physical schema of every translated SYS table at
the target SCN. The LWN commit processor now writes and fsyncs the complete
JSONL batch before atomically storing schema versions, the low-watermark and the
single durable position in H2. The CLI now invokes that processor from a
continuous per-thread loop, waits when online redo has no complete LWN, switches
to the exact next sequence, and handles SIGTERM only between complete LWN
commits.

Oracle accounts are never created by the application or Docker Compose. Review
and manually execute [sql/configure_database.sql](sql/configure_database.sql),
then [sql/create_capture_user.sql](sql/create_capture_user.sql) in every PDB
that will be tested. Place that ordinary account in the supplied YAML
configuration.

## Build

Use JDK 17 and Maven 3.9 or later:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
mvn clean verify
```

The Maven package is directly runnable with its copied runtime dependencies:

```bash
java -jar target/redo-replicator-0.1.0-SNAPSHOT.jar --help
java -jar target/redo-replicator-0.1.0-SNAPSHOT.jar \
  --install-dir . --file conf/redo-replicator.yaml --validate
```

The default command performs the preflight and then starts continuous capture;
`--validate` exits after preflight without opening runtime state. The release
layout uses the same entry point through `bin/run.sh`; `bin/validate.sh` adds
`--validate`. `bin/start.sh`, `bin/stop.sh`, `bin/restart.sh` and `bin/status.sh`
manage one background process and its installation-local PID. `stop.sh` sends
SIGTERM and never escalates to `kill -9`; its default wait is 60 seconds.
Runtime startup also takes an OS file lock under `data/` before opening H2 or
clearing stale transaction spill, so a second process cannot touch the same
installation state. SIGTERM requests a stop at the next complete LWN boundary,
after JSONL fsync and H2 commit. After each successful H2 LWN commit, the process
atomically replaces non-authoritative `data/status.json`; `status.sh` displays
that snapshot after the PID state. Status write failures are logged but never
alter the H2 recovery position. During source-tree development, the scripts can
target the Maven artifact explicitly:

```bash
REDO_REPLICATOR_JAR="$PWD/target/redo-replicator-0.1.0-SNAPSHOT.jar" \
  bin/validate.sh --help
```

With capture stopped, `bin/backup.sh` creates a private ZIP under
`data/backups/`. It contains the closed H2 file, YAML, optional status snapshot
and a manifest with database identity and SHA-256 values. JSONL output and
`data/tmp/` transaction spill are deliberately excluded.

`bin/restore.sh <backup-file>` validates every packaged digest and both the
manifest and H2 identity against the connected Oracle incarnation before it
replaces anything. Existing H2, YAML and status files remain in a timestamped
`data/backups/restore-safety-*` directory. Because JSONL is not packaged, the
restored state keeps its safe redo SCN but starts at a new file number above all
existing JSONL files; historical output is neither deleted nor overwritten.

With capture stopped, `bin/rewind.sh --scn <SCN>` moves the single H2 safe
position backward. It rejects forward moves and validates the connected Oracle
identity, a readable redo/archive sequence covering the target, and complete
selected-table dictionary state at that SCN before changing H2. The previous H2
is retained under `data/backups/rewind-safety-*`; old JSONL files remain intact,
and resumed output starts in a new monotonically numbered file.

## Compare JSONL output

The stage 1 comparator checks JSONL line by line. JSON object key order and whitespace are ignored; array order, field types and values remain significant.

```bash
tools/compare-jsonl.sh expected.jsonl actual.jsonl
```

## Build the fixed C++ baseline

The baseline is exported into `target/` before CMake runs, because upstream generates `config.h` in its source tree. The reference checkout is never modified.

```bash
scripts/baseline/prepare-rapidjson.sh target/rapidjson
scripts/baseline/build-openlogreplicator.sh \
  /Users/pika/codex-cli-worker/OpenLogReplicator \
  target/rapidjson
```

The complete 8-bit Java catalog is mechanically regenerated from the same
fixed source checkout:

```bash
scripts/baseline/generate-character-set-8bit-catalog.py \
  /Users/pika/codex-cli-worker/OpenLogReplicator \
  src/main/resources/io/github/arlowen/redoreplicator/charset/oracle-8bit-catalog.tsv
```

The generic 16-bit catalog uses the corresponding generator and fixed checkout:

```bash
scripts/baseline/generate-character-set-16bit-catalog.py \
  /Users/pika/codex-cli-worker/OpenLogReplicator \
  src/main/resources/io/github/arlowen/redoreplicator/charset/oracle-16bit-catalog.tsv
```

The Japanese EUC/SJIS and Korean KSCCS tables are regenerated together:

```bash
scripts/baseline/generate-character-set-east-asian-catalog.py \
  /Users/pika/codex-cli-worker/OpenLogReplicator \
  src/main/resources/io/github/arlowen/redoreplicator/charset/oracle-east-asian-catalog.tsv
```

## Source migration coverage

The migration map is checked against the fixed OpenLogReplicator baseline:

```bash
tools/verify-migration-map.sh /Users/pika/codex-cli-worker/OpenLogReplicator
```

## License

RedoReplicator is licensed under AGPL-3.0-or-later. Direct translations retain OpenLogReplicator provenance and copyright notices.
