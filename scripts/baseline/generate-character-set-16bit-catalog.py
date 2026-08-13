#!/usr/bin/env python3
"""Generate the Java 16-bit catalog from the fixed OpenLogReplicator source."""

from pathlib import Path
import re
import subprocess
import sys


EXPECTED_COMMIT = "6bc92bc1b89255fbc491e3080cb12a4c1dd8e832"
ARRAY_PATTERN = re.compile(
    r"typeUnicode16 CharacterSet16bit::unicode_map_([A-Za-z0-9_]+)"
    r"\[[^]]+\]\{(.*?)\n    \};",
    re.DOTALL,
)
RANGE_PATTERN = re.compile(
    r"static constexpr uint64_t ([A-Z0-9]+)_b([12])_(min|max)"
    r"\{0x([0-9A-F]+)\};"
)
REGISTRATION_PATTERN = re.compile(
    r'characterMap\[(\d+)\] = new CharacterSet16bit\("([A-Z0-9]+)", '
    r"CharacterSet16bit::unicode_map_([A-Za-z0-9_]+), "
    r"CharacterSet16bit::([A-Z0-9]+)_b1_min,\s*"
    r"CharacterSet16bit::([A-Z0-9]+)_b1_max,\s*"
    r"CharacterSet16bit::([A-Z0-9]+)_b2_min,\s*"
    r"CharacterSet16bit::([A-Z0-9]+)_b2_max\);"
)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(
            "Usage: generate-character-set-16bit-catalog.py "
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

    locales_source = (source_root / "src/locales/Locales.cpp").read_text()
    header_source = (
        source_root / "src/locales/CharacterSet16bit.h"
    ).read_text()
    maps_source = (
        source_root / "src/locales/CharacterSet16bit.cpp"
    ).read_text()

    ranges = {}
    for name, byte_number, bound, value in RANGE_PATTERN.findall(
            header_source):
        ranges.setdefault(name, {})[f"b{byte_number}_{bound}"] = int(
            value, 16)

    maps = {}
    for name, body in ARRAY_PATTERN.findall(maps_source):
        maps[name] = [
            int(value, 16)
            for value in re.findall(r"0x([0-9A-F]{4})", body)
        ]

    registrations = REGISTRATION_PATTERN.findall(locales_source)
    if len(registrations) != 8 or len(maps) != 8 or len(ranges) != 8:
        raise SystemExit(
            f"Expected 8 maps, ranges and registrations, found "
            f"{len(maps)} maps, {len(ranges)} ranges and "
            f"{len(registrations)} registrations"
        )

    lines = [
        "# Generated direct translation of OpenLogReplicator "
        "src/locales/CharacterSet16bit.cpp and Locales.cpp.",
        f"# source_commit={EXPECTED_COMMIT}",
        "# id\\tname\\tb1min\\tb1max\\tb2min\\tb2max\\tmap",
    ]
    for registration in registrations:
        id_text, name, map_name, *range_names = registration
        if len(set(range_names)) != 1:
            raise SystemExit(f"Mixed range constants for {name}")
        range_name = range_names[0]
        character_range = ranges[range_name]
        expected_size = (
            character_range["b1_max"] - character_range["b1_min"] + 1
        ) * (
            character_range["b2_max"] - character_range["b2_min"] + 1
        )
        values = maps[map_name]
        if len(values) != expected_size:
            raise SystemExit(
                f"Map {map_name} has {len(values)} values, "
                f"expected {expected_size}"
            )
        encoded_map = "".join(f"{value:04x}" for value in values)
        fields = [
            id_text,
            name,
            str(character_range["b1_min"]),
            str(character_range["b1_max"]),
            str(character_range["b2_min"]),
            str(character_range["b2_max"]),
            encoded_map,
        ]
        lines.append("\t".join(fields))

    output_file.parent.mkdir(parents=True, exist_ok=True)
    temporary_file = output_file.with_suffix(output_file.suffix + ".tmp")
    temporary_file.write_text("\n".join(lines) + "\n")
    temporary_file.replace(output_file)


if __name__ == "__main__":
    main()
