---
name: Fix PostgreSQL partition function return type
overview: Fix the mismatch between PostgreSQL function returning VOID and Java code using executeUpdate() with SELECT, which causes "A result was returned when none was expected" error. Change function to return BOOLEAN and update Java code to use executeQuery() for better observability.
todos:
  - id: "1"
    content: Update PostgreSQL function to return BOOLEAN instead of VOID in V25 migration file
    status: completed
  - id: "2"
    content: Update Java service to use executeQuery() instead of executeUpdate() and handle BOOLEAN result
    status: completed
  - id: "3"
    content: Update logging to reflect whether partition was created or already existed
    status: completed
  - id: "4"
    content: Update function documentation and JavaDoc comments
    status: completed
isProject: false
---

# Fix PostgreSQL Partition Function Return Type Issue

## Problem Analysis

The error occurs because:

1. **PostgreSQL function** `create_monthly_partition` is defined as `RETURNS VOID`
2. **Java code** uses `SELECT create_monthly_partition(...)` with `executeUpdate()`
3. **PostgreSQL behavior**: When using `SELECT` with a function, PostgreSQL returns a result set (even for VOID functions, it returns one row with NULL)
4. **Hibernate behavior**: `executeUpdate()` expects no result set, causing the error "A result was returned when none was expected"

## Root Cause

The mismatch between:

- Function signature: `RETURNS VOID`
- Java call pattern: `SELECT function()` with `executeUpdate()`

## Best Practices Research

Based on PostgreSQL and Hibernate best practices:

1. **For void functions**: Use `StoredProcedureQuery` (more complex, requires parameter registration)
2. **For functions with return values**: Use `executeQuery()` with native queries (simpler, more flexible)
3. **Idempotent operations**: Should return status indicators (BOOLEAN or TEXT) for better observability

## Recommended Solution

**Change function to return BOOLEAN** and use `executeQuery()`:

- `true` = partition was created
- `false` = partition already existed (idempotent)
- Provides better observability and logging
- Simpler than `StoredProcedureQuery`
- Follows PostgreSQL best practices for idempotent DDL operations

## Implementation Plan

### 1. Update PostgreSQL Function

**File**: `ezkey-core/src/main/resources/db/migration/V25__create_partition_management_function.sql`

Change function signature from:

```sql
RETURNS VOID
```

To:

```sql
RETURNS BOOLEAN
```

Update function body to return:

- `true` when partition is created
- `false` when partition already exists

### 2. Update Java Service

**File**: `ezkey-core/src/main/java/org/ezkey/database/service/PartitionSchedulerService.java`

Change `createPartitionIfNotExists()` method:

- Replace `executeUpdate()` with `executeQuery()`
- Extract BOOLEAN result from query
- Update logging to reflect whether partition was created or already existed

### 3. Update Documentation

**Files to update**:

- Function comment in SQL migration
- JavaDoc in `PartitionSchedulerService.java`
- Any related documentation files

## Files to Modify

1. `ezkey-core/src/main/resources/db/migration/V25__create_partition_management_function.sql`

   - Change `RETURNS VOID` to `RETURNS BOOLEAN`
   - Add `RETURN true;` after partition creation
   - Add `RETURN false;` when partition already exists

2. `ezkey-core/src/main/java/org/ezkey/database/service/PartitionSchedulerService.java`

   - Change `executeUpdate()` to `executeQuery()`
   - Extract and handle BOOLEAN result
   - Update logging messages

## Testing Considerations

- Verify function returns `true` when creating new partition
- Verify function returns `false` when partition already exists
- Verify Java code correctly handles both cases
- Verify logging reflects actual operation result
- Test idempotent behavior (calling multiple times)

## Migration Impact

- **Breaking change**: Function signature changes from VOID to BOOLEAN
- **Compatibility**: Any other code calling this function will need updates
- **Database migration**: Function will be replaced (CREATE OR REPLACE FUNCTION)
- **No data migration needed**: Only function definition changes
