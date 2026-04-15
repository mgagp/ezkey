# Native Initiative Status - April 2026

## Status

The Spring Boot 4 / GraalVM native initiative was executed as a bounded spike and is now
considered closed for day-to-day operational use.

The repository cleanup and tooling alignment work was completed, but the native Docker runtime
remains non-viable for the two target APIs.

## What Was Validated

- Parent native toolchain aligned to Spring Boot 4.0.5 expectations.
- Admin API native artifacts removed from the active strategy.
- Auth API and Integration API native profiles, hints, and Docker flow were aligned.
- Root Maven baseline succeeded: `checkstyle`, `clean`, `install -DskipTests`.
- Targeted AOT checks succeeded for Auth and Integration.
- Native images were successfully built for Auth and Integration.

## Runtime Outcome

### Auth API native

The native image progressed through multiple fixes, but still failed at runtime in the Docker
stack.

Confirmed progression during the spike:

- Missing `ObjectMapper` fixed with a fallback bean.
- Caffeine reflection gap fixed.
- Hibernate / JBoss Logging gaps fixed far enough to move past `JpaLogger` and `BootLogging`.
- Final bounded pass still failed during JPA bootstrap with Hibernate PostgreSQL type loading.

Last confirmed runtime blocker:

- `ClassNotFoundException: org.hibernate.dialect.type.PostgreSQLInetJdbcType`

### Integration API native

Integration was retested as a final bounded pass because its surface is conceptually simpler than
Auth.

Confirmed progression during the spike:

- Runtime hints were aligned with the Hibernate / JBoss Logging / PostgreSQL coverage needed by
  Auth.
- `compile` plus `spring-boot:process-aot` succeeded.
- Native image rebuild succeeded.
- Rebuild with `SPRING_PROFILES_ACTIVE=docker,native` at build time did not change the runtime
  result.

Last confirmed runtime blocker:

- Docker container still exits unhealthy during JPA bootstrap.
- Failure ends in `NullPointerException` from `com.zaxxer.hikari.pool.HikariPool`
  during `entityManagerFactory` creation.

## Decision

For the current stack, native compilation should be treated as experimental only.

Operational recommendation now:

- Admin API: JVM only.
- Auth API: JVM only.
- Integration API: JVM only.
- Native docs and scripts: keep only as historical or future-investigation material, not as an
  implied supported runtime path.

## Future Restart Notes

If this initiative is reopened later, do not restart from broad repo exploration. Start from the
last confirmed runtime boundaries above.

Recommended restart order:

1. Revalidate the exact Spring Boot / Hibernate / GraalVM / Native Build Tools combination against
   current community metadata and known issues.
2. Reproduce each API in isolation before using the full Docker stack.
3. Keep build-time and runtime profile assumptions explicit for Spring AOT.
4. Budget the work as a time-boxed spike with a stop criterion, not as an open-ended migration.

## Canonical Use Of This Note

Use this file as the primary reference for the April 2026 outcome.

Archived plans and older native guides should be read as historical implementation context, not as
current support commitments.