# Character-set parity fixture

`openlogreplicator-6bc92bc1.properties` was generated from the fixed isolated
OpenLogReplicator baseline with:

```bash
scripts/baseline/run-charset-probe.sh \
  target/openlogreplicator-baseline/source \
  target/rapidjson \
  target/charset-baseline.properties
```

Each value is the dot-separated lowercase hexadecimal Unicode code point
sequence emitted by the upstream decoder. The fixture covers AL32UTF8 (873),
Oracle UTF8/CESU-8 (871) and AL16UTF16 (2000).
