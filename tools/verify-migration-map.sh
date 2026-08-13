#!/usr/bin/env bash
set -euo pipefail

expected_commit=6bc92bc1b89255fbc491e3080cb12a4c1dd8e832

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <OpenLogReplicator source>" >&2
    exit 2
fi

source_dir=$(cd "$1" && pwd)
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$script_dir/.." && pwd)
map_file="$project_dir/migration/openlogreplicator-source-map.tsv"

actual_commit=$(git -C "$source_dir" rev-parse HEAD)
if [[ "$actual_commit" != "$expected_commit" ]]; then
    echo "OpenLogReplicator must be checked out at $expected_commit, found $actual_commit" >&2
    exit 1
fi

header=$(head -n 1 "$map_file")
expected_header=$'source_path\ttarget_java_type\tstatus\tnotes'
if [[ "$header" != "$expected_header" ]]; then
    echo "Unexpected migration map header: $header" >&2
    exit 1
fi

temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT

(
    cd "$source_dir"
    find src -type f \( -name '*.cpp' -o -name '*.h' \) -print | LC_ALL=C sort
) > "$temp_dir/source-files"

awk -F '\t' 'NR > 1 { print $1 }' "$map_file" | LC_ALL=C sort > "$temp_dir/mapped-files"
awk -F '\t' 'NR > 1 && $3 !~ /^(pending|translated|excluded)$/ {
    print "Invalid status at line " NR ": " $3 > "/dev/stderr";
    invalid = 1
}
NR > 1 && $3 != "excluded" && $2 == "-" {
    print "Missing Java target at line " NR > "/dev/stderr";
    invalid = 1
}
END { exit invalid }' "$map_file"

duplicates=$(awk -F '\t' 'NR > 1 { print $1 }' "$map_file" | LC_ALL=C sort | uniq -d)
if [[ -n "$duplicates" ]]; then
    echo "Duplicate source paths in migration map:" >&2
    echo "$duplicates" >&2
    exit 1
fi

missing=$(comm -23 "$temp_dir/source-files" "$temp_dir/mapped-files")
stale=$(comm -13 "$temp_dir/source-files" "$temp_dir/mapped-files")
if [[ -n "$missing" ]]; then
    echo "Source files missing from migration map:" >&2
    echo "$missing" >&2
    exit 1
fi
if [[ -n "$stale" ]]; then
    echo "Migration map entries absent from baseline:" >&2
    echo "$stale" >&2
    exit 1
fi

pending=$(awk -F '\t' 'NR > 1 && $3 == "pending" { count++ } END { print count + 0 }' "$map_file")
translated=$(awk -F '\t' 'NR > 1 && $3 == "translated" { count++ } END { print count + 0 }' "$map_file")
excluded=$(awk -F '\t' 'NR > 1 && $3 == "excluded" { count++ } END { print count + 0 }' "$map_file")
echo "Migration map covers all source files: pending=$pending translated=$translated excluded=$excluded"
