# Native Build Quick Start Guide

This guide provides quick reference commands for building native images for Ezkey components.

## Overview

Ezkey uses different native compilation strategies for different components:

- **auth-api**: Full native with AOT (fast startup, low memory)
- **admin-api**: Limited native without AOT (memory reduction, acceptable startup)
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

### Admin API - Limited Native Build (Native Image Only)

**Complete Build Process**:
```bash
# Single step - no AOT required
./scripts/build-native-admin.sh --skip-tests
```

**Alternative (Manual)**:
```bash
mvn -pl ezkey-admin-api -Pnative spring-boot:build-image \
    -Dspring-boot.build-image.imageName=ezkey-admin-api-native \
    -DskipTests \
    -Dspring-boot.build-image.skip=false
```

**Expected Results**:
- Startup: ~15-20 seconds (similar to JVM - acceptable for batch)
- Memory: ~100-150MB baseline (vs ~200-300MB JVM) - **~50% reduction**
- Binary Size: ~80-120MB (vs JAR + JVM ~300MB+)

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
docker-compose -f docker/docker-compose.native.yml up -d admin-api auth-api
```

**View Logs**:
```bash
docker-compose -f docker/docker-compose.native.yml logs -f auth-api
docker-compose -f docker/docker-compose.native.yml logs -f admin-api
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

### Admin API Native Image

```bash
docker run -p 9080:9080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/ezkey_db \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e SPRING_DATASOURCE_PASSWORD=ezkey \
    -e EZKEY_ADMIN_INITIAL_USERNAME=admin.docker \
    -e EZKEY_ADMIN_INITIAL_EMAIL=admin@ezkey.local \
    ezkey-admin-api-native:latest
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
- For admin-api: Add `@RegisterForReflection` or `reflect-config.json`

### Runtime Issues

**Startup Failures**:
- Check logs: `docker logs ezkey-auth-api-native` or `docker logs ezkey-admin-api-native`
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

### `build-native-admin.sh` (Admin API)
- **Purpose**: Direct native compilation for admin-api (no AOT)
- **Usage**: `./scripts/build-native-admin.sh [--skip-tests] [--verbose]`
- **Output**: Native image `ezkey-admin-api-native:latest`

### Debugging Scripts
- `debug-aot-classpath.sh`: Analyze classpath used by AOT
- `debug-aot-execution-order.sh`: Check execution order and timing
- See `scripts/README.md` for details

## Performance Comparison

| Component | Build Type | Startup | Memory | Use Case |
|-----------|-----------|---------|--------|----------|
| **auth-api** | Full Native (AOT) | 2-3s | 50-100MB | High-frequency service |
| **admin-api** | Limited Native | 15-20s | 100-150MB | Batch operations |
| **auth-api** | JVM | 15-20s | 200-300MB | Development/testing |
| **admin-api** | JVM | 15-20s | 200-300MB | Development/testing |

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
