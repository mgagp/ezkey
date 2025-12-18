# Native Image Optimization Plan for Auth API

## Current Situation

- **Image Type**: Buildpack native image (distroless, no shell)
- **Entrypoint**: `/cnb/process/web` (standard buildpack)
- **Issue**: No visible memory/time gains, need to verify if truly native and measure RSS

## Approach 1: Spring Boot Actuator Metrics (Recommended - Non-Invasive)

### Step 1: Add Actuator Dependency

Add to `ezkey-auth-api/pom.xml`:
```xml
<!-- Actuator for health checks and metrics -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Step 2: Create Optimized `application-native.properties`

Create/update `ezkey-auth-api/src/main/resources/application-native.properties` with:
- AOT enabled
- Reduced logging
- Actuator metrics enabled (memory, JVM stats)
- Optimized Tomcat settings

### Step 3: Query Memory Metrics

Once running, query:
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.memory.max
curl http://localhost:8080/actuator/metrics/process.uptime
```

**Advantages**:
- ✅ No image modification needed
- ✅ Real memory usage from JVM/process perspective
- ✅ Can compare JVM vs Native directly
- ✅ Production-safe

---

## Approach 2: Debug Image with Shell (For Deep Inspection)

### Create Debug Dockerfile

Create `ezkey-auth-api/Dockerfile.debug`:
```dockerfile
FROM ezkey-auth-api-native:latest AS base

# Add busybox for shell and basic tools
FROM gcr.io/distroless/base-debian12:nonroot
COPY --from=base /workspace /workspace
COPY --from=base /cnb /cnb

# Add busybox for shell access
COPY --from=busybox:latest /bin/sh /bin/sh
COPY --from=busybox:latest /bin/ps /bin/ps
COPY --from=busybox:latest /bin/cat /bin/cat

# Keep original entrypoint
ENTRYPOINT ["/cnb/process/web"]
```

**Usage**:
```bash
docker build -f ezkey-auth-api/Dockerfile.debug -t ezkey-auth-api-native-debug .
docker run -it --rm --entrypoint /bin/sh ezkey-auth-api-native-debug
# Inside container:
cat /proc/self/status | grep VmRSS
ps aux
```

**Advantages**:
- ✅ Direct access to `/proc` for RSS
- ✅ Can inspect process tree
- ✅ Full debugging capabilities

**Disadvantages**:
- ❌ Larger image size
- ❌ Not production-ready

---

## Approach 3: External Monitoring (Docker Stats + Calculations)

### Use Docker Stats with Process Inspection

```bash
# Get container stats
docker stats ezkey-auth-api-native --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"

# Get RSS from host (if process is visible)
docker top ezkey-auth-api-native
```

**Note**: Docker stats shows allocated memory, not actual RSS. For true RSS, need access to `/proc` inside container.

---

## Approach 4: Buildpack Environment Variables (Memory Limits)

### Set Memory Constraints at Build Time

Add to `pom.xml` native profile:
```xml
<spring-boot.build-image.environment.BP_JVM_GC>G1GC</spring-boot.build-image.environment.BP_JVM_GC>
<spring-boot.build-image.environment.BP_JVM_HEAD_ROOM>0</spring-boot.build-image.environment.BP_JVM_HEAD_ROOM>
```

**Note**: These are for JVM builds. For native, we need GraalVM-specific flags.

---

## Optimization Strategy

### Phase 1: Verification (Is it really native?)

1. **Check binary type**:
   ```bash
   docker run --rm --entrypoint /bin/sh busybox:latest sh -c "file /path/to/binary"
   ```
   (Need to find binary location in native image)

2. **Compare startup logs**: Native should show NO JVM memory calculator messages

3. **Check image layers**: Native images are typically single-layer executables

### Phase 2: Memory Measurement

**Recommended**: Use Approach 1 (Actuator) first, then Approach 2 (debug image) if needed.

### Phase 3: Build Optimizations

#### A. GraalVM Native Image Flags (in `native-image.properties`)

Current flags in `ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/native-image.properties`:
```
Args = --verbose \
       --no-fallback \
       -H:+ReportExceptionStackTraces \
       -H:+AddAllCharsets \
       --enable-all-security-services \
       -H:IncludeResourceBundles=org.hibernate.validator.ValidationMessages \
       -H:+StaticExecutableWithDynamicLibC \
       -H:+RemoveSaturatedTypeFlows \
       --allow-incomplete-classpath \
       --report-unsupported-elements-at-runtime
```

**Additional optimization flags to test**:
```
-H:+UnlockExperimentalVMOptions \
-H:+UseG1GC \
-H:MaxNewSize=50m \
-H:NewSize=50m \
-H:+PrintGC \
-H:+PrintGCDetails \
-H:+TraceClassInitialization \
-H:InitialCollectionPolicy=com.oracle.svm.core.genscavenge.CollectionPolicy$BySpaceAndTime \
-H:+AllowVMInspection \
-H:FallbackThreshold=0 \
-H:+ReportExceptionStackTraces \
-H:+PrintAnalysisCallTree \
-H:ReflectionConfigurationFiles=reflect-config.json \
-H:ResourceConfigurationFiles=resource-config.json \
-H:SerializationConfigurationFiles=serialization-config.json
```

#### B. Buildpack Builder Selection

Current: `paketobuildpacks/builder-jammy-tiny`

**Alternative builders** (may have different optimizations):
- `paketobuildpacks/builder-jammy-base` (larger but more tools)
- `paketobuildpacks/builder-jammy-full` (full stack)

#### C. Runtime Memory Configuration

Add to `application-native.properties`:
```properties
# Native memory optimization
spring.aot.enabled=true

# JVM-like settings (if applicable to native)
# Note: Native images don't use JVM heap, but some settings may apply
```

### Phase 4: Runtime Optimizations

1. **Connection Pool Sizing**: Reduce for native (already done in admin-api native config)
2. **Thread Pool**: Minimize (already configured)
3. **Logging**: Reduce verbosity (needs update in auth-api native config)
4. **Feature Flags**: Disable unused features (Swagger, dev tools)

---

## Immediate Actions

### Priority 1: Verify Native Compilation
- [ ] Check if image is truly native (no JVM)
- [ ] Compare startup logs (should NOT see "JVM memory calculator")
- [ ] Verify binary type

### Priority 2: Add Memory Measurement
- [ ] Add Actuator to auth-api
- [ ] Create optimized `application-native.properties`
- [ ] Rebuild native image
- [ ] Query memory metrics

### Priority 3: Optimize Build
- [ ] Review and enhance `native-image.properties` flags
- [ ] Test different buildpack builders
- [ ] Benchmark before/after

---

## Expected Results

### Native vs JVM Comparison

| Metric | JVM | Native (Target) | Native (Current) |
|--------|-----|-----------------|------------------|
| Startup | 15-20s | 2-3s | ? |
| Memory (RSS) | 200-300MB | 50-100MB | ? |
| Memory (Docker) | 300-400MB | 80-120MB | ? |
| Cold Start | 5-10s | 100-500ms | ? |

---

## Next Steps

1. **Add Actuator** to auth-api and create optimized native properties
2. **Rebuild** native image with optimizations
3. **Measure** using Actuator metrics
4. **Compare** with JVM version
5. **Iterate** on GraalVM flags if needed
