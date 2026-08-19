#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
VERSION=$(sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' \
    "$PROJECT_DIR/pom.xml" | sed -n '1p')

case "$VERSION" in
    0.*) ;;
    *)
        echo "Only 0.x RedoReplicator distributions are allowed: $VERSION" >&2
        exit 2
        ;;
esac

OS_NAME=$(uname -s)
case "$OS_NAME" in
    Linux) PLATFORM=linux ;;
    Darwin) PLATFORM=darwin ;;
    *)
        echo "Unsupported release operating system: $OS_NAME" >&2
        exit 2
        ;;
esac

MACHINE=$(uname -m)
case "$MACHINE" in
    aarch64|arm64) ARCHITECTURE=arm64 ;;
    x86_64|amd64) ARCHITECTURE=x86_64 ;;
    *)
        echo "Unsupported release architecture: $MACHINE" >&2
        exit 2
        ;;
esac

if [ -n "${REDO_REPLICATOR_RELEASE_JDK:-}" ]; then
    RELEASE_JDK=$REDO_REPLICATOR_RELEASE_JDK
elif [ -n "${JAVA_HOME:-}" ]; then
    RELEASE_JDK=$JAVA_HOME
elif [ "$OS_NAME" = Darwin ] && [ -x /usr/libexec/java_home ]; then
    RELEASE_JDK=$(/usr/libexec/java_home -v 17)
else
    JAVA_COMMAND=$(command -v java || true)
    if [ -z "$JAVA_COMMAND" ]; then
        echo "OpenJDK 17 is required to build the distribution" >&2
        exit 3
    fi
    RESOLVED_JAVA=$(readlink -f "$JAVA_COMMAND")
    RELEASE_JDK=$(CDPATH= cd -- "$(dirname -- "$RESOLVED_JAVA")/.." && pwd)
fi

JAVA_BIN=$RELEASE_JDK/bin/java
JDEPS_BIN=$RELEASE_JDK/bin/jdeps
JLINK_BIN=$RELEASE_JDK/bin/jlink
if [ ! -x "$JAVA_BIN" ] || [ ! -x "$JDEPS_BIN" ] || [ ! -x "$JLINK_BIN" ]; then
    echo "REDO_REPLICATOR_RELEASE_JDK must point to a complete JDK 17" >&2
    exit 3
fi
JAVA_BANNER=$($JAVA_BIN -version 2>&1)
case "$JAVA_BANNER" in
    *openjdk\ version\ \"17*) ;;
    *)
        echo "Distribution builds require OpenJDK 17" >&2
        exit 3
        ;;
esac
case "$JAVA_BANNER" in
    *Temurin*|*Adoptium*)
        echo "Eclipse Temurin is not a supported release JDK" >&2
        exit 3
        ;;
esac

cd "$PROJECT_DIR"
mvn -q -DskipTests package

MAIN_JAR=$PROJECT_DIR/target/redo-replicator-$VERSION.jar
if [ ! -r "$MAIN_JAR" ]; then
    echo "Maven did not create $MAIN_JAR" >&2
    exit 3
fi
if [ ! -r "$PROJECT_DIR/target/SBOM.json" ]; then
    echo "Maven did not create target/SBOM.json" >&2
    exit 3
fi

MODULES=$($JDEPS_BIN \
    --ignore-missing-deps \
    --multi-release 17 \
    --recursive \
    --print-module-deps \
    "$MAIN_JAR" "$PROJECT_DIR"/target/lib/*.jar)

mkdir -p "$PROJECT_DIR/target/distributions"
STAGE=$(mktemp -d "$PROJECT_DIR/target/release-stage.XXXXXX")
trap 'rm -rf "$STAGE"' EXIT HUP INT TERM
ROOT_NAME=redo-replicator-$VERSION
ROOT=$STAGE/$ROOT_NAME
mkdir -p "$ROOT/bin" "$ROOT/conf" "$ROOT/data" "$ROOT/lib" \
    "$ROOT/logs" "$ROOT/output" "$ROOT/sql" \
    "$ROOT/THIRD-PARTY-LICENSES"

$JLINK_BIN \
    --add-modules "$MODULES" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output "$ROOT/runtime"

cp "$MAIN_JAR" "$ROOT/lib/redo-replicator.jar"
cp "$PROJECT_DIR"/target/lib/*.jar "$ROOT/lib/"
cp "$PROJECT_DIR"/bin/*.sh "$ROOT/bin/"
cp "$PROJECT_DIR/conf/redo-replicator.yaml" "$ROOT/conf/"
cp "$PROJECT_DIR"/sql/*.sql "$ROOT/sql/"
cp -R "$PROJECT_DIR/sql/test" "$ROOT/sql/"
cp "$PROJECT_DIR/LICENSE" "$PROJECT_DIR/NOTICE" \
    "$PROJECT_DIR/README.md" "$PROJECT_DIR/target/SBOM.json" "$ROOT/"
cp -R "$PROJECT_DIR/THIRD-PARTY-LICENSES/." \
    "$ROOT/THIRD-PARTY-LICENSES/"
REDO_REPLICATOR_JAR_TOOL=$RELEASE_JDK/bin/jar \
    "$SCRIPT_DIR/collect-embedded-licenses.sh" \
    "$ROOT/THIRD-PARTY-LICENSES"
printf '%s\n' "$VERSION" > "$ROOT/VERSION"

ARTIFACT=$PROJECT_DIR/target/distributions/$ROOT_NAME-$PLATFORM-$ARCHITECTURE.tar.gz
tar -C "$STAGE" -czf "$ARTIFACT" "$ROOT_NAME"
cp "$PROJECT_DIR/target/SBOM.json" \
    "$PROJECT_DIR/target/distributions/SBOM.json"
echo "Distribution: $ARTIFACT"
