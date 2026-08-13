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
now supplies the spill limit, but multi-row DML output, continuous online redo
routing and XDB dictionary families are not complete, so the project is not
ready to capture Oracle redo yet.

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
online overwrite. Parser buffering and the capture lifecycle are not wired to
the reader yet.

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

The default command performs the same preflight but currently stops before the
not-yet-implemented continuous redo discovery and capture loop. The release
layout uses the same entry point through `bin/run.sh`; `bin/validate.sh` adds
`--validate`. Runtime startup takes an OS file lock under `data/` before opening
H2 or clearing stale transaction spill, so a second process cannot touch the
same installation state. During source-tree development, the scripts can target
the Maven artifact explicitly:

```bash
REDO_REPLICATOR_JAR="$PWD/target/redo-replicator-0.1.0-SNAPSHOT.jar" \
  bin/validate.sh --help
```

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

## Source migration coverage

The migration map is checked against the fixed OpenLogReplicator baseline:

```bash
tools/verify-migration-map.sh /Users/pika/codex-cli-worker/OpenLogReplicator
```

## License

RedoReplicator is licensed under AGPL-3.0-or-later. Direct translations retain OpenLogReplicator provenance and copyright notices.
