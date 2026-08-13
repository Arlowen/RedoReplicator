#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
INSTALL_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PID_FILE=$INSTALL_DIR/data/redo-replicator.pid
TIMEOUT=${REDO_REPLICATOR_STOP_TIMEOUT_SECONDS:-60}

case "$TIMEOUT" in
    ''|*[!0-9]*)
        echo "REDO_REPLICATOR_STOP_TIMEOUT_SECONDS must be a non-negative integer" >&2
        exit 3
        ;;
esac

if [ ! -f "$PID_FILE" ]; then
    echo "RedoReplicator is not running"
    exit 0
fi
PID=$(sed -n '1p' "$PID_FILE")
case "$PID" in
    ''|*[!0-9]*)
        echo "Invalid RedoReplicator PID file: $PID_FILE" >&2
        exit 3
        ;;
esac
if ! kill -0 "$PID" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "RedoReplicator is not running; removed stale PID $PID"
    exit 0
fi

kill -TERM "$PID"
ELAPSED=0
while kill -0 "$PID" 2>/dev/null; do
    if [ "$ELAPSED" -ge "$TIMEOUT" ]; then
        echo "RedoReplicator PID $PID did not stop within ${TIMEOUT}s" >&2
        exit 3
    fi
    sleep 1
    ELAPSED=$((ELAPSED + 1))
done

rm -f "$PID_FILE"
echo "RedoReplicator stopped"
