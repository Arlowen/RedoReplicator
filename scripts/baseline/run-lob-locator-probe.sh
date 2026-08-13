#!/usr/bin/env bash
set -euo pipefail

expected_commit=6bc92bc1b89255fbc491e3080cb12a4c1dd8e832

if [[ $# -ne 3 ]]; then
    echo "Usage: $0 <isolated OpenLogReplicator source> <RapidJSON root> <output file>" >&2
    exit 2
fi

source_dir=$(cd "$1" && pwd)
rapidjson_dir=$(cd "$2" && pwd)
output_file=$3
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$script_dir/../.." && pwd)
probe_binary="$project_dir/target/lob-locator-probe"
commit_marker="$source_dir/.redoreplicator-baseline-commit"
build_dir="$source_dir/build"

if [[ ! -f "$commit_marker" ]]; then
    echo "Not an isolated RedoReplicator baseline snapshot: $source_dir" >&2
    exit 1
fi
actual_commit=$(<"$commit_marker")
if [[ "$actual_commit" != "$expected_commit" ]]; then
    echo "Baseline snapshot contains $actual_commit, expected $expected_commit" >&2
    exit 1
fi
if [[ ! -f "$rapidjson_dir/include/rapidjson/document.h" ]]; then
    echo "RapidJSON header not found under $rapidjson_dir/include" >&2
    exit 1
fi

object_files=()
while IFS= read -r object_file; do
    object_files+=("$object_file")
done < <(find "$build_dir/src/CMakeFiles" \
    -path '*/Lib*.dir/*.o' -type f | sort)
if [[ ${#object_files[@]} -eq 0 ]]; then
    echo "OpenLogReplicator baseline object files are missing under $build_dir" >&2
    exit 1
fi

c++ -std=c++17 -DCTXASSERT=0 \
    -I "$source_dir" \
    -I "$source_dir/src" \
    -I "$rapidjson_dir/include" \
    "$project_dir/scripts/baseline/lob-locator-probe.cpp" \
    "${object_files[@]}" \
    -pthread \
    -o "$probe_binary"

"$probe_binary" > "$output_file"
echo "LOB locator parity output: $output_file"
