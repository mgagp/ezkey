# AOT Classpath Issue Analysis

## Problem Statement

Spring Boot AOT processing (`process-aot`) intermittently fails with `NoClassDefFoundError` for classes that should be available:
- `AuthAttemptPendingResponse`
- `AuthAttemptRespondRequestDto`
- `AuthAttemptRespondResponse`
- Other DTOs and domain classes

**Critical Observation**: This worked previously without any changes to `pom.xml` or build configuration, suggesting an environmental or timing issue rather than a configuration problem.

## Root Cause Hypothesis

### Hypothesis 1: Classpath Construction Issue

Spring Boot AOT uses Maven's **compile classpath**, which should automatically include:
1. `target/classes` (compiled classes from the current module)
2. All dependency JARs (including `ezkey-core`)
3. Generated sources (MapStruct mappers)

However, `dependency:build-classpath` only captures **dependency JARs**, not `target/classes`. This is expected behavior for that plugin, but it means our debugging script doesn't show the full picture.

**Verification**: Spring Boot AOT should automatically include `target/classes` in its classpath. If it doesn't, that's a bug or misconfiguration.

### Hypothesis 2: Timing/Order of Execution

MapStruct generates mapper implementations during the `compile` phase. If AOT runs before MapStruct completes, or if there's a race condition, classes might not be available.

**Verification**: Check if `AuthAttemptMapperImpl.class` exists in `target/classes` before AOT runs.

### Hypothesis 3: Intermittent Build State

If the build is run in different ways (e.g., `mvn clean install` vs `mvn compile`), the state of `target/classes` might differ. Previous successful builds might have had stale classes that are now missing.

**Verification**: Compare successful vs failed builds to identify differences.

### Hypothesis 4: Environment Changes

Something in the environment changed:
- Maven version
- Spring Boot version (upgraded from 3.3.6 to 3.5.8?)
- Java version
- IDE interference (Eclipse/IntelliJ locking files)
- Build order differences

**Verification**: Check git history for version changes, check Maven/Java versions.

## Diagnostic Tools Created

### 1. `debug-aot-classpath.sh`
Captures the compile classpath using `dependency:build-classpath`. This shows dependency JARs but not `target/classes` (which is expected - that plugin doesn't include it).

**Limitation**: Doesn't show the actual classpath used by AOT, only the dependency classpath.

### 2. `debug-aot-execution-order.sh` (NEW)
Checks:
- When classes are generated (before/after compilation)
- If MapStruct classes exist before AOT runs
- What phase AOT runs in
- Lists all classes in `target/classes`

### 3. `build-native-aot.sh`
Enforces a specific build order:
1. Clean everything
2. Install `ezkey-core` first
3. Package `ezkey-auth-api` (ensures JAR is built)
4. Run AOT processing

## Why It Might Have Worked Before

### Scenario 1: Stale Classes
Previous builds might have had stale `.class` files in `target/classes` from earlier successful compilations. A `clean` might have removed these, exposing the issue.

### Scenario 2: Build Order
Previous builds might have been run in a specific order (e.g., always `mvn clean install` from root) that ensured `ezkey-core` was installed and `ezkey-auth-api` was fully compiled before AOT ran.

### Scenario 3: IDE Compilation
If the IDE (Eclipse/IntelliJ) was compiling classes directly to `target/classes`, those classes would be available even if Maven compilation had issues. Closing the IDE might have exposed the real problem.

### Scenario 4: Version Change
A recent upgrade (Spring Boot 3.3.6 → 3.5.8?) might have changed how AOT constructs its classpath or when it runs.

## Solutions to Test

### Solution 1: Ensure Proper Build Order
Use `build-native-aot.sh` which enforces:
- `ezkey-core` installed first
- `ezkey-auth-api` fully packaged (not just compiled)
- Then AOT runs

**Status**: Already implemented, but may need refinement.

### Solution 2: Verify MapStruct Compilation
Ensure MapStruct generates classes before AOT runs. Check Maven phase bindings:
- MapStruct runs during `generate-sources` or `process-sources`
- AOT runs during `process-classes` (default for `process-aot`)

**Action**: Verify this is correct in the effective POM.

### Solution 3: Explicit Classpath Configuration
If Spring Boot AOT isn't including `target/classes` automatically, we might need to explicitly configure it. However, this should not be necessary - Spring Boot should handle this automatically.

**Action**: Only if Solution 1 and 2 don't work.

### Solution 4: Check for "Unresolved Compilation Problems"
If MapStruct mappers have "Unresolved compilation problems" in their bytecode (as seen with `javap`), they might compile but not be usable. This could happen if:
- `ezkey-core` wasn't installed when MapStruct ran
- Classes were compiled with missing dependencies

**Action**: Use `javap -v` to inspect mapper bytecode for unresolved references.

## Next Steps

1. **Run `debug-aot-execution-order.sh`** to see:
   - If MapStruct classes exist before AOT
   - What phase AOT runs in
   - If classes are in `target/classes`

2. **Compare successful vs failed builds**:
   - What command was used?
   - Was it a clean build?
   - Was the IDE running?

3. **Check version history**:
   - When did Spring Boot version change?
   - When did Maven version change?
   - Any other dependency upgrades?

4. **Inspect mapper bytecode**:
   ```bash
   javap -v ezkey-auth-api/target/classes/org/ezkey/authattempt/mapper/AuthAttemptMapperImpl.class | grep -i "unresolved"
   ```

5. **Test with explicit build order**:
   ```bash
   ./scripts/build-native-aot.sh --skip-tests
   ```

## Expected Behavior

Spring Boot AOT should:
1. Use Maven's compile classpath (which includes `target/classes`)
2. Find all classes in `target/classes` (including MapStruct generated mappers)
3. Find all classes in dependency JARs (including `ezkey-core`)
4. Process everything without `NoClassDefFoundError`

If this doesn't happen, either:
- The classpath isn't being constructed correctly (Spring Boot bug?)
- Classes aren't being compiled/generated at the right time (build order issue)
- Classes have unresolved compilation problems (MapStruct compilation issue)

## References

- [Spring Boot Native Image Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [Maven Classpath Construction](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
- [MapStruct Documentation](https://mapstruct.org/documentation/stable/reference/html/)
