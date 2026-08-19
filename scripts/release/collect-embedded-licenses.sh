#!/bin/sh

set -eu

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <third-party-license-directory>" >&2
    exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
DESTINATION=$1
JAR_TOOL=${REDO_REPLICATOR_JAR_TOOL:-$(command -v jar || true)}

if [ -z "$JAR_TOOL" ] || [ ! -x "$JAR_TOOL" ]; then
    echo "A JDK jar tool is required to collect embedded licenses" >&2
    exit 3
fi
mkdir -p "$DESTINATION/resolved"
STAGE=$(mktemp -d "${TMPDIR:-/tmp}/redoreplicator-license.XXXXXX")
trap 'rm -rf "$STAGE"' EXIT HUP INT TERM

for artifact in "$PROJECT_DIR"/target/lib/*.jar; do
    NAME=$(basename "$artifact" .jar)
    ARTIFACT_STAGE=$STAGE/$NAME
    mkdir -p "$ARTIFACT_STAGE"
    (cd "$ARTIFACT_STAGE" && "$JAR_TOOL" -xf "$artifact")
    MATCHES=$STAGE/$NAME.matches
    find "$ARTIFACT_STAGE" -type f \( \
        -iname 'LICENSE' -o -iname 'LICENSE.*' -o \
        -iname 'NOTICE' -o -iname 'NOTICE.*' -o \
        -iname '*THIRD*PARTY*' \
    \) -print > "$MATCHES"
    if [ ! -s "$MATCHES" ]; then
        continue
    fi
    mkdir -p "$DESTINATION/resolved/$NAME"
    while IFS= read -r source; do
        RELATIVE=${source#"$ARTIFACT_STAGE/"}
        FILE_NAME=$(printf '%s' "$RELATIVE" | tr '/ ' '__')
        cp "$source" "$DESTINATION/resolved/$NAME/$FILE_NAME"
    done < "$MATCHES"
done

echo "Embedded licenses: $DESTINATION/resolved"
