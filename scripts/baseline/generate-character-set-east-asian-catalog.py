#!/usr/bin/env python3
"""Generate Japanese and Korean maps from fixed OpenLogReplicator sources."""

from pathlib import Path
import re
import subprocess
import sys


EXPECTED_COMMIT = "6bc92bc1b89255fbc491e3080cb12a4c1dd8e832"
ARRAY_PATTERN = re.compile(
    r"typeUnicode16 [A-Za-z0-9]+::unicode_map_([A-Za-z0-9_]+)"
    r"\[[^]]+\]\{(.*?)\n    \};",
    re.DOTALL,
)
EXPECTED_SIZES = {
    "JA16EUC_2b": (0xFE - 0x8E + 1) * (0xFE - 0xA1 + 1),
    "JA16EUC_3b": (0xFE - 0xA1 + 1) * (0xFE - 0xA1 + 1),
    "JA16SJIS_2b": (0xFC - 0x81 + 1) * (0xFC - 0x40 + 1),
    "KO16KSCCS_2b": (0xF9 - 0x84 + 1) * (0xFE - 0x31 + 1),
}


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(
            "Usage: generate-character-set-east-asian-catalog.py "
            "<OpenLogReplicator root> <output file>"
        )

    source_root = Path(sys.argv[1]).resolve()
    output_file = Path(sys.argv[2]).resolve()
    actual_commit = subprocess.check_output(
        ["git", "-C", str(source_root), "rev-parse", "HEAD"],
        text=True,
    ).strip()
    if actual_commit != EXPECTED_COMMIT:
        raise SystemExit(
            f"OpenLogReplicator must be checked out at {EXPECTED_COMMIT}, "
            f"found {actual_commit}"
        )

    maps = {}
    for source_name in (
        "CharacterSetJA16EUC.cpp",
        "CharacterSetJA16SJIS.cpp",
        "CharacterSetKO16KSCCS.cpp",
    ):
        source = (source_root / "src/locales" / source_name).read_text()
        for name, body in ARRAY_PATTERN.findall(source):
            maps[name] = [
                int(value, 16)
                for value in re.findall(r"0x([0-9A-F]{4})", body)
            ]

    if maps.keys() != EXPECTED_SIZES.keys():
        raise SystemExit(
            f"Expected maps {sorted(EXPECTED_SIZES)}, found {sorted(maps)}"
        )

    lines = [
        "# Generated direct translation of OpenLogReplicator Japanese and "
        "Korean character-set tables.",
        f"# source_commit={EXPECTED_COMMIT}",
        "# map_name\\tfour-digit hexadecimal Unicode code points",
    ]
    for name, expected_size in EXPECTED_SIZES.items():
        values = maps[name]
        if len(values) != expected_size:
            raise SystemExit(
                f"Map {name} has {len(values)} values, expected {expected_size}"
            )
        encoded_map = "".join(f"{value:04x}" for value in values)
        lines.append(f"{name}\t{encoded_map}")

    output_file.parent.mkdir(parents=True, exist_ok=True)
    temporary_file = output_file.with_suffix(output_file.suffix + ".tmp")
    temporary_file.write_text("\n".join(lines) + "\n")
    temporary_file.replace(output_file)


if __name__ == "__main__":
    main()
