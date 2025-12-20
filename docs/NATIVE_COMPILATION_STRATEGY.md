# Native Compilation Strategy for Ezkey

## Overview

This document defines the native compilation strategy for Ezkey components, balancing performance benefits, build complexity, and operational requirements.

## Strategic Decision Matrix

| Component | Native Compilation | AOT Processing | Rationale |
|-----------|-------------------|----------------|-----------|
| **auth-api** | ✅ Full (AOT + Native) | ✅ Required | High-frequency service, benefits from fast startup and low memory |
| **admin-api** | ⚠️ Limited (Native only, no AOT) | ❌ Not required | Batch operations, startup time less critical, memory reduction valuable |
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

### 2. Admin API - Limited Native Compilation

**Strategy**: Native image compilation WITHOUT AOT processing

**Rationale**:
- **Startup Time**: Less critical (batch operations, scheduled tasks)
- **Memory Footprint**: Significant reduction (~50-100MB vs ~200-300MB JVM)
- **Build Complexity**: Avoids AOT configuration overhead
- **Operational Pattern**: Runs scheduled tasks, less frequent restarts

**Benefits of Native Without AOT**:
- ✅ **Memory Reduction**: 50-70% reduction in baseline memory usage
- ✅ **Smaller Binary**: Reduced container image size
- ✅ **Simpler Build**: No AOT hints configuration required
- ⚠️ **Startup Time**: Similar to JVM (~15-20 seconds) - acceptable for batch operations
- ⚠️ **Reflection**: Requires manual configuration for dynamic features

**Build Process**:
```bash
# Direct native compilation (no AOT step needed)
mvn -pl ezkey-admin-api -Pnative spring-boot:build-image -DskipTests -Dspring-boot.build-image.skip=false
```

**Configuration Requirements**:
- Basic native image configuration (reflection for DTOs if needed)
- No AOT processing configuration
- Standard Spring Boot native image support

**Memory Analysis**:
- **JVM Baseline**: ~200-300MB (heap + metaspace + native memory)
- **Native (no AOT)**: ~100-150MB (reduced JIT overhead, no class metadata)
- **Native (with AOT)**: ~50-100MB (optimized startup, pre-computed metadata)

**Conclusion**: For admin-api, native compilation without AOT provides **significant memory benefits** (~50% reduction) with acceptable trade-offs (startup time similar to JVM, but less critical for batch operations).

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

### Admin API - Limited Native Build

```bash
# Direct native compilation (no AOT)
mvn -pl ezkey-admin-api -Pnative spring-boot:build-image -DskipTests -Dspring-boot.build-image.skip=false
```

**When to use**:
- Production deployments with memory constraints
- Container environments with resource limits
- Batch processing workloads
- Scheduled task execution

## Performance Expectations

### Auth API (Full Native with AOT)

| Metric | JVM | Native (AOT) | Improvement |
|--------|-----|--------------|-------------|
| Startup Time | 15-20s | 2-3s | **85-90%** |
| Memory Baseline | 200-300MB | 50-100MB | **60-75%** |
| Cold Start (Lambda) | 5-10s | 100-500ms | **90-95%** |
| Binary Size | N/A | ~80-120MB | N/A |

### Admin API (Native without AOT)

| Metric | JVM | Native (no AOT) | Improvement |
|--------|-----|-----------------|-------------|
| Startup Time | 15-20s | 15-20s | **0%** (acceptable) |
| Memory Baseline | 200-300MB | 100-150MB | **50%** |
| Binary Size | N/A | ~80-120MB | N/A |

## Implementation Notes

### Auth API AOT Configuration

The AOT configuration is iterative and requires adding runtime hints as new features are added. Current configuration includes:
- All REST endpoint DTOs
- MapStruct mapper implementations
- Hibernate/JBoss Logging classes
- Resource patterns for properties and validation messages

See `ezkey-auth-api/src/main/java/org/ezkey/auth/config/AuthNativeConfiguration.java` for current hints.

### Admin API Native Configuration

Admin API uses standard Spring Boot native image support without AOT. If reflection issues arise, they can be addressed with:
- `@RegisterForReflection` annotations
- `reflect-config.json` files
- Runtime hints (if AOT is added later)

## Maintenance Strategy

### Auth API
- **High Maintenance**: AOT hints must be updated as new features are added
- **Iterative Process**: Add hints when new `NoClassDefFoundError` or reflection errors occur
- **Documentation**: Keep `NATIVE_BUILD.md` updated with new hints

### Admin API
- **Low Maintenance**: Standard native image support, minimal configuration
- **Reactive**: Add reflection configuration only if issues arise
- **Optional AOT**: Can be upgraded to full AOT if startup time becomes critical

## Future Considerations

### Potential Admin API AOT Upgrade

If admin-api startup time becomes critical (e.g., frequent restarts, scaling requirements), consider:
1. Adding AOT processing configuration
2. Creating `AdminNativeConfiguration.java` with runtime hints
3. Following the same iterative process as auth-api

### Migration Path

To upgrade admin-api to full AOT:
1. Create native configuration class
2. Add runtime hints iteratively
3. Update build process to include AOT step
4. Test thoroughly before production deployment

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
