#!/usr/bin/env bash
#
# BuildKit cache validation helper for Ezkey Docker image builds.
# Usage: ./docker/check-cache.sh
#

echo "========================================"
echo "BuildKit Cache Validation"
echo "========================================"
echo ""
echo "Maven dependencies for service images are cached via BuildKit RUN --mount"
echo "in docker/Dockerfile (cache id maven-cache inside BuildKit — not a Docker volume)."
echo ""
echo "Bind-mounted Spotless runs (./scripts/build-docker.sh) use a separate named volume"
echo "for /root/.m2 (default: ezkey-maven-spotless-cache) because Apply must write to the host checkout."
echo ""
echo "To validate BuildKit Maven cache behavior:"
echo "  1. Run a first build: ./docker/start.sh --debug-cache"
echo "  2. Note the build time (especially dependency download time)"
echo "  3. Run again: ./docker/start.sh --debug-cache"
echo "  4. Second build should be much faster when cache is warm"
echo ""
echo "Checking BuildKit status..."
if docker buildx version >/dev/null 2>&1; then
    echo "✅ BuildKit / buildx is available"
else
    echo "⚠️  WARNING: docker buildx may not be available"
    echo "   Make sure Docker Desktop is running and BuildKit is enabled"
fi
echo ""
echo "BuildKit cache is managed internally and is not listed as a normal Docker volume."
echo "To clear BuildKit build cache (aggressive):"
echo "  docker builder prune -a"
echo ""
