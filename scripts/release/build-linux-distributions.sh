#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
OUTPUT_DIR=$PROJECT_DIR/target/distributions
IMAGE=maven:3.9.16-amazoncorretto-17@sha256:c7411a43a466ffece2e4a80892fc44c9d24011c3d31be71e773f970978d32768

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker Buildx is required for Linux distribution builds" >&2
    exit 3
fi
docker buildx version >/dev/null
mkdir -p "$OUTPUT_DIR"
CURRENT_STAGE=
cleanup() {
    if [ -n "$CURRENT_STAGE" ]; then
        rm -rf "$CURRENT_STAGE"
    fi
}
trap cleanup EXIT HUP INT TERM

for architecture in arm64 amd64; do
    CURRENT_STAGE=$(mktemp -d \
        "$PROJECT_DIR/target/linux-$architecture.XXXXXX")
    docker buildx build \
        --platform "linux/$architecture" \
        --file "$SCRIPT_DIR/Dockerfile.release" \
        --output "type=local,dest=$CURRENT_STAGE" \
        "$PROJECT_DIR"
    cp "$CURRENT_STAGE"/*.tar.gz "$OUTPUT_DIR/"
    cp "$CURRENT_STAGE/SBOM.json" "$OUTPUT_DIR/SBOM.json"
    rm -rf "$CURRENT_STAGE"
    CURRENT_STAGE=
done

for artifact in \
    "$OUTPUT_DIR"/*-linux-arm64.tar.gz \
    "$OUTPUT_DIR"/*-linux-x86_64.tar.gz
do
    case "$artifact" in
        *-linux-arm64.tar.gz) platform=linux/arm64 ;;
        *-linux-x86_64.tar.gz) platform=linux/amd64 ;;
    esac
    docker run --rm \
        --platform "$platform" \
        --entrypoint /bin/sh \
        -v "$PROJECT_DIR:/source:ro" \
        -v "$OUTPUT_DIR:/distributions:ro" \
        "$IMAGE" \
        /source/scripts/release/test-distribution.sh \
        "/distributions/$(basename "$artifact")"
done

"$SCRIPT_DIR/generate-checksums.sh"
trap - EXIT HUP INT TERM
echo "Linux distributions: $OUTPUT_DIR"
