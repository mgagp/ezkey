---
name: Quarkus Migration Evaluation
overview: Evaluate and potentially convert the Authorization API from Spring Boot to Quarkus for simplified native compilation. Create a separate subproject (authorization-api-quarkus) to test the conversion without destabilizing the current working version.
todos: []
isProject: false
---

# Quarkus Migration Evaluation and Conversion Plan

## Executive Summary

The Authorization API is experiencing significant challenges with Spring Boot native compilation (iterative process lasting days). This plan evaluates converting to Quarkus, which is designed from the ground up for native compilation and offers first-class support for MapStruct and JPA.

## Current State Analysis

### Authorization API Structure

- **Controllers**: 2 REST controllers (`AuthAttemptController`, `EnrollmentController`)
- **Mappers**: 2 MapStruct mappers (`AuthAttemptMapper`, `EnrollmentAuthMapper`)
- **Persistence**: JPA/Hibernate with Spring Data JPA repositories (entities in `ezkey-core`)
- **Dependencies**: 
- MapStruct 1.6.3 (problematic in native)
- Spring Data JPA (problematic in native)
- Bucket4j (rate limiting)
- SpringDoc OpenAPI
- Audit logging service

### Current Native Compilation Issues

- Complex AOT processing with manual hints configuration
- MapStruct compilation order issues requiring custom build scripts
- Intermittent classpath resolution problems
- Heavy iterative debugging process

## Quarkus Advantages

### 1. Native Compilation

- **First-class support**: Quarkus is designed for native compilation from the start
- **Simplified process**: No manual AOT hints configuration required
- **Better tooling**: Built-in native image testing and debugging
- **Faster builds**: Optimized build process with better caching

### 2. Dependency Compatibility

- **MapStruct**: Official `quarkus-mapstruct` extension with native support
- **JPA/Hibernate**: `quarkus-hibernate-orm` with full native compatibility
- **Spring Data JPA**: `quarkus-spring-data-jpa` compatibility layer allows reuse of existing repositories
- **OpenAPI**: `quarkus-smallrye-openapi` for API documentation

### 3. Performance Benefits

- **Startup time**: 2-3 seconds (native) vs 15-20 seconds (JVM Spring Boot)
- **Memory footprint**: 50-100MB (native) vs 200-300MB (JVM Spring Boot)
- **Cold start**: 100-500ms (native) vs 5-10 seconds (JVM Spring Boot)

## Migration Strategy

### Phase 1: Evaluation (Week 1)

1. **Create separate subproject**: `ezkey-auth-api-quarkus` (parallel to `ezkey-auth-api`)
2. **Set up Quarkus project structure**:

- Maven module with Quarkus BOM
- Basic configuration files
- Dependencies mapping

1. **Test dependency compatibility**:

- Verify `ezkey-core` can be used as dependency
- Test MapStruct generation in Quarkus context
- Validate JPA entity scanning

### Phase 2: Core Conversion (Week 2-3)

1. **Convert application entry point**:

- Replace `@SpringBootApplication` with Quarkus main class
- Configure package scanning

1. **Convert controllers**:

- Replace `@RestController` with Quarkus `@Path` annotations (JAX-RS)
- Convert Spring `@RequestMapping` to JAX-RS `@Path`
- Update dependency injection (constructor injection works the same)

1. **Convert configuration**:

- `application.properties` → `application.properties` (Quarkus format)
- JPA configuration → `quarkus.hibernate-orm.`*
- Database configuration → `quarkus.datasource.*`

1. **MapStruct integration**:

- Add `quarkus-mapstruct` extension
- Verify mapper generation works
- Test mapper injection

### Phase 3: Advanced Features (Week 3-4)

1. **JPA/Repository conversion**:

- Use `quarkus-spring-data-jpa` for repository compatibility
- OR convert to Quarkus Panache repositories
- Test entity scanning and repository injection

1. **Rate limiting**:

- Evaluate Bucket4j compatibility
- Consider Quarkus rate limiting extensions

1. **OpenAPI documentation**:

- Convert SpringDoc annotations to OpenAPI annotations
- Configure `quarkus-smallrye-openapi`

1. **Exception handling**:

- Convert `@ControllerAdvice` to Quarkus exception mappers
- Update error response format if needed

### Phase 4: Testing and Validation (Week 4-5)

1. **Unit tests**:

- Convert Spring Boot Test to QuarkusTest
- Update test configuration

1. **Integration tests**:

- Test all endpoints
- Verify database operations
- Validate MapStruct mappings

1. **Native compilation test**:

- Build native image: `mvn clean package -Pnative`
- Compare build time and complexity
- Test native binary execution

1. **Performance comparison**:

- Startup time
- Memory usage
- Response times

## Technical Considerations

### File Structure

```javascript
ezkey-auth-api-quarkus/
├── pom.xml (Quarkus BOM, extensions)
├── src/main/java/
│   └── org/ezkey/auth/
│       ├── AuthApplication.java (Quarkus main)
│       ├── controller/ (JAX-RS resources)
│       ├── config/ (Quarkus configuration)
│       └── ...
├── src/main/resources/
│   ├── application.properties (Quarkus format)
│   └── META-INF/
│       └── native-image/ (if needed)
└── src/test/java/ (QuarkusTest)
```

### Key Dependencies Mapping

| Spring Boot | Quarkus ||------------|---------|| `spring-boot-starter-web` | `quarkus-resteasy-reactive` or `quarkus-vertx-web` || `spring-boot-starter-data-jpa` | `quarkus-hibernate-orm` + `quarkus-spring-data-jpa` || `mapstruct` | `quarkus-mapstruct` || `springdoc-openapi` | `quarkus-smallrye-openapi` || `spring-boot-starter-validation` | `quarkus-hibernate-validator` |

### Configuration Conversion Examples

**Spring Boot** (`application.properties`):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ezkey_db
spring.jpa.hibernate.ddl-auto=validate
server.port=8080
```

**Quarkus** (`application.properties`):

```properties
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ezkey_db
quarkus.hibernate-orm.database.generation=validate
quarkus.http.port=8080
```

## Risk Assessment

### Low Risk

- MapStruct: Official Quarkus extension available
- JPA entities: Standard JPA annotations work in Quarkus
- Basic REST endpoints: Straightforward conversion

### Medium Risk

- Spring Data JPA repositories: Compatibility layer exists but may need adjustments
- Rate limiting: Bucket4j may need alternative implementation
- Exception handling: Different patterns in Quarkus

### High Risk

- `ezkey-core` dependency: Need to ensure compatibility
- Audit logging service: May need refactoring
- Complex business logic: Needs thorough testing

## Success Criteria

1. **Native compilation**: Builds successfully without manual hints configuration
2. **Functionality**: All endpoints work identically to Spring Boot version
3. **Performance**: Faster startup and lower memory usage
4. **Build time**: Simpler and faster native compilation process
5. **Code maintainability**: Clean, readable Quarkus code

## Decision Points

### Go/No-Go Criteria (After Phase 1)

- Can `ezkey-core` be used as dependency? ✅/❌
- Does MapStruct generate correctly? ✅/❌
- Can JPA entities be scanned? ✅/❌

### Full Migration Decision (After Phase 4)

- Native compilation is significantly simpler? ✅/❌
- All functionality works correctly? ✅/❌
- Performance improvements are significant? ✅/❌

## Estimated Effort

- **Phase 1 (Evaluation)**: 1 week
- **Phase 2 (Core Conversion)**: 2 weeks
- **Phase 3 (Advanced Features)**: 2 weeks
- **Phase 4 (Testing)**: 1-2 weeks
- **Total**: 6-7 weeks

## Next Steps

1. **Immediate**: Create `ezkey-auth-api-quarkus` subproject structure
2. **Week 1**: Set up Quarkus project and test dependency compatibility
3. **Week 2-3**: Convert core functionality (controllers, configuration)
4. **Week 4**: Convert advanced features (JPA, rate limiting)
5. **Week 5-6**: Testing and validation
6. **Week 7**: Documentation and decision on full migration

## References

