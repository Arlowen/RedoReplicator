#!/usr/bin/env bash
set -euo pipefail

expected_commit=6bc92bc1b89255fbc491e3080cb12a4c1dd8e832
expected_rapidjson_commit=f54b0e47a08782a6131cc3d60f94d038fa6e0a51

if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "Usage: $0 <OpenLogReplicator source> <RapidJSON root> [Oracle Instant Client root]" >&2
    exit 2
fi

source_dir=$(cd "$1" && pwd)
rapidjson_dir=$(cd "$2" && pwd)
oracle_client_dir=""
if [[ $# -eq 3 ]]; then
    oracle_client_dir=$(cd "$3" && pwd)
fi

actual_commit=$(git -C "$source_dir" rev-parse HEAD)
if [[ "$actual_commit" != "$expected_commit" ]]; then
    echo "OpenLogReplicator must be checked out at $expected_commit, found $actual_commit" >&2
    exit 1
fi

if [[ ! -f "$rapidjson_dir/include/rapidjson/document.h" ]]; then
    echo "RapidJSON header not found under $rapidjson_dir/include" >&2
    exit 1
fi
rapidjson_commit=$(git -C "$rapidjson_dir" rev-parse HEAD 2>/dev/null || true)
if [[ "$rapidjson_commit" != "$expected_rapidjson_commit" ]]; then
    echo "RapidJSON must be checked out at $expected_rapidjson_commit, found ${rapidjson_commit:-non-git source}" >&2
    exit 1
fi

baseline_dir=${OPENLOG_REPLICATOR_BUILD_DIR:-"$PWD/target/openlogreplicator-baseline"}
baseline_source_dir="$baseline_dir/source"
build_dir="$baseline_source_dir/build"
commit_marker="$baseline_source_dir/.redoreplicator-baseline-commit"

if [[ -f "$commit_marker" ]]; then
    snapshot_commit=$(<"$commit_marker")
    if [[ "$snapshot_commit" != "$expected_commit" ]]; then
        echo "Baseline snapshot contains $snapshot_commit, expected $expected_commit" >&2
        exit 1
    fi
elif [[ -e "$baseline_source_dir" ]]; then
    echo "Baseline source directory exists without a commit marker: $baseline_source_dir" >&2
    exit 1
else
    mkdir -p "$baseline_source_dir"
    git -C "$source_dir" archive "$expected_commit" | tar -x -C "$baseline_source_dir"
    printf '%s\n' "$expected_commit" > "$commit_marker"
fi

cmake_args=(
    -S "$baseline_source_dir"
    -B "$build_dir"
    -DCMAKE_BUILD_TYPE=Release
    -DWITH_RAPIDJSON="$rapidjson_dir"
)

if [[ -n "$oracle_client_dir" ]]; then
    if [[ ! -d "$oracle_client_dir/sdk/include" ]]; then
        echo "Oracle Instant Client SDK not found under $oracle_client_dir/sdk/include" >&2
        exit 1
    fi
    cmake_args+=("-DWITH_OCI=$oracle_client_dir")
fi

cmake "${cmake_args[@]}"
cmake --build "$build_dir" --config Release --parallel

echo "Baseline binary: $build_dir/OpenLogReplicator"
