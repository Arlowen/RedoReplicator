#!/usr/bin/env bash
set -euo pipefail

expected_commit=6bc92bc1b89255fbc491e3080cb12a4c1dd8e832

if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "Usage: $0 <isolated OpenLogReplicator source> <output file> [RapidJSON root]" >&2
    exit 2
fi

source_dir=$(cd "$1" && pwd)
output_file=$2
rapidjson_dir=""
if [[ $# -eq 3 ]]; then
    rapidjson_dir=$(cd "$3" && pwd)
fi
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$script_dir/../.." && pwd)
probe_binary="$project_dir/target/value-types-probe"

commit_marker="$source_dir/.redoreplicator-baseline-commit"
if [[ ! -f "$commit_marker" ]]; then
    echo "Not an isolated RedoReplicator baseline snapshot: $source_dir" >&2
    exit 1
fi
actual_commit=$(<"$commit_marker")
if [[ "$actual_commit" != "$expected_commit" ]]; then
    echo "Baseline snapshot contains $actual_commit, expected $expected_commit" >&2
    exit 1
fi

include_args=(-I "$source_dir")
if [[ -n "$rapidjson_dir" ]]; then
    include_args+=(-I "$rapidjson_dir/include")
fi

c++ -std=c++17 -DCTXASSERT=0 \
    "${include_args[@]}" \
    "$project_dir/scripts/baseline/value-types-probe.cpp" \
    "$source_dir/src/common/types/Data.cpp" \
    "$source_dir/src/common/exception/DataException.cpp" \
    "$source_dir/src/common/exception/RuntimeException.cpp" \
    -o "$probe_binary"

"$probe_binary" > "$output_file"
echo "Value type parity output: $output_file"
