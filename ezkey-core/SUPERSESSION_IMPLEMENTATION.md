# Authentication Attempt Supersession Implementation

## Overview

This document summarizes the implementation of the authentication attempt supersession feature, which ensures that only the most recent authentication attempt for a person is valid.

## Business Rule

**One Active Attempt Per Person**: When a new authentication attempt is created for an enrollment, all previous attempts for the same enrollment are considered **EXPIRED** regardless of their actual timeout.

## Implementation Details

### 1. Database Changes

**File**: `ezkey-core/src/main/resources/db/migration/V1__initial_schema.sql`

Added index for performance optimization:
```sql
-- Index to optimize queries for finding newer authentication attempts by enrollment
CREATE INDEX idx_auth_attempt_enrollment_created ON ezkey_auth_attempt(enrollment_id, created_at DESC);
```

### 2. Repository Layer

**File**: `ezkey-core/src/main/java/org/ezkey/authattempt/domain/repository/AuthAttemptRepository.java`

Added new method:
```java
/**
 * Finds a newer authentication attempt for the same enrollment.
 */
@Query("SELECT a FROM AuthAttempt a WHERE a.enrollmentId = :enrollmentId AND a.createdAt > :createdAt ORDER BY a.createdAt DESC")
Optional<AuthAttempt> findNewerAttemptByEnrollmentId(@Param("enrollmentId") Integer enrollmentId, @Param("createdAt") java.time.LocalDateTime createdAt);
```

### 3. Service Layer

**File**: `ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java`

#### Modified Methods:

**RESPOND Method**:
- Added check for newer attempts before processing
- Returns `EXPIRED` status if superseded
- Logs supersession events for monitoring

**WAIT Method**:
- Added check for newer attempts at start of wait operation
- Returns `EXPIRED` status immediately if superseded
- Prevents unnecessary polling for expired attempts

### 4. Testing

**Unit Tests**: `ezkey-core/src/test/java/org/ezkey/authattempt/service/AuthAttemptServiceSupersededTest.java`
- Tests supersession logic in isolation
- Validates both RESPOND and WAIT behaviors
- Tests edge cases and normal flows

**Integration Tests**: `ezkey-core/src/test/java/org/ezkey/authattempt/service/AuthAttemptServiceIntegrationTest.java`
- Tests complete end-to-end flow
- Validates database interactions
- Tests real-world scenarios

### 5. Documentation

**File**: `ezkey-docs/ENDPOINT.md`
- Added supersession rule explanation
- Documented affected APIs
- Provided usage examples and scenarios

## API Behavior Changes

### RESPOND API
```http
POST /api/v1/auth-attempts/respond/{authAttemptId}
```

**New Response**:
```json
{
  "result": "EXPIRED",
  "message": "Authentication attempt superseded by newer request"
}
```

### WAIT API
```http
GET /api/v1/auth-attempts/{authAttemptId}/wait
```

**New Response**:
```json
{
  "status": "EXPIRED",
  "completed": false,
  "timeoutReached": false,
  "waitDuration": 0
}
```

## Performance Considerations

1. **Index Optimization**: Added database index for efficient queries
2. **Early Exit**: WAIT API returns immediately if superseded
3. **Minimal Overhead**: Single query per RESPOND/WAIT call
4. **Logging**: Supersession events logged for monitoring

## Security Benefits

1. **Prevents Confusion**: Only one active attempt per person
2. **Reduces Attack Surface**: Eliminates stale authentication attempts
3. **Clear State**: Always clear which attempt is current
4. **Audit Trail**: Supersession events are logged

## Migration Notes

- **No Data Migration Required**: Existing data remains unchanged
- **Backward Compatible**: All existing APIs continue to work
- **Index Addition**: New index improves performance for all queries
- **Pre-release**: Changes made in V1 migration (no V2 needed)

## Future Considerations

1. **Audit Table**: Future implementation will track supersession events
2. **Analytics**: Distinguish between timeout and supersession expirations
3. **Monitoring**: Track supersession frequency and patterns
4. **Mobile App**: Update mobile app to handle superseded attempts gracefully

## Testing Checklist

- [x] Unit tests for supersession logic
- [x] Integration tests for complete flow
- [x] Database index creation
- [x] API documentation updates
- [x] Performance validation
- [x] Security review
- [x] Backward compatibility verification

## Deployment Notes

1. **Database**: Run Flyway migration to add index
2. **Application**: Deploy updated service code
3. **Monitoring**: Watch for supersession events in logs
4. **Documentation**: Update API documentation for consumers
