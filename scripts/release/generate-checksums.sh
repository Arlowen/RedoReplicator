#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
ARTIFACT_DIR=$PROJECT_DIR/target/distributions

if [ ! -d "$ARTIFACT_DIR" ]; then
    echo "Distribution directory does not exist: $ARTIFACT_DIR" >&2
    exit 2
fi

set -- "$ARTIFACT_DIR"/*.tar.gz "$ARTIFACT_DIR/SBOM.json"
for artifact in "$@"; do
    if [ ! -r "$artifact" ]; then
        echo "Release artifact is missing: $artifact" >&2
        exit 2
    fi
done

TEMP=$ARTIFACT_DIR/SHA256SUMS.tmp.$$
trap 'rm -f "$TEMP"' EXIT HUP INT TERM
cd "$ARTIFACT_DIR"
if command -v sha256sum >/dev/null 2>&1; then
    for artifact in "$@"; do
        sha256sum "$(basename "$artifact")" >> "$TEMP"
    done
else
    for artifact in "$@"; do
        shasum -a 256 "$(basename "$artifact")" >> "$TEMP"
    done
fi
mv "$TEMP" SHA256SUMS
trap - EXIT HUP INT TERM
echo "Checksums: $ARTIFACT_DIR/SHA256SUMS"
