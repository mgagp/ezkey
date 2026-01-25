# Spring Boot 4 Migration Analysis Report
## Based on Open Rewrite Patch Review

**Date:** 2026-01-24  
**Migration:** Spring Boot 3.5.8 → 4.0.2  
**Patch File:** `target/rewrite/rewrite.patch` (1748 lines)

---

## 📊 Overall Assessment

**Confidence Level:** 🟡 **Medium-High (75%)**

The migration appears well-structured with mostly mechanical changes. However, several critical areas require manual verification and potential adjustments.

---

## ✅ Changes That Should Work Smoothly

### 1. **Version Updates**
- ✅ Spring Boot: 3.5.8 → 4.0.2
- ✅ SpringDoc OpenAPI: 2.8.13 → 3.0.1
- ✅ Spring Cloud Context: 4.1.1 → 4.3.1
- **Risk:** Low - Standard version bumps

### 2. **Java Modernization**
- ✅ `String.format()` → `.formatted()` (Java 21+)
- ✅ `Paths.get()` → `Path.of()` (Java 11+)
- ✅ Pattern matching for `instanceof`
- ✅ `@Serial` annotation for `serialVersionUID`
- **Risk:** Low - Pure syntax improvements

### 3. **Package Renames (Mechanical)**
- ✅ `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- ✅ `@EntityScan` package change
- ✅ `@DataJpaTest` package change
- **Risk:** Low - Automatic refactoring

---

## ⚠️ Critical Areas Requiring Attention

### 1. **Jackson Migration (HIGH RISK) 🔴**

**Change:** `com.fasterxml.jackson` → `tools.jackson`

**Impact:**
- **All Jackson imports changed** across the codebase
- `JsonProcessingException` → `JacksonException`
- `ObjectNode.asText()` → `ObjectNode.asString()`
- Jackson configuration in `application.properties` may need updates

**Files Affected:**
- `ezkey-core/src/main/java/org/ezkey/audit/util/AuditDetailsBuilder.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/BootstrapCredentialsFileExporter.java`
- `ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/service/UserMappingService.java`
- `ezkey-demo-device/src/main/java/org/ezkey/demo/device/service/EnrollmentStoreService.java`
- Multiple test files

**Action Required:**
1. ✅ Verify Jackson 3.x compatibility with existing configuration
2. ⚠️ Check if `spring.jackson.*` properties still work (they should)
3. ⚠️ Test JSON serialization/deserialization thoroughly
4. ⚠️ Verify custom ObjectMapper configurations (if any)
5. ⚠️ Check compatibility with RestAssured and other HTTP clients

**Known Issue:** Jackson 3.x is a major version change. While the package rename is mechanical, behavior changes may exist.

---

### 2. **Spring Data JPA @NativeQuery (MEDIUM RISK) 🟡**

**Change:** `@Query(nativeQuery = true)` → `@NativeQuery`

**Impact:**
- New annotation introduced in Spring Data JPA
- All native queries in repositories updated

**Files Affected:**
- `ezkey-core/src/main/java/org/ezkey/authattempt/domain/repository/AuthAttemptRepository.java`
- `ezkey-core/src/main/java/org/ezkey/enrollment/domain/repository/EnrollmentRepository.java`

**Action Required:**
1. ✅ Verify `@NativeQuery` is available in Spring Data JPA 4.x
2. ⚠️ Test all native query methods (especially locking queries with `FOR NO KEY UPDATE`)
3. ⚠️ Verify parameter binding still works correctly
4. ⚠️ Check if query execution behavior changed

**Note:** The patch shows proper migration, but native queries are sensitive to changes.

---

### 3. **Test Framework Changes (MEDIUM RISK) 🟡**

**Changes:**
- `@MockBean` → `@MockitoBean`
- `@WebMvcTest` package change
- `@DataJpaTest` package change
- `spring-security-test` → `spring-boot-starter-security-test`
- New dependency: `spring-boot-starter-webmvc-test`

**Files Affected:**
- All test files across modules
- `ezkey-core/src/test/java/org/ezkey/security/KeyRotationServiceIntegrationTest.java`
- `ezkey-core/src/test/java/org/ezkey/security/ReencryptionServiceIntegrationTest.java`
- `ezkey-auth-api/src/test/java/org/ezkey/auth/controller/*Test.java`
- `ezkey-admin-api` test files

**Action Required:**
1. ⚠️ Run full test suite after migration
2. ⚠️ Verify `@MockitoBean` works identically to `@MockBean`
3. ⚠️ Check if test configuration needs updates
4. ⚠️ Verify Spring Security test integration still works

**Known Issue:** Test framework changes can break tests even if code compiles.

---

### 4. **Management/Actuator Configuration (MEDIUM RISK) 🟡**

**Changes:**
- `management.endpoint.health.enabled=true` → `management.endpoint.health.access=read-only`
- `management.endpoints.enabled-by-default=false` → `management.endpoints.access.default=none`
- `management.metrics.export.prometheus.enabled` → `management.prometheus.metrics.export.enabled`

**Files Affected:**
- `ezkey-migration/src/main/resources/application*.properties`
- `ezkey-auth-api/src/main/resources/application-native.properties`
- `ezkey-crypto-api/config/application*.properties`
- `ezkey-demo-device/src/main/resources/application-docker.properties`

**Action Required:**
1. ⚠️ Verify actuator endpoints still work as expected
2. ⚠️ Test health checks in all environments
3. ⚠️ Verify Prometheus metrics (if used)
4. ⚠️ Check if endpoint access control changed behavior

**Note:** Actuator configuration changes can affect monitoring and health checks.

---

### 5. **Flyway Migration (LOW-MEDIUM RISK) 🟢🟡**

**Change:**
- `org.flywaydb:flyway-core` → `spring-boot-starter-flyway`
- `FlywayMigrationInitializer` package change

**Files Affected:**
- `ezkey-migration/pom.xml`
- `ezkey-migration/src/main/java/org/ezkey/migration/EzkeyMigrationApp.java`

**Action Required:**
1. ⚠️ Verify Flyway migrations still run correctly
2. ⚠️ Test migration execution order
3. ⚠️ Check if Flyway configuration properties changed

**Note:** Flyway is critical for database setup. Test thoroughly.

---

### 6. **Spring Cloud Context (LOW RISK) 🟢**

**Change:** 4.1.1 → 4.3.1

**Files Affected:**
- `ezkey-demo-app-acme/pom.xml`

**Action Required:**
1. ✅ Verify `@RefreshScope` still works
2. ⚠️ Test config hot-reload if used

---

## 🔍 Areas to Peaufiner (Polish)

### 1. **Dependency Version Hardcoding**

**Issue:** Some dependencies have hardcoded versions in the patch:
- `spring-boot-starter-data-jpa-test` version `4.0.2`
- `spring-boot-starter-security-test` version `4.0.2`
- `spring-boot-starter-flyway` version `4.0.2`
- `jackson-databind` version `3.0.4` (in ezkey-tests)

**Recommendation:**
- Move these to parent `pom.xml` properties if not already managed by Spring Boot BOM
- Ensure version consistency across modules

---

### 2. **Missing Dependencies**

**New Dependencies Added:**
- `spring-boot-starter-data-jpa-test` (ezkey-core)
- `spring-boot-starter-webmvc-test` (auth-api, crypto-api)
- `spring-boot-starter-restclient` (demo-app-acme)
- `spring-boot-starter-security-test` (admin-api, auth-api, crypto-api)

**Action Required:**
- ✅ Verify these are needed (some might be transitive)
- ⚠️ Check if they're properly scoped (test scope)

---

### 3. **WebJars Dependency Change**

**Change:** `webjars-locator-core` → `webjars-locator-lite`

**Files Affected:**
- `ezkey-demo-device/pom.xml`

**Action Required:**
- ⚠️ Verify web resources still load correctly
- ⚠️ Test if "lite" version has all needed features

---

### 4. **Removed Dependencies**

**Removed:**
- `io.swagger.core.v3:swagger-annotations` (ezkey-demo-device)

**Action Required:**
- ⚠️ Verify no compilation errors
- ⚠️ Check if annotations are still needed (might be transitive)

---

### 5. **@DependsOnDatabaseInitialization**

**New Annotation:**
- Added to `ShedLockHealthIndicator`

**Action Required:**
- ⚠️ Verify this doesn't cause circular dependencies
- ⚠️ Test health check behavior

---

### 6. **@Valid Annotation Addition**

**Change:** Added `@Valid` to nested configuration properties

**Files Affected:**
- `ezkey-core/src/main/java/org/ezkey/config/EzkeyCoreProperties.java`

**Action Required:**
- ⚠️ Verify validation still works as expected
- ⚠️ Test configuration property binding

---

### 7. **Response Status Code Change**

**Change:** `response.getRawStatusCode()` → `response.getStatusCode().value()`

**Files Affected:**
- `ezkey-demo-app-acme/src/main/java/org/ezkey/demo/acme/config/BufferingClientHttpResponseWrapper.java`

**Action Required:**
- ⚠️ Verify HTTP response handling still works
- ⚠️ Test edge cases (error responses)

---

## 🚨 Potential Breaking Changes

### 1. **Jackson 3.x Behavior Changes**
- Serialization/deserialization might behave differently
- Custom serializers/deserializers may need updates
- Date/time handling might change

### 2. **Spring Security Test Changes**
- Test authentication setup might need adjustments
- Mock security context behavior might differ

### 3. **Actuator Endpoint Access**
- New access model might restrict endpoints differently
- Health check behavior might change

### 4. **Native Query Execution**
- Parameter binding might behave differently
- Lock behavior (`FOR NO KEY UPDATE`) needs verification

---

## 📋 Recommended Testing Strategy

### Phase 1: Compilation & Basic Tests
1. ✅ Apply patch
2. ✅ Fix any compilation errors
3. ✅ Run unit tests
4. ✅ Fix test failures

### Phase 2: Integration Testing
1. ⚠️ Test all native queries (especially locking queries)
2. ⚠️ Test JSON serialization/deserialization
3. ⚠️ Test actuator endpoints
4. ⚠️ Test Flyway migrations
5. ⚠️ Test Spring Security authentication

### Phase 3: End-to-End Testing
1. ⚠️ Test complete enrollment flow
2. ⚠️ Test authentication flow
3. ⚠️ Test admin operations
4. ⚠️ Test crypto operations
5. ⚠️ Test demo applications

### Phase 4: Native Image Testing (if applicable)
1. ⚠️ Test native compilation
2. ⚠️ Test native runtime behavior
3. ⚠️ Verify AOT configuration

---

## 🎯 Action Items Summary

### High Priority
1. 🔴 **Test Jackson migration thoroughly** - JSON is critical
2. 🔴 **Verify native queries work** - Database operations are critical
3. 🔴 **Run full test suite** - Catch breaking changes early

### Medium Priority
1. 🟡 **Verify actuator configuration** - Monitoring is important
2. 🟡 **Test Spring Security integration** - Security is critical
3. 🟡 **Check Flyway migrations** - Database setup is critical

### Low Priority
1. 🟢 **Clean up hardcoded versions** - Code quality
2. 🟢 **Verify removed dependencies** - Dependency management
3. 🟢 **Test WebJars change** - UI resources

---

## 📝 Notes

1. **Jackson 3.x:** This is a major version change. While the package rename is mechanical, verify all JSON operations work correctly.

2. **Test Coverage:** The project has good test coverage. Running tests will catch most issues.

3. **Native Image:** If using GraalVM native images, test thoroughly as Spring Boot 4 might have AOT changes.

4. **Configuration Properties:** Some property names changed. Verify all `application*.properties` files are updated correctly.

5. **Dependencies:** Some dependencies were added/removed. Verify they're all necessary and properly scoped.

---

## ✅ Conclusion

The migration patch looks **well-structured** and covers most mechanical changes correctly. The main risks are:

1. **Jackson 3.x migration** - Needs thorough testing
2. **Native query changes** - Critical database operations
3. **Test framework changes** - May require test updates

**Recommendation:** Apply the patch, fix compilation errors, run tests, and address issues incrementally. The migration should be **manageable** with proper testing.

**Estimated Effort:** 2-4 days for testing and fixing issues after applying the patch.
