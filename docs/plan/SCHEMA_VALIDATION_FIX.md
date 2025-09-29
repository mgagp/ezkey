# Schema Validation Fix - IP Address Column Type

## Problem Description

The admin-api application fails to start with the following error:

```
Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: wrong column type encountered in column [ip_address] in table [ezkey_admin_tokens]; found [inet (Types#OTHER)], but expecting [varchar(255) (Types#VARCHAR)]
```

## Root Cause Analysis

### The Issue
- **Database Schema**: Column `ip_address` is defined as `INET` type in PostgreSQL
- **JPA Entity**: Field `ipAddress` is mapped as `String` without explicit column type
- **Hibernate Expectation**: Hibernate expects `VARCHAR(255)` for `String` fields by default
- **Type Mismatch**: PostgreSQL `INET` type vs Hibernate `VARCHAR` expectation

### Why This Happened
1. **Migration V2**: Created `ip_address` column as `INET` type (correct for PostgreSQL)
2. **Entity Mapping**: Used `String` type without specifying column type
3. **Hibernate Validation**: Hibernate validates schema and finds type mismatch
4. **PostgreSQL INET**: More appropriate for IP addresses than VARCHAR

## Solution

### Option 1: Update Entity Mapping (Recommended)
```java
@Column(name = "ip_address", columnDefinition = "INET")
private String ipAddress;
```

### Option 2: Update Database Schema
```sql
ALTER TABLE ezkey_admin_tokens ALTER COLUMN ip_address TYPE VARCHAR(255);
```

## Recommendation

**Use Option 1** because:
- PostgreSQL `INET` type is more appropriate for IP addresses
- Provides better validation and storage efficiency
- Maintains data integrity for IP address format
- Follows PostgreSQL best practices

## Implementation

Update the `AdminToken` entity to specify the column type explicitly:

```java
@Column(name = "ip_address", columnDefinition = "INET")
private String ipAddress;
```

This tells Hibernate to expect an `INET` type column, resolving the validation error.

## Testing

After implementing the fix:
1. Restart admin-api application
2. Verify no schema validation errors
3. Test IP address storage and retrieval
4. Confirm PostgreSQL INET type functionality

---

**Status**: Ready for Implementation  
**Priority**: High (Blocks Application Startup)  
**Impact**: Admin-API cannot start without this fix
