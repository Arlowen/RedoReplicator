# Multi-row Builder parity fixture

`openlogreplicator-6bc92bc1.properties` is generated from the fixed
OpenLogReplicator baseline with:

```bash
scripts/baseline/run-multi-row-probe.sh \
  target/openlogreplicator-baseline/source \
  target/rapidjson \
  target/multi-row-baseline.properties
```

The probe invokes upstream `Builder::processInsertMultiple` and
`Builder::processDeleteMultiple` with two synthetic Oracle row images. The
fixture records emitted row order, slot and raw column bytes.
