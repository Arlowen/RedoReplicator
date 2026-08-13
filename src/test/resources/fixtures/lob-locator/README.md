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
value, an in-index locator reconstructed from one direct-loader LOB page and
a classic out-of-row locator reconstructed from its indexed page and length.
The 12+ style-1 extent list and style-2 KDLI list-page chain are also covered.
The legacy extent-list locator uses the same payload as an additional boundary.
