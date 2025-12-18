# Fix: Native Image Not Actually Native + MapStruct Error

## Problem 1: Image is NOT Native (Running JVM)

**Symptoms:**
- Logs show "Calculating JVM memory based on..."
- "Java Heap (reserved=...)"
- "Native Memory Tracking" (JVM feature)
- Process uses `/workspace/BOOT-INF/classes` (JAR structure, not native binary)

**Root Cause:**
The image was likely built **without** the `-Pnative` profile, or the buildpack didn't detect native mode correctly.

**Solution:**

### Step 1: Clean Build (Fixes MapStruct Issue)

```bash
cd ezkey-auth-api
mvn clean
```

### Step 2: Build Native Image Correctly

**CRITICAL**: You MUST use the `-Pnative` profile:

```bash
# From project root
mvn spring-boot:build-image \
  -pl ezkey-auth-api \
  -Pnative \
  -DskipTests \
  "-Dspring-boot.build-image.imageName=ezkey-auth-api-native"
```

**OR from ezkey-auth-api directory:**

```bash
cd ezkey-auth-api
mvn spring-boot:build-image -Pnative -DskipTests "-Dspring-boot.build-image.imageName=ezkey-auth-api-native"
```

### Step 3: Verify Image is Native

After building, check the image:

```bash
# Check entrypoint (should be /cnb/process/web, not java)
docker inspect ezkey-auth-api-native:latest --format '{{.Config.Entrypoint}}'

# Run and check logs - should NOT see:
# - "Calculating JVM memory"
# - "Java Heap"
# - Should see fast startup (< 5 seconds)
docker run --rm ezkey-auth-api-native:latest
```

**Expected Native Image Behavior:**
- ✅ Fast startup (< 5 seconds)
- ✅ NO "JVM memory calculator" messages
- ✅ NO "Java Heap" in logs
- ✅ Lower memory usage (~50-100MB vs 200-300MB)

**JVM Image (Wrong) Behavior:**
- ❌ Slow startup (15-20 seconds)
- ❌ "Calculating JVM memory based on..."
- ❌ "Java Heap (reserved=...)"
- ❌ Higher memory usage

---

## Problem 2: MapStruct Duplicate Method Error

**Error:**
```
ClassFormatError: Duplicate method name "toAuthAttemptPendingResponseDto" 
with signature "(LAuthAttemptPendingResponse;)Lorg.ezkey.authattempt.dto.AuthAttemptPendingResponseDto;"
```

**Root Cause:**
Corrupted MapStruct generated code cache, possibly from partial compilation or IDE interference.

**Solution:**

1. **Clean build:**
   ```bash
   cd ezkey-auth-api
   mvn clean
   ```

2. **Rebuild:**
   ```bash
   mvn clean package -DskipTests
   ```

3. **If still failing, delete generated sources manually:**
   ```bash
   rm -rf ezkey-auth-api/target/generated-sources
   mvn clean package -DskipTests
   ```

4. **Verify generated file:**
   ```bash
   # Check for duplicate methods
   grep -n "toAuthAttemptPendingResponseDto" ezkey-auth-api/target/generated-sources/annotations/org/ezkey/authattempt/mapper/AuthAttemptMapperImpl.java
   ```
   
   Should show only ONE occurrence (the method definition).

---

## Complete Rebuild Procedure

To fix both issues:

```bash
# 1. Clean everything
cd c:\github\ezkey
mvn clean -pl ezkey-auth-api

# 2. Build native image with correct profile
mvn spring-boot:build-image \
  -pl ezkey-auth-api \
  -Pnative \
  -DskipTests \
  "-Dspring-boot.build-image.imageName=ezkey-auth-api-native"

# 3. Verify it's native
docker run --rm ezkey-auth-api-native:latest 2>&1 | head -20
# Should NOT see "Calculating JVM memory" or "Java Heap"

# 4. Test in Docker Compose
cd ezkey-tests
./clean-start.sh --native
```

---

## Why This Happened

1. **Native Image Issue**: The image was likely built with `mvn spring-boot:build-image` **without** `-Pnative`, which builds a JVM image instead of native.

2. **MapStruct Issue**: Partial compilation or IDE cache corruption caused duplicate method generation.

---

## Prevention

Always use:
```bash
mvn spring-boot:build-image -Pnative ...
```

**Never:**
```bash
mvn spring-boot:build-image ...  # Missing -Pnative!
```
