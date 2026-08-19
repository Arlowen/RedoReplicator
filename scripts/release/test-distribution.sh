#!/bin/sh

set -eu

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <redo-replicator-distribution.tar.gz>" >&2
    exit 2
fi

ARTIFACT=$1
if [ ! -r "$ARTIFACT" ]; then
    echo "Distribution is not readable: $ARTIFACT" >&2
    exit 2
fi

STAGE=$(mktemp -d "${TMPDIR:-/tmp}/redoreplicator-smoke.XXXXXX")
trap 'rm -rf "$STAGE"' EXIT HUP INT TERM
tar -xzf "$ARTIFACT" -C "$STAGE"

ROOT_COUNT=$(find "$STAGE" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')
if [ "$ROOT_COUNT" -ne 1 ]; then
    echo "Distribution must contain exactly one root directory" >&2
    exit 3
fi
ROOT=$(find "$STAGE" -mindepth 1 -maxdepth 1 -type d)

for path in \
    bin/run.sh \
    bin/start.sh \
    bin/stop.sh \
    bin/validate.sh \
    conf/logback.xml \
    conf/logback-background.xml \
    conf/redo-replicator.yaml \
    lib/redo-replicator.jar \
    runtime/bin/java \
    runtime/legal/java.base/LICENSE \
    sql/configure_database.sql \
    sql/create_capture_user.sql \
    sql/create_common_capture_user.sql \
    LICENSE \
    NOTICE \
    README.md \
    SBOM.json \
    THIRD-PARTY-LICENSES/README.md \
    THIRD-PARTY-LICENSES/Apache-2.0.txt \
    THIRD-PARTY-LICENSES/MPL-2.0.txt \
    THIRD-PARTY-LICENSES/LGPL-2.1.txt \
    THIRD-PARTY-LICENSES/resolved/ojdbc17-23.26.3.0.0/META-INF_license.txt \
    VERSION
do
    if [ ! -r "$ROOT/$path" ]; then
        echo "Distribution entry is missing: $path" >&2
        exit 3
    fi
done

if ! grep -q '"bomFormat"[[:space:]]*:[[:space:]]*"CycloneDX"' \
        "$ROOT/SBOM.json"; then
    echo "Distribution SBOM is not CycloneDX JSON" >&2
    exit 3
fi

if [ ! -x "$ROOT/bin/run.sh" ] || [ ! -x "$ROOT/runtime/bin/java" ]; then
    echo "Distribution launchers are not executable" >&2
    exit 3
fi

VERSION=$(sed -n '1p' "$ROOT/VERSION")
case "$VERSION" in
    0.*) ;;
    *)
        echo "Distribution version must remain 0.x: $VERSION" >&2
        exit 3
        ;;
esac

"$ROOT/bin/run.sh" --help >/dev/null
ACTUAL_VERSION=$("$ROOT/bin/run.sh" --version)
if [ "$ACTUAL_VERSION" != "RedoReplicator $VERSION" ]; then
    echo "Unexpected distribution version: $ACTUAL_VERSION" >&2
    exit 3
fi

echo "Distribution smoke test passed: $ARTIFACT"
