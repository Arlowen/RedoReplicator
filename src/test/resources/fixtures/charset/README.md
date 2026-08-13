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
Oracle UTF8/CESU-8 (871), AL16UTF16 (2000), and ZHS16GBK (852). The
`zhs16gbk.map_fnv1a64` value covers every upstream two-byte table entry in
byte order. The WE8MSWIN1252 (178) digest covers all 256 byte values, so the
compact fixture detects mapping-table drift without committing large tables.
The `seven.<id>.map_fnv1a64` values cover all 14 upstream 7-bit character sets
across every possible input byte, including the upstream high-bit masking rule.
The `eight.<id>.map_fnv1a64` values cover all 101 registered upstream 8-bit
character sets across every possible input byte, including the four custom
ASCII maps. The adjacent `eight.<id>.name` values also fix every Oracle
registration identity.
The `sixteen.<id>` values fix all eight generic 16-bit identities, every
single-byte truncated input and all 65,536 possible byte pairs per identity.
The `east.<id>` values fix all six Japanese EUC/SJIS identities and KO16KSCCS,
including every single byte and byte pair. The Japanese EUC identities also
cover the complete 94 by 94 valid three-byte rectangle beginning with `8F`.
The `gb18030.*` values fix ZHS32GB18030 identity and consumption samples, all
single bytes and byte pairs, all 50,400 valid group-1 four-byte entries and all
1,058,400 valid supplementary group-2 entries.
