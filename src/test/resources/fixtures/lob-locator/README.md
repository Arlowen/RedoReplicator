# LOB locator Builder parity fixture

`openlogreplicator-6bc92bc1.properties` is generated from the fixed isolated
OpenLogReplicator baseline with:

```bash
scripts/baseline/run-lob-locator-probe.sh \
  target/openlogreplicator-baseline/source \
  target/rapidjson \
  target/lob-locator.properties
```

The fixture covers both upstream inline locator encodings and an empty inline
value. Locator forms that reference transaction LOB pages are deliberately not
represented as inline values.
