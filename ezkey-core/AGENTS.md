# Ezkey Core — Agent Notes

For agents working in `ezkey-core/`.

## JPA entity defaults

- **Timestamps** (`createdAt`, `updatedAt`): `@PrePersist` / `@PreUpdate` when null only.
- **Other defaults** (`active`, status): field init. No `@PrePersist` for these.
- **MapStruct** create→entity: ignore `id`, `createdAt`, `active` (etc.). Example: `IntegrationServiceMapper.toEntity`.

## Non-negotiables

- All project content **in English**.
- Constructor injection; no `@Autowired`.

## Testing philosophy: Opportunistic database validation

**Critical security principle**: Security functionality must be validated at all times, not just through API responses.

- **Use APIs primarily**: Tests should primarily use and exercise the APIs as real clients would.
- **Be opportunistic**: When relevant and appropriate, use `DatabaseHelper` to validate or cross-check database state directly.
- **Security validation**: For security-critical features, it is imperative to verify the actual database state, not just API responses. This ensures security controls are working correctly "under the hood".
- **When to use**: Use `DatabaseHelper` for:
  - State verification (faster than API calls when appropriate)
  - Finding existing entities before creating new ones
  - Cross-validation of security constraints (e.g., uniqueness constraints, status transitions)
  - Cleanup operations for test idempotence
  - Resetting state when needed

**Example**: When testing enrollment uniqueness, verify both:
1. API rejects duplicate creation (API-level validation)
2. Database constraint prevents duplicate VERIFIED enrollments (database-level validation)

This dual validation ensures security is enforced at both application and database levels.
