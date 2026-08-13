# Compressed-row Builder parity fixture

`openlogreplicator-6bc92bc1.properties` was generated from the fixed isolated
OpenLogReplicator baseline with:

```bash
scripts/baseline/run-compressed-row-probe.sh \
  target/openlogreplicator-baseline/source \
  target/rapidjson \
  target/compressed-row.properties
```

The upstream Builder does not expand Oracle compressed row bytes into logical
columns. It preserves the complete payload as a RAW field named `COMPRESSED`;
the Java output follows the same lossless boundary.
