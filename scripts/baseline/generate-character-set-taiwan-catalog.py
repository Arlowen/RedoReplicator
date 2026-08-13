#!/usr/bin/env python3
"""Generate Taiwan character-set maps from fixed OpenLogReplicator sources."""

from pathlib import Path
import re
import subprocess
import sys


EXPECTED_COMMIT = "6bc92bc1b89255fbc491e3080cb12a4c1dd8e832"
ARRAY_PATTERN = re.compile(
    r"typeUnicode(16|32) [A-Za-z0-9]+::unicode_map_"
    r"([A-Za-z0-9_]+)\[[^]]+\]\s*\{(.*?)\n    \};",
    re.DOTALL,
)
EXPECTED_MAPS = {
    "ZHT32EUC_2b": (4, (0xFD - 0xA1 + 1) * (0xFE - 0xA1 + 1)),
    "ZHT32EUC_4b": (
        4,
        (0xAE - 0xA2 + 1) * (0xF2 - 0xA1 + 1) * (0xFE - 0xA1 + 1),
    ),
    "ZHT32TRIS_4b": (
        4,
        (0xAE - 0xA1 + 1) * (0xFE - 0xA1 + 1) ** 2,
    ),
    "ZHT16HKSCS31_2b": (
        8,
        (0xFE - 0x81 + 1) * (0xFE - 0x40 + 1),
    ),
}


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(
            "Usage: generate-character-set-taiwan-catalog.py "
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
        "CharacterSetZHT32EUC.cpp",
        "CharacterSetZHT32TRIS.cpp",
        "CharacterSetZHT16HKSCS31.cpp",
    ):
        source = (source_root / "src/locales" / source_name).read_text()
        for type_width, name, body in ARRAY_PATTERN.findall(source):
            hex_digits = 4
            if type_width == "32":
                hex_digits = 8
            values = re.findall(
                rf"0x([0-9A-Fa-f]{{{hex_digits}}})", body
            )
            maps[name] = (hex_digits, values)

    if maps.keys() != EXPECTED_MAPS.keys():
        raise SystemExit(
            f"Expected maps {sorted(EXPECTED_MAPS)}, found {sorted(maps)}"
        )

    lines = [
        "# Generated direct translation of the OpenLogReplicator Taiwan "
        "character-set tables.",
        f"# source_commit={EXPECTED_COMMIT}",
        "# map_name\\tfixed-width hexadecimal Unicode code points",
    ]
    for name, (expected_digits, expected_size) in EXPECTED_MAPS.items():
        hex_digits, values = maps[name]
        if hex_digits != expected_digits or len(values) != expected_size:
            raise SystemExit(
                f"Map {name} has width {hex_digits} and {len(values)} "
                f"values, expected width {expected_digits} and "
                f"{expected_size} values"
            )
        lines.append(f"{name}\t{''.join(value.lower() for value in values)}")

    output_file.parent.mkdir(parents=True, exist_ok=True)
    temporary_file = output_file.with_suffix(output_file.suffix + ".tmp")
    temporary_file.write_text("\n".join(lines) + "\n")
    temporary_file.replace(output_file)


if __name__ == "__main__":
    main()
