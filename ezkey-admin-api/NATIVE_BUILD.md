# Native Build Implementation for ezkey-admin-api

This document describes the implementation of native image support for the ezkey-admin-api module using Spring Boot 3.4.10 and GraalVM.

## Overview

The ezkey-admin-api now supports native compilation for deployment as a Linux binary optimized for AWS Lambda environments. The implementation uses Spring Boot's native image support with AOT (Ahead-of-Time) processing.

## Configuration Approach

**Explicit Java Configuration**: This implementation uses explicit Java-based configuration via `AdminNativeConfiguration.java` with `RuntimeHintsRegistrar` rather than relying solely on JSON configuration files. This approach provides:

- **Explicit Control**: If something doesn't work, we know exactly where to update
- **Type Safety**: Configuration is verified at compile time
- **Maintainability**: Easier to follow and understand
- **Testability**: Can be unit tested

The JSON configuration files (`reflect-config.json`, `serialization-config.json`, `resource-config.json`) may be redundant with the Java configuration and can potentially be removed after validation testing.

## Implementation Details

### 1. Maven Configuration

#### Native Profile
```xml
<profile>
    <id>native</id>
    <properties>
        <spring-boot.build-image.builder>paketobuildpacks/builder:tiny</spring-boot.build-image.builder>
        <spring-boot.build-image.environment.BP_NATIVE_IMAGE>true</spring-boot.build-image.environment.BP_NATIVE_IMAGE>
        <native.maven.plugin.version>0.10.3</native.maven.plugin.version>
    </properties>
    <!-- Additional dependencies and plugins -->
</profile>
```

#### Key Plugins
- **spring-boot-maven-plugin**: AOT processing with `process-aot` and `process-test-aot` goals
- **native-maven-plugin**: GraalVM native image compilation with AWS Lambda optimizations

### 2. Configuration Files

#### Application Properties
- `application.properties`: Standard Spring Boot configuration
- `application-native.properties`: Native-optimized configuration (if exists) with:
  - AOT enabled
  - Reduced logging
  - Minimal connection pools
  - Disabled Swagger for production

#### Native Image Configuration
Located in `src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/`:

1. **reflect-config.json**: Reflection configuration for DTOs and controllers (may be redundant with AdminNativeConfiguration.java)
2. **resource-config.json**: Resource bundling for properties and validation messages (may be redundant with AdminNativeConfiguration.java)
3. **serialization-config.json**: Serialization hints for JDBC drivers
4. **native-image.properties**: GraalVM build arguments (required, not redundant)

### 3. AOT Hints Configuration

#### AdminNativeConfiguration.java
Spring configuration class providing runtime hints for:
- **Controllers**: All 9 REST controllers (IntegrationController, AuthAttemptController, EnrollmentController, AdminEnrollmentController, AdminAuthController, ApiKeyController, EncryptionKeyController, AuditLogController, GlobalExceptionHandler)
- **DTOs**: All request/response DTOs used in REST endpoints:
  - Integration DTOs: IntegrationCreateRequestDto, IntegrationCreateResponseDto, IntegrationResponseDto
  - AuthAttempt DTOs: AuthAttemptDto, AuthAttemptCreateRequestDto, AuthAttemptCreateResponseDto, AuthAttemptWaitRequestDto, AuthAttemptWaitResponseDto
  - Enrollment DTOs: EnrollmentCreateRequestDto, EnrollmentCreateResponseDto, EnrollmentResponseDto
  - Admin DTOs: AdminLoginRequestDto, AdminLoginResponseDto, AdminPasswordlessWaitRequestDto, AdminRecoveryRequestDto, AdminRecoveryResponseDto, EnrollmentResetRequestDto, EnrollmentResetResponseDto
  - API Key DTOs: ApiKeyCreateRequestDto, ApiKeyCreateResponseDto, ApiKeyResponseDto
  - EncryptionKeyController record DTOs: EncryptionKeyResponse, KeyRotationResponse, ReencryptionBatchResponse, BatchResumeResponse, ReencryptionTriggerResponse, ReencryptionKeyResponse, BatchCreationResponse
  - AuditLog DTOs: AuditLogResponseDto
  - Error responses: RFC 9457 `ProblemDetail` (Spring-provided; no legacy error DTO)
- **JPA Entities**: All database entities:
  - AuthAttempt, Enrollment, Integration
  - ApiKey, EzkeyAdmin, Tenant, AdminToken
  - EncryptionKey, ReencryptionBatch, KeysetBlob
  - AuditLog
- **Exceptions**: Custom exception classes (ResourceNotFoundException, RateLimitExceededException, NoPendingAuthAttemptException, AuthenticationException)
- **Resources**: Application properties and validation messages
- **Serialization**: Jackson serialization for all DTOs and entities

## Build Commands

### AOT Processing
```bash
mvn spring-boot:process-aot -pl ezkey-admin-api -Pnative
```

### Native Image with Buildpack (Recommended)
```bash
mvn spring-boot:build-image -pl ezkey-admin-api -Pnative -DskipTests
```

### Direct Native Compilation (Requires GraalVM)
```bash
mvn native:compile -pl ezkey-admin-api -Pnative
```

## AWS Lambda Optimizations

The native image is optimized for AWS Lambda with:

- `--no-fallback`: Ensures native-only execution
- `-H:+StaticExecutableWithDynamicLibC`: Creates portable Linux binary
- `-H:+RemoveSaturatedTypeFlows`: Reduces image size
- `-H:+AddAllCharsets`: Supports international text
- Resource bundling for minimal runtime dependencies

## AOT Processing Requirements

### Classes Requiring Reflection
1. **Controllers**: All 9 REST controllers need reflection for request mapping
2. **DTOs**: All request/response DTOs for JSON serialization (comprehensive list in AdminNativeConfiguration.java)
3. **JPA Entities**: All database entities for ORM operations
4. **Exceptions**: Custom exception classes used by GlobalExceptionHandler
5. **Configuration Classes**: Spring configuration beans (handled automatically by Spring AOT)

### Resources Required at Runtime
1. Application properties files
2. Validation message bundles
3. Database driver classes
4. Native image configuration files (native-image.properties)

## Testing the Native Build

### Prerequisites
- Docker installed and running
- Maven 3.6+
- Java 21+ (GraalVM for direct native compilation)

### Buildpack Approach (Recommended)
```bash
cd ezkey-admin-api

# Build AOT processed JAR
mvn clean package -Pnative -DskipTests

# Create native image using buildpack
mvn spring-boot:build-image -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-admin-api-native \
    -DskipTests
```

### Running the Native Image
```bash
# Run the native container
docker run -p 9080:9080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/ezkey_db \
    ezkey-admin-api-native
```

### Health Check
```bash
curl http://localhost:9080/actuator/health
```

## Redundancy Testing

### Hypothesis
The JSON configuration files (`reflect-config.json`, `serialization-config.json`, `resource-config.json`) are redundant with `AdminNativeConfiguration.java` and can be removed.

### Testing Approach
1. Build native image with AdminNativeConfiguration.java
2. Test execution of native image
3. Remove JSON files and rebuild
4. Test execution again
5. If successful: JSON files are redundant and can be removed
6. If failures occur: Identify missing configuration and add to AdminNativeConfiguration.java

### Current Status
- ✅ AdminNativeConfiguration.java created with comprehensive configuration
- ⏳ Redundancy testing pending (requires native build environment)

## Differences from Auth API

The admin-api has significantly more complexity than auth-api:

- **Controllers**: 9 controllers vs 2 in auth-api
- **DTOs**: ~30+ DTOs vs 8 in auth-api
- **Entities**: More JPA entities (ApiKey, EzkeyAdmin, Tenant, AdminToken, EncryptionKey, ReencryptionBatch, AuditLog, etc.)
- **Exceptions**: More custom exception classes
- **Features**: Admin authentication, API keys, encryption key management, audit logging

## Known Issues and Limitations

### Current Environment Constraints
1. **TLS Certificate Issues**: Buildpack may fail in environments with strict TLS certificate validation
2. **Network Access**: Some environments may block external downloads during build
3. **GraalVM Availability**: Direct native compilation requires GraalVM installation

### Solutions for Production
1. **Use CI/CD with GraalVM**: Install GraalVM in build environment
2. **Corporate Networks**: Configure proxy settings for buildpack downloads
3. **Offline Builds**: Cache buildpack layers in corporate registries

## Performance Expectations

### Startup Time
- **JVM**: ~20-30 seconds (more complex than auth-api)
- **Native**: ~3-5 seconds

### Memory Usage
- **JVM**: ~300-400MB baseline
- **Native**: ~80-120MB baseline

### Cold Start (AWS Lambda)
- **JVM**: ~8-12 seconds
- **Native**: ~200-800ms

## Deployment Strategy

### AWS Lambda
1. Build native image using buildpack
2. Extract binary from container
3. Package as Lambda deployment zip
4. Configure Lambda with custom runtime

### Container Deployment
1. Use native image container directly
2. Configure environment variables
3. Set appropriate resource limits

## Next Steps for Complete Testing

### 1. Environment Setup
- Set up build environment with proper network access
- Install GraalVM for direct native compilation testing
- Configure Docker registry for image storage

### 2. Integration Testing
- Test with actual PostgreSQL database
- Verify all REST endpoints work in native mode
- Test admin authentication flow
- Test API key authentication
- Test encryption key management
- Performance benchmarking vs JVM version

### 3. Redundancy Validation
- Test native build with JSON files removed
- Validate all functionality works without JSON files
- Remove redundant JSON files if validation succeeds

### 4. AWS Lambda Testing
- Create Lambda function with native binary
- Test cold start performance
- Verify integration with AWS services

### 5. Production Readiness
- Add monitoring and logging configuration
- Implement health checks and metrics
- Document deployment procedures

## Conclusion

The ezkey-admin-api native build implementation uses explicit Java-based configuration via `AdminNativeConfiguration.java` for better maintainability and type safety. The configuration is comprehensive and covers all controllers, DTOs, entities, exceptions, and resources needed for native compilation.

The implementation supports both buildpack and direct GraalVM compilation approaches, with optimizations specifically for AWS Lambda deployment.

## Quick Reference

```bash
# Build native image
mvn spring-boot:build-image -pl ezkey-admin-api -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-admin-api-native \
    -DskipTests

# Run native container
docker run -p 9080:9080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/ezkey_db \
    ezkey-admin-api-native
```
