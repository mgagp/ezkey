# Native Build Quick Start Guide

> Status note (April 2026): this guide is now historical. The bounded Spring Boot 4 native spike
> completed image builds for Auth API and Integration API, but the Docker runtime path is still not
> viable. Admin API native support is no longer part of the active strategy. Read
> `docs/NATIVE_INITIATIVE_STATUS_2026-04.md` first before using anything below.

This guide provides quick reference commands for building native images for Ezkey components.

## Overview

Ezkey uses different native compilation strategies for different components:

- **auth-api**: Full native with AOT (fast startup, low memory)
- **integration-api**: Experimental native path explored during the Spring Boot 4 spike
- **admin-api**: JVM-only in the active strategy
- **Other components**: No native compilation

See `docs/NATIVE_COMPILATION_STRATEGY.md` for detailed strategy and rationale.

## Quick Reference Commands

### Auth API - Full Native Build (AOT + Native Image)

**Complete Build Process**:
```bash
# Step 1: AOT Processing
./scripts/build-native-aot.sh --skip-tests

# Step 2: Native Image Compilation
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -DskipTests -Dspring-boot.build-image.skip=false
```

**Rebuild Native Image Only** (if AOT already exists):
```bash
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -DskipTests -Dspring-boot.build-image.skip=false
```

**Expected Results**:
- Startup: ~2-3 seconds (vs ~15-20s JVM)
- Memory: ~50-100MB baseline (vs ~200-300MB JVM)
- Cold Start (Lambda): ~100-500ms (vs ~5-10s JVM)

### Integration API - Experimental Native Build

```bash
mvn -pl ezkey-integration-api -Pnative spring-boot:build-image \
    -Dspring-boot.build-image.imageName=ezkey-integration-api-native \
    -DskipTests
```

Use this only as historical context for the April 2026 spike. The image build works, but the
runtime path is still not considered viable.

## Docker Compose

### Running Native Images with Docker Compose

**Prerequisites**: Build native images first (see commands above)

**Start Native Stack**:
```bash
docker-compose -f docker/docker-compose.native.yml up -d
```

**Start Specific Services**:
```bash
# Start only native APIs (assumes postgres and migration already running)
docker-compose -f docker/docker-compose.native.yml up -d auth-api integration-api
```

**View Logs**:
```bash
docker-compose -f docker/docker-compose.native.yml logs -f auth-api
docker-compose -f docker/docker-compose.native.yml logs -f integration-api
```

**Stop Services**:
```bash
docker-compose -f docker/docker-compose.native.yml down
```

## Running Native Images Directly

### Auth API Native Image

```bash
docker run -p 8080:8080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/ezkey_db \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e SPRING_DATASOURCE_PASSWORD=ezkey \
    ezkey-auth-api-native:latest
```

### Integration API Native Image

```bash
docker run -p 7080:7080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/ezkey_db \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e SPRING_DATASOURCE_PASSWORD=ezkey \
    ezkey-integration-api-native:latest
```

## Troubleshooting

### Build Issues

**AOT Processing Fails**:
- See `docs/AOT_CLASSPATH_ISSUE_ANALYSIS.md` for classpath issues
- Use `./scripts/debug-aot-execution-order.sh --check-classes` to diagnose
- Ensure `ezkey-core` is installed: `mvn -pl ezkey-core install`

**Native Image Build Fails**:
- Ensure Docker is running
- Check network connectivity (buildpacks need to download)
- Try with `--verbose` flag for detailed output

**Class Not Found Errors**:
- For auth-api: Add runtime hints to `AuthNativeConfiguration.java`
- For integration-api: Review `IntegrationNativeConfiguration.java` and the native image logs

### Runtime Issues

**Startup Failures**:
- Check logs: `docker logs ezkey-auth-api-native` or `docker logs ezkey-integration-api-native`
- Verify database connectivity
- Check environment variables

**Memory Issues**:
- Native images use less memory, but monitor for leaks
- Use `docker stats` to monitor memory usage

## Build Scripts Reference

### `build-native-aot.sh` (Auth API)
- **Purpose**: AOT processing + verification for auth-api
- **Usage**: `./scripts/build-native-aot.sh [--skip-tests] [--verbose]`
- **Output**: AOT files in `target/spring-aot/`

### Integration API build command
- **Purpose**: Build the experimental integration-api native image used during the April 2026 spike
- **Usage**: `mvn -pl ezkey-integration-api -Pnative spring-boot:build-image -DskipTests`
- **Output**: Native image `ezkey-integration-api-native:latest`

### Debugging Scripts
- `debug-aot-classpath.sh`: Analyze classpath used by AOT
- `debug-aot-execution-order.sh`: Check execution order and timing
- See `scripts/README.md` for details

## Performance Comparison

| Component | Build Type | Startup | Memory | Use Case |
|-----------|-----------|---------|--------|----------|
| **auth-api** | Full Native (AOT) | 2-3s | 50-100MB | High-frequency service |
| **integration-api** | Experimental Native | Not validated | Not validated | Historical spike |
| **auth-api** | JVM | 15-20s | 200-300MB | Development/testing |
| **admin-api** | JVM | 15-20s | 200-300MB | Active operational path |

## Next Steps

- **Detailed Strategy**: See `docs/NATIVE_COMPILATION_STRATEGY.md`
- **Auth API Details**: See `ezkey-auth-api/NATIVE_BUILD.md`
- **Scripts Documentation**: See `scripts/README.md`
- **Troubleshooting**: See `docs/AOT_CLASSPATH_ISSUE_ANALYSIS.md`

## Related Documentation

- [Native Compilation Strategy](NATIVE_COMPILATION_STRATEGY.md) - Complete strategy and rationale
- [Auth API Native Build](../ezkey-auth-api/NATIVE_BUILD.md) - Detailed auth-api documentation
- [AOT Classpath Analysis](AOT_CLASSPATH_ISSUE_ANALYSIS.md) - Troubleshooting guide
- [Scripts README](../scripts/README.md) - Build script documentation
