# LOB locator Builder parity fixture

`openlogreplicator-6bc92bc1.properties` is generated from the fixed isolated
OpenLogReplicator baseline with:

```bash
scripts/baseline/run-lob-locator-probe.sh \
  target/openlogreplicator-baseline/source \
  target/rapidjson \
  target/lob-locator.properties
```

The fixture covers both upstream inline locator encodings, an empty inline
value and an in-index locator reconstructed from one direct-loader LOB page.
