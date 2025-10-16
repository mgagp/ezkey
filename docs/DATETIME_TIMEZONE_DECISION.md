# Datetime and Timezone Management - Architectural Decision

**Date:** October 2025  
**Status:** Implemented  
**Decision:** Use UTC for all API datetime responses

---

## Context

Ezkey APIs return datetime fields in JSON responses (e.g., `createdAt`, `expiresAt`, `completedAt`, `timestamp`). A critical architectural decision was needed regarding timezone representation to ensure consistency, reliability, and maintainability across the platform.

### Initial Problem

Before standardization, Ezkey exhibited inconsistent datetime serialization:

```json
{
  "createdAt": "2025-10-16T07:55:55.9255124-04:00",  // Local timezone with offset
  "expiresAt": "2025-10-16T11:56:19.433745Z",        // UTC with Z suffix
  "completedAt": "2025-10-16T11:55:55.925512Z"       // UTC with Z suffix
}
```

This inconsistency created:
- **Ambiguity** for API consumers
- **Parsing complexity** requiring timezone-aware logic
- **Potential bugs** in datetime comparisons
- **Poor developer experience** with unpredictable formats

---

## Two Valid Approaches

### Approach 1: UTC (Coordinated Universal Time)

**Format:** `2025-10-16T15:52:52.764912Z`

**Advantages:**
- ✅ No ambiguity (no daylight saving time transitions)
- ✅ Simplifies synchronization between distributed systems
- ✅ ISO 8601 standard with Z suffix
- ✅ Ideal for multi-region applications
- ✅ Direct chronological sorting and comparison
- ✅ Industry standard for public/global APIs

**Used by:** GitHub API, Stripe API, Twitter API, AWS API, Google APIs

**Best for:**
- Open source projects with global reach
- Multi-tenant platforms across regions
- Mobile applications (devices travel)
- Distributed systems and microservices

### Approach 2: Local Timezone with Offset

**Format:** `2025-10-16T07:55:55.925512-04:00`

**Advantages:**
- ✅ More intuitive for users in same region
- ✅ Preserves local business context
- ✅ Useful for direct display without conversion
- ✅ ISO 8601 compliant with explicit offset

**Used by:** Many internal enterprise APIs, regional banking systems

**Best for:**
- Internal APIs within single organization/region
- Applications where local context matters
- Systems with predominantly regional users
- Business logic tied to local time (banking hours, etc.)

---

## Decision for Ezkey: UTC

### Rationale

Ezkey adopted **UTC with Z suffix** for all API datetime responses based on the following analysis:

#### 1. **Open Source & Global Reach**
Ezkey is an open-source MFA solution designed for worldwide adoption. Users and developers can be in any timezone, making a universal reference essential.

#### 2. **Multi-Tenant Architecture**
Ezkey supports multiple tenants (organizations) that may operate in different geographical regions. UTC provides timezone independence.

#### 3. **Mobile Application**
The Ezkey mobile app is used by end-users whose devices may travel across timezones. UTC ensures consistent authentication timestamps regardless of device location.

#### 4. **Distributed Systems**
Ezkey separates Auth API and Admin API, potentially deployed in different regions. UTC simplifies synchronization and log correlation.

#### 5. **Developer Experience**
UTC is predictable and well-documented. Developers can rely on consistent formatting across all endpoints without timezone-specific logic.

#### 6. **Industry Alignment**
As an authentication platform comparable to Auth0, Okta, and similar services, UTC aligns with industry standards.

#### 7. **Security & Audit**
Security events, authentication attempts, and audit logs benefit from unambiguous timestamps that work globally.

---

## Implementation

### Configuration

**Location:** `ezkey-auth-api/config/application.properties` and `ezkey-admin-api/config/application.properties`

```properties
# Jackson Datetime Configuration
# Serialize all OffsetDateTime to UTC with Z suffix for consistent API responses
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.time-zone=UTC
spring.jackson.date-format=yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'
```

### Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Database Layer (PostgreSQL TIMESTAMPTZ)                      │
│    Stores: 2025-10-16 07:55:55.925512-04:00                    │
│    (Preserves original timezone internally)                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. Java Layer (OffsetDateTime)                                  │
│    OffsetDateTime.now() → 2025-10-16T07:55:55.925512-04:00     │
│    (Server timezone preserved in object)                        │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. Jackson Serialization (UTC Conversion)                       │
│    spring.jackson.time-zone=UTC                                 │
│    Converts: -04:00 → Z (UTC)                                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. JSON API Response (UTC with Z)                              │
│    "createdAt": "2025-10-16T11:55:55.925512Z"                  │
│    (Consistent UTC format for all clients)                      │
└─────────────────────────────────────────────────────────────────┘
```

### Result: Consistent Format

**All datetime fields now serialize uniformly:**

```json
{
  "createdAt": "2025-10-16T11:55:55.925512Z",
  "expiresAt": "2025-10-16T15:56:19.433745Z",
  "completedAt": "2025-10-16T11:55:55.925512Z",
  "timestamp": "2025-10-16T16:02:24.123456Z"
}
```

---

## Client Responsibility

Clients consuming Ezkey APIs are responsible for:

1. **Parsing UTC timestamps** from JSON responses
2. **Converting to local timezone** for display (if needed)
3. **Using ISO 8601 libraries** in their programming language

### Examples

**JavaScript:**
```javascript
const createdAt = new Date("2025-10-16T11:55:55.925512Z");
console.log(createdAt.toLocaleString()); // Automatic local display
```

**Java:**
```java
OffsetDateTime createdAt = OffsetDateTime.parse("2025-10-16T11:55:55.925512Z");
ZonedDateTime local = createdAt.atZoneSameInstant(ZoneId.systemDefault());
```

**Python:**
```python
from datetime import datetime
created_at = datetime.fromisoformat("2025-10-16T11:55:55.925512Z")
local_time = created_at.astimezone()  # Convert to local timezone
```

---

## Benefits Achieved

### 1. **Consistency**
All API responses use identical datetime format - no surprises.

### 2. **Timezone Independence**
Works globally without regional configuration or assumptions.

### 3. **Simplicity**
One format to document, one format to parse.

### 4. **Reliability**
No daylight saving time edge cases or transition bugs.

### 5. **Industry Standard**
Aligns with REST API best practices and ISO 8601.

### 6. **Sortability**
String comparison works correctly for chronological ordering:
```
"2025-10-16T10:00:00Z" < "2025-10-16T11:00:00Z" ✓
```

### 7. **Global Collaboration**
Teams in different timezones see consistent data in logs and APIs.

---

## Trade-offs Acknowledged

### What We Gave Up

1. **Immediate Local Context**
   - Timestamps are not in server's local time
   - Requires conversion for timezone-specific display

2. **Intuitive Reading**
   - `11:55:55Z` less intuitive than `07:55:55-04:00` for Montreal users
   - Mitigated by client-side conversion for user interfaces

3. **Regional Optimization**
   - Not optimized for single-region deployments
   - Acceptable trade-off for global flexibility

### What We Gained

The benefits of consistency, global reach, and industry alignment far outweigh the minor inconvenience of UTC → local conversion in client applications.

---

## When to Reconsider

This decision should be revisited if:

1. **Ezkey pivots to regional-only deployment** (unlikely for open source)
2. **Business requirements demand local timezone** in API responses
3. **All users are in single timezone** and local context is critical
4. **Industry standards shift away from UTC** (highly unlikely)

---

## Alternative: If You Need Local Timezone

For organizations that **must** use local timezone with offset, modify configuration:

```properties
# Use server's local timezone (e.g., America/Montreal)
spring.jackson.time-zone=America/Montreal
spring.jackson.date-format=yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX
```

Result: `"createdAt": "2025-10-16T07:55:55.925512-04:00"`

**Warning:** This creates timezone dependency and complicates multi-region deployments.

---

## References

- **ISO 8601:** International datetime standard
- **RFC 3339:** Internet datetime format specification
- **REST API Best Practices:** UTC recommended for global APIs
- **Industry Examples:** GitHub, Stripe, AWS, Google APIs use UTC

## Related Documentation

- [ENDPOINT.md](ENDPOINT.md) - API datetime format specification
- [ARCHITECTURE.md](ARCHITECTURE.md) - Overall system architecture
- [TIMEZONE_UTC_IMPLEMENTATION_SUMMARY.md](../TIMEZONE_UTC_IMPLEMENTATION_SUMMARY.md) - Implementation details

---

## Conclusion

**Ezkey uses UTC for all API datetime responses** to provide consistency, timezone independence, and alignment with industry standards. This decision supports Ezkey's mission as a global, open-source MFA platform while maintaining excellent developer experience.

The choice between UTC and local timezone is **context-dependent**, but for Ezkey's use case — open source, multi-region, mobile-first authentication — UTC is the correct architectural decision.

