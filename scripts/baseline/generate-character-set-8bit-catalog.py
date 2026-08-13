#!/usr/bin/env python3
"""Generate the Java 8-bit catalog from the fixed OpenLogReplicator source."""

from pathlib import Path
import re
import subprocess
import sys


EXPECTED_COMMIT = "6bc92bc1b89255fbc491e3080cb12a4c1dd8e832"
ARRAY_PATTERN = re.compile(
    r"typeUnicode16 CharacterSet8bit::unicode_map_([A-Z0-9]+)"
    r"\[(128|256)\]\{(.*?)\n    \};",
    re.DOTALL,
)
REGISTRATION_PATTERN = re.compile(
    r'characterMap\[(\d+)\] = new CharacterSet8bit\("([A-Z0-9]+)", '
    r"CharacterSet8bit::unicode_map_([A-Z0-9]+)(, true)?\);"
)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(
            "Usage: generate-character-set-8bit-catalog.py "
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
    maps_source = (
        source_root / "src/locales/CharacterSet8bit.cpp"
    ).read_text()
    maps = {}
    for match in ARRAY_PATTERN.finditer(maps_source):
        name = match.group(1)
        expected_size = int(match.group(2))
        values = [
            int(value, 16)
            for value in re.findall(r"0x([0-9A-F]{4})", match.group(3))
        ]
        if len(values) != expected_size:
            raise SystemExit(
                f"Map {name} has {len(values)} values, expected {expected_size}"
            )
        maps[name] = values

    registrations = REGISTRATION_PATTERN.findall(locales_source)
    if len(registrations) != 101 or len(maps) != 101:
        raise SystemExit(
            f"Expected 101 maps and registrations, found "
            f"{len(maps)} maps and {len(registrations)} registrations"
        )

    lines = [
        "# Generated direct translation of OpenLogReplicator "
        "src/locales/CharacterSet8bit.cpp and Locales.cpp.",
        f"# source_commit={EXPECTED_COMMIT}",
        "# id\\tname\\t256 four-digit hexadecimal Unicode code points",
    ]
    ids = set()
    names = set()
    for id_text, name, map_name, custom_ascii in registrations:
        character_id = int(id_text)
        if character_id in ids or name in names:
            raise SystemExit(f"Duplicate 8-bit identity: {character_id} {name}")
        ids.add(character_id)
        names.add(name)

        values = maps[map_name]
        if custom_ascii:
            if len(values) != 256:
                raise SystemExit(f"Custom ASCII map {map_name} is not 256 entries")
            full_map = values
        else:
            if len(values) != 128:
                raise SystemExit(f"High-bit map {map_name} is not 128 entries")
            full_map = list(range(128)) + values
        encoded_map = "".join(f"{value:04x}" for value in full_map)
        lines.append(f"{character_id}\t{name}\t{encoded_map}")

    output_file.parent.mkdir(parents=True, exist_ok=True)
    temporary_file = output_file.with_suffix(output_file.suffix + ".tmp")
    temporary_file.write_text("\n".join(lines) + "\n")
    temporary_file.replace(output_file)


if __name__ == "__main__":
    main()
