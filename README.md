# RedoReplicator

RedoReplicator is an in-progress JDK 17 translation of OpenLogReplicator's Oracle redo change data capture engine. The implementation target and acceptance gates are defined in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

The current implementation covers parts of stages 3 and 4. Parsed redo vectors
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
for the spill limit, multi-row DML output, online redo routing and XDB
dictionary families are not complete, so the project is not ready to capture
Oracle redo yet.

Oracle accounts are never created by the application or Docker Compose. Review
and manually execute [sql/configure_database.sql](sql/configure_database.sql),
then [sql/create_capture_user.sql](sql/create_capture_user.sql) in every PDB
that will be tested. Place that ordinary account in the future YAML
configuration.

## Build

Use JDK 17 and Maven 3.9 or later:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
mvn clean verify
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
