# RedoReplicator

RedoReplicator is an in-progress JDK 17 translation of OpenLogReplicator's Oracle redo change data capture engine. The implementation target and acceptance gates are defined in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

The project is currently implementing stage 3: initial Oracle dictionary loading,
schema history, H2 recovery state and transactional replay for all fifteen SYS
dictionary families used by OpenLogReplicator. LOB, partition, guard-column and
delayed-segment relationships are rebuilt into complete schema versions. The raw
redo row bridge and XDB dictionary families are not complete, so the project is
not ready to capture Oracle redo yet.

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
