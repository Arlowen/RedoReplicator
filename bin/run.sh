#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
INSTALL_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
JAVA_BIN=${REDO_REPLICATOR_JAVA:-$INSTALL_DIR/runtime/bin/java}
JAR_PATH=${REDO_REPLICATOR_JAR:-$INSTALL_DIR/lib/redo-replicator.jar}
LOGBACK_CONFIG=${REDO_REPLICATOR_LOGBACK_CONFIG:-$INSTALL_DIR/conf/logback.xml}

if [ ! -x "$JAVA_BIN" ]; then
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_BIN=$JAVA_HOME/bin/java
    else
        JAVA_BIN=$(command -v java || true)
    fi
fi

if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
    echo "OpenJDK 17 runtime not found" >&2
    exit 3
fi
JAVA_BANNER=$("$JAVA_BIN" -version 2>&1)
case "$JAVA_BANNER" in
    *openjdk\ version\ \"17* ) ;;
    *)
        echo "RedoReplicator requires OpenJDK 17" >&2
        exit 3
        ;;
esac
case "$JAVA_BANNER" in
    *Temurin*|*Adoptium*)
        echo "Eclipse Temurin is not a supported RedoReplicator runtime" >&2
        exit 3
        ;;
esac
if [ ! -r "$JAR_PATH" ]; then
    echo "RedoReplicator jar not found: $JAR_PATH" >&2
    exit 3
fi
if [ ! -r "$LOGBACK_CONFIG" ]; then
    echo "Logback configuration not found: $LOGBACK_CONFIG" >&2
    exit 3
fi
mkdir -p "$INSTALL_DIR/logs"

exec "$JAVA_BIN" \
    -Dlogback.configurationFile="$LOGBACK_CONFIG" \
    -Dredo.replicator.log.dir="$INSTALL_DIR/logs" \
    -cp "$JAR_PATH:$INSTALL_DIR/lib/*" \
    io.github.arlowen.redoreplicator.cli.RedoReplicatorMain \
    --install-dir "$INSTALL_DIR" "$@"
