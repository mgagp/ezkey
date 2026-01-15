# Test Philosophy: Opportunistic E2E Testing Strategy

## Core Principles

### 1. Opportunistic Resource Usage
Tests should opportunistically use available resources in the Docker clean room environment:

- **Local Files**: Use `.ezkey-test/*.json` for state persistence (bootstrap credentials, tokens, device credentials)
- **Database Direct Access**: Query/update PostgreSQL directly via `docker exec psql` when appropriate
- **Container Logs**: Read Docker container logs for state information
- **REST APIs**: Use APIs when they are the most appropriate or only available method

### 2. Test Independence & Idempotence
- Tests should be independent and idempotent
- Tests should handle existing state gracefully (reuse, reset, or skip as appropriate)
- Tests should clean up after themselves when necessary, but prefer reuse over cleanup

### 3. Real-World Validation
- Tests run in production-like Docker environment
- Security features remain enabled (no bypass)
- Tests validate actual behavior, not mocked behavior

## Resource Usage Guidelines

### When to Use Each Resource

| Resource | Use Case | Example |
|----------|----------|---------|
| **Local Files** | State persistence, caching, fast lookups | `admin-token.json`, `device-credentials.json` |
| **Database Direct** | State verification, cleanup, reset operations | Check enrollment status, reset enrollment state |
| **Container Logs** | Initial state extraction, debugging | Bootstrap credentials extraction |
| **REST APIs** | Business logic validation, operations requiring authentication | Create integration, create enrollment, authenticate |

### Decision Tree

```
Need to check/create entity?
├─ Is it a bootstrap/admin token?
│  └─ Use local files + AdminBootstrapService (handles DB/API internally)
├─ Is it test data (integration, enrollment)?
│  ├─ Check DB first (if appropriate)
│  ├─ Reuse existing if matches criteria
│  └─ Create via API if not found
└─ Is it state verification?
   ├─ Use DB for fast, unauthenticated checks
   └─ Use API for authenticated, business-logic validation
```

## Current Implementation Status

### ✅ Already Opportunistic
- **AdminBootstrapService**: Uses files, DB, and APIs opportunistically
- **BootstrapCredentialsExtractor**: Reads Docker logs and caches to files
- **AuthTokenManager**: Uses cached tokens from files

### ⚠️ Could Be More Opportunistic
- **TestDataFactory**: Always creates new entities via API (could check/reuse existing)
- **Test Cleanup**: No cleanup mechanism (could use DB for cleanup)
- **State Verification**: Some tests only use API (could use DB for faster checks)

## Recommendations

### High Priority
1. **TestDataFactory Enhancement**: Add methods to find/reuse existing entities before creating new ones
2. **Database Helper Utility**: Create a `DatabaseHelper` utility for common DB operations
3. **Test Cleanup Strategy**: Add optional cleanup mechanisms using DB direct access

### Medium Priority
4. **State Verification Helpers**: Add helpers that use DB for fast state checks
5. **Entity Reuse Patterns**: Document patterns for reusing test entities

### Low Priority
6. **Log Analysis Utilities**: Expand log reading capabilities for debugging
7. **Performance Optimization**: Use DB for bulk operations instead of API loops

## Philosophy alignment notes (consistency checklist)

This section captures lessons learned while aligning existing tests with the opportunistic E2E strategy (especially around admin bootstrap and token acquisition).

### 1. Apply the opportunistic strategy consistently

`AdminBootstrapService` is the reference implementation of the opportunistic approach:

- **Local files**: cache bootstrap artifacts and tokens in `.ezkey-test/*.json`
- **Direct DB checks**: verify/reset enrollment state when API responses are insufficient for diagnosis or for safe recovery
- **Docker logs**: extract initial bootstrap credentials when needed

When adding new utilities (or extending `TestDataFactory`), prefer the same approach: optimize for **idempotence** and **diagnosability** first, performance second.

### 2. Reuse vs accumulation: use the right tool for the job

- **Data accumulation is a feature** for functional E2E suites: tests must remain correct as datasets grow.
- **Reuse is an optimization**, not a correctness requirement:
  - Prefer **unique suffixes + explicit filters/pagination** for correctness under accumulation.
  - Use **find-or-create** patterns selectively when the goal is to avoid expensive setup (or when a test is explicitly designed as a “building block” / pre-warm).

### 3. Avoid “file-only resets” that can desynchronize state

Deleting `.ezkey-test/*.json` (or deleting only `device-credentials.json`) can create confusing states where:

- files are gone locally,
- but the enrollment is still `BOUND`/`VERIFIED` in the database.

When you need a forced fresh bootstrap, prefer a dedicated building-block test (e.g., `AdminInitialBootstrapTest`) and document that it intentionally forces a full bootstrap.

### 4. Use DB direct access for setup/cleanup/diagnosis — not to bypass behavior under test

- **Good uses**: fast existence/status checks, safe recovery from partially-completed bootstrap, optional cleanup when a test *must* reset state for correctness.
- **Bad uses**: replacing API assertions with DB checks for the core behavior being validated.

The API should remain the primary validation surface; DB access is a supporting tool.

