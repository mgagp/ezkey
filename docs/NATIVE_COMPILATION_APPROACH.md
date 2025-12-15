# Native Compilation Configuration Approach

## Overview

This document describes the approach used for native image compilation configuration in the Ezkey project, specifically the preference for explicit Java-based configuration over JSON configuration files.

## Configuration Philosophy

### Explicit Java Configuration (Preferred)

We use explicit Java-based configuration via `RuntimeHintsRegistrar` classes (`AdminNativeConfiguration.java`, `AuthNativeConfiguration.java`) rather than relying solely on JSON configuration files.

### Why Explicit Java Configuration?

1. **Explicit Control**: If something doesn't work, we know exactly where to update
2. **Type Safety**: Configuration is verified at compile time
3. **Maintainability**: Easier to follow and understand
4. **Testability**: Can be unit tested
5. **Version Control**: Changes are tracked in Git with clear diffs
6. **IDE Support**: Full IDE support for refactoring and navigation

### JSON Configuration Files (Potentially Redundant)

The JSON configuration files (`reflect-config.json`, `serialization-config.json`, `resource-config.json`) may be redundant with the Java configuration and can potentially be removed after validation testing.

**Note**: The `native-image.properties` file is **not redundant** as it contains GraalVM-specific build arguments that cannot be expressed in Java configuration.

## Implementation

### AdminNativeConfiguration.java

Located at: `ezkey-admin-api/src/main/java/org/ezkey/admin/config/AdminNativeConfiguration.java`

This configuration class registers:
- **Controllers**: All 9 REST controllers
- **DTOs**: All request/response DTOs (~30+ DTOs)
- **JPA Entities**: All database entities (AuthAttempt, Enrollment, Integration, ApiKey, EzkeyAdmin, Tenant, AdminToken, EncryptionKey, ReencryptionBatch, AuditLog, etc.)
- **Exceptions**: Custom exception classes (ResourceNotFoundException, RateLimitExceededException, etc.)
- **Resources**: Application properties and validation messages
- **Serialization**: Jackson serialization hints for all DTOs and entities

### AuthNativeConfiguration.java

Located at: `ezkey-auth-api/src/main/java/org/ezkey/auth/config/AuthNativeConfiguration.java`

This configuration class registers:
- **Controllers**: All 2 REST controllers
- **DTOs**: All request/response DTOs (8 DTOs)
- **JPA Entities**: Core entities (AuthAttempt, Enrollment, Integration)
- **Resources**: Application properties and validation messages
- **Serialization**: Jackson serialization hints for all DTOs and entities

## Redundancy Testing

### Hypothesis

The JSON configuration files (`reflect-config.json`, `serialization-config.json`, `resource-config.json`) are redundant with the Java configuration classes and can be removed.

### Testing Approach

1. Build native image with Java configuration only
2. Test execution of native image
3. Remove JSON files and rebuild
4. Test execution again
5. If successful: JSON files are redundant and can be removed
6. If failures occur: Identify missing configuration and add to Java configuration

### Current Status

- ✅ AdminNativeConfiguration.java created with comprehensive configuration
- ✅ AuthNativeConfiguration.java exists with comprehensive configuration
- ⏳ Redundancy testing pending (requires native build environment)

## Files That Are NOT Redundant

### native-image.properties

**Location**: `src/main/resources/META-INF/native-image/org.ezkey/{api-name}/native-image.properties`

**Purpose**: Contains GraalVM-specific build arguments that cannot be expressed in Java configuration.

**Content**: Build arguments like `--no-fallback`, `-H:+ReportExceptionStackTraces`, etc.

**Status**: **Keep** - Required for native compilation, not redundant.

### application.native.properties

**Location**: `src/main/resources/application.native.properties`

**Purpose**: Spring Boot configuration optimized for native images (reduced logging, disabled Swagger, etc.).

**Status**: **Keep** - Spring Boot configuration, not related to reflection/serialization hints.

## Migration Path

### For New APIs

1. Create `{ApiName}NativeConfiguration.java` with `RuntimeHintsRegistrar`
2. Register all controllers, DTOs, entities, exceptions, resources, and serialization hints
3. Test native compilation
4. Test redundancy hypothesis (remove JSON files and rebuild)
5. Remove redundant JSON files if validation succeeds

### For Existing APIs

1. Review existing JSON configuration files
2. Create equivalent Java configuration class
3. Ensure Java configuration covers all JSON entries
4. Test native compilation with Java configuration
5. Test redundancy hypothesis
6. Remove redundant JSON files if validation succeeds

## Best Practices

### 1. Comprehensive Registration

Register all classes that need reflection or serialization:
- Controllers (for request mapping)
- DTOs (for JSON serialization)
- Entities (for JPA operations)
- Exceptions (for exception handling)
- Inner classes (using TypeReference.of with class name string)

### 2. Organized Structure

Group registrations logically:
```java
// Register controllers
hints.reflection().registerType(Controller1.class)...
// Register DTOs
hints.reflection().registerType(Dto1.class)...
// Register entities
hints.reflection().registerType(Entity1.class)...
```

### 3. Documentation

Document what each section registers and why:
```java
// Register Integration DTOs for reflection
// These DTOs are used in IntegrationController endpoints
hints.reflection().registerType(IntegrationCreateRequestDto.class)...
```

### 4. Consistency

Use consistent patterns across all APIs:
- Same structure for reflection and serialization registration
- Same resource patterns
- Same exception handling

## Troubleshooting

### Missing Reflection Configuration

**Symptom**: `ClassNotFoundException` or `NoSuchMethodException` at runtime in native image.

**Solution**: Add missing class to reflection registration in `{ApiName}NativeConfiguration.java`.

### Missing Serialization Configuration

**Symptom**: JSON serialization fails for certain DTOs in native image.

**Solution**: Add missing DTO to serialization registration in `{ApiName}NativeConfiguration.java`.

### Missing Resource Configuration

**Symptom**: Properties files or validation messages not found at runtime.

**Solution**: Add resource pattern to resource registration in `{ApiName}NativeConfiguration.java`.

## Conclusion

The explicit Java-based configuration approach provides better maintainability, type safety, and explicit control over native image compilation requirements. While JSON files may be redundant, they serve as a fallback during the transition period and can be removed after validation testing confirms the Java configuration is complete.
