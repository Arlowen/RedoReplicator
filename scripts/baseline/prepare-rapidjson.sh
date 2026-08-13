#!/usr/bin/env bash
set -euo pipefail

expected_commit=f54b0e47a08782a6131cc3d60f94d038fa6e0a51
repository=https://github.com/Tencent/rapidjson.git

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <destination>" >&2
    exit 2
fi

destination=$1
if [[ -e "$destination" ]]; then
    destination=$(cd "$destination" && pwd)
    actual_commit=$(git -C "$destination" rev-parse HEAD 2>/dev/null || true)
    if [[ "$actual_commit" != "$expected_commit" ]]; then
        echo "RapidJSON destination contains ${actual_commit:-non-git data}, expected $expected_commit" >&2
        exit 1
    fi
    echo "RapidJSON is already prepared at $destination"
    exit 0
fi

git clone --filter=blob:none --no-checkout "$repository" "$destination"
git -C "$destination" checkout --detach "$expected_commit"
echo "RapidJSON prepared at $destination"
