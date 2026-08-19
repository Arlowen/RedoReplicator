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

cd "$PROJECT_DIR"
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "Source distribution requires a clean Git worktree" >&2
    exit 3
fi
mkdir -p target/distributions
ARTIFACT=target/distributions/redo-replicator-$VERSION-sources.tar.gz
git archive --format=tar.gz \
    --prefix="redo-replicator-$VERSION-sources/" \
    --output="$ARTIFACT" HEAD
echo "Source distribution: $PROJECT_DIR/$ARTIFACT"
