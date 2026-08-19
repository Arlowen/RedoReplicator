#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
INSTALL_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PID_FILE=$INSTALL_DIR/data/redo-replicator.pid
START_LOCK=$INSTALL_DIR/data/redo-replicator-start.lock
CONSOLE_LOG=$INSTALL_DIR/logs/console.log

mkdir -p "$INSTALL_DIR/data" "$INSTALL_DIR/logs"
if ! mkdir "$START_LOCK" 2>/dev/null; then
    echo "Another RedoReplicator start is already in progress" >&2
    exit 3
fi
trap 'rmdir "$START_LOCK" 2>/dev/null || true' EXIT
trap 'exit 3' HUP INT TERM

if [ -f "$PID_FILE" ]; then
    PID=$(sed -n '1p' "$PID_FILE")
    case "$PID" in
        ''|*[!0-9]*)
            echo "Invalid RedoReplicator PID file: $PID_FILE" >&2
            exit 3
            ;;
    esac
    if kill -0 "$PID" 2>/dev/null; then
        echo "RedoReplicator is already running with PID $PID" >&2
        exit 3
    fi
    rm -f "$PID_FILE"
fi

umask 077
REDO_REPLICATOR_LOGBACK_CONFIG="$INSTALL_DIR/conf/logback-background.xml" \
    nohup "$SCRIPT_DIR/run.sh" "$@" >> "$CONSOLE_LOG" 2>&1 &
PID=$!
PID_TEMP=$PID_FILE.tmp.$$
printf '%s\n' "$PID" > "$PID_TEMP"
mv "$PID_TEMP" "$PID_FILE"

sleep 1
if ! kill -0 "$PID" 2>/dev/null; then
    wait "$PID" || STATUS=$?
    rm -f "$PID_FILE"
    echo "RedoReplicator failed to start; see $CONSOLE_LOG (exit ${STATUS:-0})" >&2
    exit 3
fi

echo "RedoReplicator started with PID $PID"
