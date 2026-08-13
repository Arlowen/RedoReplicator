# OpenLogReplicator migration map

`openlogreplicator-source-map.tsv` contains one row for every C++ source or header under the fixed OpenLogReplicator baseline `src/` directory.

Statuses:

- `pending`: the source behavior is in scope but its Java target is not complete.
- `translated`: the Java target is implemented and covered by parity tests.
- `excluded`: the source belongs to an explicitly excluded feature or is replaced by a JDK facility.

A row may move from `pending` to `translated` only when all source behavior represented by that row has a Java implementation and parity evidence. Header and implementation rows can point to the same Java type, but both rows must be updated.

Run the coverage verifier against the exact source checkout:

```bash
tools/verify-migration-map.sh /Users/pika/codex-cli-worker/OpenLogReplicator
```

The verifier rejects a wrong source commit, missing or stale paths, duplicate rows, invalid statuses and pending rows without Java targets.
