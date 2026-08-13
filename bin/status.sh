#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
INSTALL_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PID_FILE=$INSTALL_DIR/data/redo-replicator.pid
STATUS_FILE=$INSTALL_DIR/data/status.json

if [ ! -f "$PID_FILE" ]; then
    echo "RedoReplicator is STOPPED"
    exit 1
fi
PID=$(sed -n '1p' "$PID_FILE")
case "$PID" in
    ''|*[!0-9]*)
        echo "RedoReplicator status is UNKNOWN: invalid PID file $PID_FILE" >&2
        exit 3
        ;;
esac
if ! kill -0 "$PID" 2>/dev/null; then
    echo "RedoReplicator is STOPPED (stale PID $PID)"
    exit 1
fi

echo "RedoReplicator is RUNNING (PID $PID)"
if [ -r "$STATUS_FILE" ]; then
    cat "$STATUS_FILE"
fi
