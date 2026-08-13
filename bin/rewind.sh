#!/bin/sh

set -eu
umask 077

if [ "$#" -ne 2 ] || [ "$1" != "--scn" ]; then
    echo "Usage: $0 --scn <SCN>" >&2
    exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$SCRIPT_DIR/run.sh" --rewind "$2"
