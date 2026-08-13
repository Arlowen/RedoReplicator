#!/bin/sh

set -eu
umask 077

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <backup-file>" >&2
    exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$SCRIPT_DIR/run.sh" --restore "$1"
