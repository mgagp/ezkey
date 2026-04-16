# Native Compilation Strategy for Ezkey

> Status note (April 2026): this document is now historical. The bounded Spring Boot 4 native
> initiative completed build-time alignment and native image generation, but the Docker runtime path
> is not currently viable for Auth API or Integration API. Admin API native support is no longer
> part of the active strategy. Read `docs/NATIVE_INITIATIVE_STATUS_2026-04.md` first.

## Overview

This document records an earlier native compilation strategy for Ezkey components. It is kept for
historical rationale, not as the current operational recommendation.

## Strategic Decision Matrix

| Component | Native Compilation | AOT Processing | Rationale |
|-----------|-------------------|----------------|-----------|
| **auth-api** | ✅ Full (AOT + Native) | ✅ Required | High-frequency service, benefits from fast startup and low memory |
| **integration-api** | ⚠️ Experimental (builds, runtime blocked) | ✅ Required | Simpler target surface, but runtime Docker path still blocked |
| **admin-api** | ❌ No active native path | ❌ Not applicable | JVM-only in the active strategy |
| **demo-device** | ❌ No | ❌ No | Development/demo tool, no production deployment |
| **demo-app-acme** | ❌ No | ❌ No | Development/demo tool, no production deployment |
| **migration (Flyway)** | ❌ No | ❌ No | One-time execution, no runtime performance benefit |

## Component-Specific Strategies

### 1. Auth API - Full Native Compilation

**Strategy**: Complete AOT processing + Native image compilation

**Benefits**:
- **Startup Time**: ~2-3 seconds (vs ~15-20 seconds JVM)
- **Memory Footprint**: ~50-100MB baseline (vs ~200-300MB JVM)
- **Cold Start (Lambda)**: ~100-500ms (vs ~5-10 seconds JVM)
- **High Frequency**: Runs continuously, handles authentication requests

**Build Process**:
```bash
# Step 1: AOT Processing (required for full native benefits)
./scripts/build-native-aot.sh --skip-tests

# Step 2: Native Image Compilation
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -DskipTests -Dspring-boot.build-image.skip=false
```

**Configuration**:
- AOT hints in `AuthNativeConfiguration.java`
- Native-optimized properties in `application-native.properties`
- Full reflection configuration for DTOs, mappers, and Hibernate classes

### 2. Integration API - Experimental Native Compilation

**Strategy**: Full native image compilation with AOT, but still considered experimental because the
runtime Docker path remains blocked.

**Current outcome**:
- Native image build succeeds.
- Runtime Docker startup still fails during JPA / Hikari bootstrap.
- The path remains a future-investigation topic, not a recommended deployment mode.

**Build Process**:
```bash
# Experimental integration-api native build
mvn -pl ezkey-integration-api -Pnative spring-boot:build-image -DskipTests
```

**Conclusion**: Integration was the last bounded candidate because its surface is conceptually
simpler, but the current stack still does not provide a viable native runtime path.

### 3. Demo Components - No Native Compilation

**Rationale**:
- Development/demo tools only
- No production deployment
- Build complexity not justified
- Standard JVM execution sufficient

### 4. Migration Tools - No Native Compilation

**Rationale**:
- One-time execution per migration
- No runtime performance requirements
- Simpler JVM execution preferred
- No memory footprint concerns

## Build Recipes

### Auth API - Full Native Build

```bash
# Complete build process (AOT + Native Image)
./scripts/build-native-aot.sh --skip-tests
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -DskipTests -Dspring-boot.build-image.skip=false
```

**When to use**:
- Production deployments
- AWS Lambda deployments
- High-frequency service instances
- Memory-constrained environments

### Integration API - Experimental Native Build

```bash
# Experimental native build
mvn -pl ezkey-integration-api -Pnative spring-boot:build-image -DskipTests
```

**When to use**:
- Historical reproduction of the April 2026 spike
- Focused future investigation only

## Performance Expectations

### Auth API (Full Native with AOT)

| Metric | JVM | Native (AOT) | Improvement |
|--------|-----|--------------|-------------|
| Startup Time | 15-20s | 2-3s | **85-90%** |
| Memory Baseline | 200-300MB | 50-100MB | **60-75%** |
| Cold Start (Lambda) | 5-10s | 100-500ms | **90-95%** |
| Binary Size | N/A | ~80-120MB | N/A |

### Integration API (Experimental Native)

| Metric | JVM | Native | Improvement |
|--------|-----|--------|-------------|
| Startup Time | 15-20s | Not validated | Not validated |
| Memory Baseline | 200-300MB | Not validated | Not validated |
| Binary Size | N/A | Built successfully | N/A |

## Implementation Notes

### Auth API AOT Configuration

The AOT configuration is iterative and requires adding runtime hints as new features are added. Current configuration includes:
- All REST endpoint DTOs
- MapStruct mapper implementations
- Hibernate/JBoss Logging classes
- Resource patterns for properties and validation messages

See `ezkey-auth-api/src/main/java/org/ezkey/auth/config/AuthNativeConfiguration.java` for current hints.

### Integration API Native Configuration

Integration API now has its own runtime hints and native image properties, but the current runtime
result is still blocked in Docker.

## Maintenance Strategy

### Auth API
- **High Maintenance**: AOT hints must be updated as new features are added
- **Iterative Process**: Add hints when new `NoClassDefFoundError` or reflection errors occur
- **Documentation**: Keep `NATIVE_BUILD.md` updated with new hints

### Integration API
- **Experimental**: Build-time path works, runtime path remains blocked
- **Do not treat as supported**: revisit only in a bounded future spike

## Future Considerations

### Future Restart Target

If the initiative is reopened, Integration remains a reasonable first bounded retry because its
surface is conceptually simpler than Auth. The restart baseline is documented in
`docs/NATIVE_INITIATIVE_STATUS_2026-04.md`.

## Troubleshooting Strategy

When encountering native image issues, follow the systematic approach defined in `docs/NATIVE_TROUBLESHOOTING_STRATEGY.md`:

1. **First**: Check if the problematic feature can be disabled via configuration properties
2. **Evaluate**: Assess functional impact of disablement
3. **Categorize**: 
   - **Category 1**: Disablement → Fully functional application (document for review)
   - **Category 2**: Disablement → Non-production-ready (note for later resolution)
4. **Goal**: Achieve a working native image that starts without errors and passes all end-to-end tests

This strategy prioritizes progress while maintaining clear documentation for future optimization.

## References

- [Spring Boot Native Image Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [GraalVM Native Image Guide](https://www.graalvm.org/latest/reference-manual/native-image/)
- `ezkey-auth-api/NATIVE_BUILD.md` - Detailed auth-api native build documentation
- `docs/AOT_CLASSPATH_ISSUE_ANALYSIS.md` - AOT classpath troubleshooting guide
- `docs/NATIVE_TROUBLESHOOTING_STRATEGY.md` - Systematic troubleshooting approach with disablement strategy
