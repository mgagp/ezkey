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

