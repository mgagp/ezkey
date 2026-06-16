# Native Build Implementation for ezkey-auth-api

This document describes the implementation of native image support for the ezkey-auth-api module using Spring Boot 3.3.6 and GraalVM.

## Overview

The ezkey-auth-api now supports native compilation for deployment as a Linux binary optimized for AWS Lambda environments. The implementation uses Spring Boot's native image support with AOT (Ahead-of-Time) processing.

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
- `application-native.properties`: Native-optimized configuration with:
  - AOT enabled
  - Reduced logging
  - Minimal connection pools
  - Disabled Swagger for production

#### Native Image Configuration
Located in `src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/`:

1. **reflect-config.json**: Reflection configuration for DTOs and controllers
2. **resource-config.json**: Resource bundling for properties and validation messages
3. **serialization-config.json**: Serialization hints for JDBC drivers
4. **native-image.properties**: GraalVM build arguments

### 3. AOT Hints Configuration

#### AuthNativeConfiguration.java
Spring configuration class providing runtime hints for:
- All REST endpoint DTOs (reflection and serialization)
- Resource patterns for properties files
- Validation message bundles
- Hibernate and JBoss Logging classes (critical for JPA/Hibernate)

**Configuration Approach**: This implementation uses explicit Java-based configuration via `AuthNativeConfiguration.java` with `RuntimeHintsRegistrar` rather than relying solely on JSON configuration files. This approach provides:
- **Explicit Control**: If something doesn't work, we know exactly where to update
- **Type Safety**: Configuration is verified at compile time
- **Maintainability**: Easier to follow and understand
- **Testability**: Can be unit tested

The JSON configuration files (`reflect-config.json`, `serialization-config.json`, `resource-config.json`) may be redundant with the Java configuration and can potentially be removed after validation testing.

**Important Note**: Hibernate uses JBoss Logging with generated logger classes. These must be explicitly registered for reflection. Common loggers that need to be added:
- `LobCreationLogging` - Required for JDBC environment initialization (added 2025-12-16)
- `CoreMessageLogger` - Core Hibernate logging
- `EntityManagerMessageLogger` - EntityManager operations
- `DeprecationLogger` - Deprecation warnings
- `DialectLogging` - Database dialect operations

**See also**: `docs/NATIVE_IMAGE_CHALLENGES.md` for community context on why this is necessary.

## Build Commands

### Complete Native Build Process (Recommended)

**Standard Production Build**:
```bash
# Step 1: AOT Processing (generates runtime hints and optimizations)
./scripts/build-native-aot.sh --skip-tests

# Step 2: Native Image Compilation (creates native binary)
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -Dmaven.test.skip=true -Dspring-boot.build-image.skip=false
```

**Why Two Steps?**
- AOT processing must complete successfully before native compilation
- The build script ensures proper build order and classpath resolution
- Native compilation reuses AOT-generated files from `target/spring-aot/`

**Rebuilding Native Image (Reusing AOT)**:
If AOT is already generated and you only need to rebuild the native image:
```bash
# Skip AOT, just rebuild native image
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -Dmaven.test.skip=true -Dspring-boot.build-image.skip=false
```

### Individual Commands (For Debugging)

**AOT Processing Only**:
```bash
./scripts/build-native-aot.sh --skip-tests
```

**Native Image Only** (requires AOT to be already generated):
```bash
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -Dmaven.test.skip=true
```

**Direct Native Compilation (Requires GraalVM)**:
```bash
mvn native:compile -pl ezkey-auth-api -Pnative
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
1. **Controllers**: All REST controllers need reflection for request mapping
2. **DTOs**: All request/response DTOs for JSON serialization
3. **JPA Entities**: Database entities for ORM operations
4. **Configuration Classes**: Spring configuration beans

### Resources Required at Runtime
1. Application properties files
2. Validation message bundles
3. Database driver classes
4. Native image configuration files

## Testing the Native Build

### Prerequisites
- Docker installed and running
- Maven 3.6+
- Java 17+ (GraalVM for direct native compilation)

### Buildpack Approach (Recommended)

**Complete Build**:
```bash
# From project root
./scripts/build-native-aot.sh --skip-tests
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image \
    -Dspring-boot.build-image.imageName=ezkey-auth-api-native \
    -Dmaven.test.skip=true \
    -Dspring-boot.build-image.skip=false
```

**Note**: The build script (`build-native-aot.sh`) handles the AOT processing step with proper build order to avoid classpath issues. See `scripts/README.md` for details.

### Running the Native Image
```bash
# Run the native container
docker run -p 8080:8080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/ezkey_db \
    ezkey-auth-api-native
```

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

## Known Issues and Limitations

### Current Environment Constraints
1. **TLS Certificate Issues**: Buildpack may fail in environments with strict TLS certificate validation
2. **Network Access**: Some environments may block external downloads during build
3. **GraalVM Availability**: Direct native compilation requires GraalVM installation

### Solutions for Production
1. **Use CI/CD with GraalVM**: Install GraalVM in build environment
2. **Corporate Networks**: Configure proxy settings for buildpack downloads
3. **Offline Builds**: Cache buildpack layers in corporate registries

### Troubleshooting Approach

When encountering native image issues, follow the systematic troubleshooting strategy defined in `docs/NATIVE_TROUBLESHOOTING_STRATEGY.md`:

1. **Check for disablement options first**: Many issues can be resolved by disabling non-critical features
2. **Evaluate functional impact**: Categorize disablements as Category 1 (fully functional) or Category 2 (non-production-ready)
3. **Document for review**: All disablements should be documented for future optimization

**Current Disablements**: See `docs/NATIVE_TROUBLESHOOTING_STRATEGY.md` for a complete list of current disablements and their status.

## Performance Expectations

### Startup Time
- **JVM**: ~15-20 seconds
- **Native**: ~2-3 seconds

### Memory Usage
- **JVM**: ~200-300MB baseline
- **Native**: ~50-100MB baseline

### Cold Start (AWS Lambda)
- **JVM**: ~5-10 seconds
- **Native**: ~100-500ms

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
- Performance benchmarking vs JVM version

### 3. AWS Lambda Testing
- Create Lambda function with native binary
- Test cold start performance
- Verify integration with AWS services

### 4. Production Readiness
- Add monitoring and logging configuration
- Implement health checks and metrics
- Document deployment procedures

## Current Status (2025-12-19)

### ✅ Completed
- AOT processing configuration and build scripts
- Comprehensive reflection hints for DTOs, mappers, and Hibernate classes
- Native image build arguments configuration
- Application properties optimized for native execution
- Build process documented and scripted

### ⚠️ Blocking Issue
**SQL AST Tree Logger Initialization Failure**

The application fails to start in native mode due to:
```
Invalid logger interface org.hibernate.sql.ast.tree.SqlAstTreeLogger (implementation not found)
```

**Root Cause**: JBoss Logging generates `SqlAstTreeLogger_$logger` at compile time, but this class is not found in the native image. The logger initializes statically during class loading, before configuration properties are read.

**Failed Solutions**:
- ❌ Property-based disablement (`hibernate.sql.log_sql_ast=false`)
- ❌ Logging level disablement
- ❌ Reflection hints for logger classes
- ❌ Runtime initialization configuration

**Impact**: Native image compilation is blocked until this issue is resolved.

### 📋 Next Steps (When Resuming)

See `docs/NATIVE_TROUBLESHOOTING_STRATEGY.md` for detailed next steps, including:
1. Verify generated class existence in dependencies
2. Investigate JBoss Logging class generation process
3. Check Hibernate version compatibility
4. Explore GraalVM substitution mechanism
5. Research community solutions (Quarkus, Spring Boot native image issues)
6. Consider alternative approaches

**Reference Documentation**:
- `docs/NATIVE_TROUBLESHOOTING_STRATEGY.md` - Troubleshooting strategy and current disablements
- `docs/NATIVE_COMPILATION_STRATEGY.md` - Overall native compilation strategy

## Conclusion

The ezkey-auth-api native build implementation is **partially complete**. The configuration and build process are in place, but a blocking issue with Hibernate's JBoss Logging prevents successful native image execution. Work is paused pending resolution of the `SqlAstTreeLogger` initialization issue.

**Quick Reference Commands**:
```bash
# AOT Processing
./scripts/build-native-aot.sh --skip-tests

# Native Image Build (currently blocked by SqlAstTreeLogger issue)
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -Dmaven.test.skip=true -Dspring-boot.build-image.skip=false

# Run Native Container (when issue is resolved)
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=native -d ezkey-auth-api-native
```

