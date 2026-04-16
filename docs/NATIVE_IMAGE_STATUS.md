# Native Image Compilation Status

**Last Updated**: 2026-04-15  
**Status**: Archived after bounded spike; build-time path works, runtime native path remains non-viable.

## Quick Summary

The Spring Boot 4 native initiative no longer has a single active blocker.

- `ezkey-auth-api` native builds, but still fails at runtime during Hibernate / JPA bootstrap.
- `ezkey-integration-api` native builds, but still fails at runtime during JPA / Hikari startup.
- `ezkey-admin-api` native support is no longer part of the active strategy.

This file is now a historical status snapshot. The canonical reference is
`docs/NATIVE_INITIATIVE_STATUS_2026-04.md`.

## Current State

### ✅ Completed
- Parent native toolchain alignment for Spring Boot 4.0.5
- Auth API native profile and iterative hint fixes
- Integration API native profile and runtime hints alignment
- Docker native compose rationalization around Auth + Integration
- Root Maven validation and targeted AOT checks
- Native image builds for Auth API and Integration API

### ⚠️ Final Runtime Outcome

**Auth API native**

- Progressed beyond earlier JBoss Logging failures.
- Latest confirmed blocker: `ClassNotFoundException: org.hibernate.dialect.type.PostgreSQLInetJdbcType`.

**Integration API native**

- Rebuilt and retested after Hibernate / PostgreSQL hint alignment.
- Rebuilt again with `SPRING_PROFILES_ACTIVE=docker,native` active during build.
- Latest confirmed blocker: `NullPointerException` from `com.zaxxer.hikari.pool.HikariPool`
	during `entityManagerFactory` creation.

## Next Steps (When Resuming)

1. Revalidate the exact Spring Boot, Hibernate, GraalVM, and Native Build Tools combination.
2. Reproduce each API in isolation before using the full Docker stack.
3. Treat profile selection during AOT and runtime as an explicit validation point.
4. Restart only as a time-boxed spike with a stop criterion.

## Key Files

- `ezkey-auth-api/src/main/java/org/ezkey/auth/config/AuthNativeConfiguration.java` - AOT hints
- `ezkey-integration-api/src/main/java/org/ezkey/integration/api/config/IntegrationNativeConfiguration.java` - Integration native hints
- `ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/native-image.properties` - GraalVM build args
- `ezkey-integration-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-integration-api/native-image.properties` - Integration GraalVM build args
- `docker/docker-compose.native.yml` - Current native stack definition used during the spike
- `docs/NATIVE_INITIATIVE_STATUS_2026-04.md` - Canonical closure note

## Build Commands

```bash
# AOT processing (works)
./scripts/build-native-aot.sh --skip-tests

# Native image builds (work, but runtime remains blocked)
mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -DskipTests -Dspring-boot.build-image.skip=false
mvn -pl ezkey-integration-api -Pnative spring-boot:build-image -DskipTests
```

## Related Documentation

- `docs/NATIVE_INITIATIVE_STATUS_2026-04.md` - Canonical April 2026 closure note
- `docs/NATIVE_TROUBLESHOOTING_STRATEGY.md` - Systematic troubleshooting approach
- `docs/NATIVE_COMPILATION_STRATEGY.md` - Overall native compilation strategy
- `ezkey-auth-api/NATIVE_BUILD.md` - Detailed build documentation
