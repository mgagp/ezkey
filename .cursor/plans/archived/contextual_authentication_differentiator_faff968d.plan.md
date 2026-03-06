---
name: Contextual Authentication Differentiator
overview: Extend EZKey's authentication model from binary MFA (approve/deny) to "Contextual Authentication" by adding optional structured context fields to auth attempts. This transforms EZKey from a pure MFA tool into a business workflow approval platform with minimal implementation effort, creating a significant differentiator.
todos:
  - id: flyway-migration
    content: Create Flyway migration to add context_title, context_message, context_details, context_level columns to ezkey_auth_attempt (handle partitioned table)
    status: pending
  - id: entity-update
    content: Add contextTitle, contextMessage, contextDetails, contextLevel fields to AuthAttempt entity with proper JPA annotations
    status: pending
  - id: core-dtos
    content: Update AuthAttemptDto, AuthAttemptCreateResponseDto, AuthAttemptPendingResponse domain objects with context fields
    status: pending
  - id: admin-create-dto
    content: Update AuthAttemptCreateRequestDto in admin-api to accept optional context fields with validation (@Size, enum for level)
    status: pending
  - id: auth-pending-dto
    content: Update AuthAttemptPendingResponseDto in auth-api to include context fields in mobile response
    status: pending
  - id: service-layer
    content: Update AuthAttemptService.create() and AuthAttemptPendingService to pass context through the flow
    status: pending
  - id: mappers
    content: Update MapStruct mappers to map context fields between entity, domain, and DTOs
    status: pending
  - id: mobile-types
    content: Update PendingAuthResponse type in ezkey_mobile/app/services/api/types.ts
    status: pending
  - id: mobile-ui
    content: Enhance PendingAuthScreen.tsx to display contextual information (title, message, details, level styling)
    status: pending
  - id: tests
    content: Add/update unit and integration tests for context fields in create, pending, and wait flows
    status: pending
  - id: documentation
    content: Update ENDPOINT.md, PRD.md, and create docs/CONTEXTUAL_AUTH.md with use cases and examples
    status: pending
isProject: false
---

# Contextual Authentication: From MFA to Business Approval Platform

## Strategic Analysis

### The Opportunity

EZKey currently handles authentication as a binary approve/deny decision with an optional numeric challenge. The mobile app shows only: integration name, tenant name, auth attempt ID, timestamp. **There is zero business context visible to the approver.**

Adding optional contextual information to auth attempts transforms EZKey's value proposition:

- **Before**: "Approve login to ACME App?" (MFA)
- **After**: "Approve payment batch #1497 to Acme Corp for $1,400?" (Business Approval)

### Competitive Validation

This is not speculative -- established players already do this:

- **Duo Security** has a `pushinfo` parameter (key/value pairs, max 20KB) displayed in push notifications. This is their premium feature.
- **PSD2/SCA regulation** (EU) legally requires "dynamic linking" -- authentication must be cryptographically bound to transaction details (amount, payee). EZKey with contextual auth becomes PSD2-compliant-ready.
- **FIDO2/WebAuthn** has `txAuthSimple`/`txAuthGeneric` extensions for transaction confirmation, but they are poorly adopted and complex. EZKey can deliver this via simple REST.
- **IETF Transaction Tokens** (draft 2025) standardize auth context preservation across services.

**EZKey's advantage**: Deliver transaction context via simple REST API + mobile display, with zero protocol complexity. Developer-first, as always.

### Broader Use Cases (Beyond Payment Batches)

With this single feature, EZKey becomes applicable to:


| Domain     | Use Case                   | Context Example                                             |
| ---------- | -------------------------- | ----------------------------------------------------------- |
| Financial  | Payment batch approval     | "Batch #1497 - Acme Corp - $1,400"                          |
| Financial  | Wire transfer confirmation | "Wire $50,000 to account ending 4829"                       |
| DevOps     | Production deployment      | "Deploy v2.3.1 to production (3 services)"                  |
| Healthcare | Prescription approval      | "Prescribe Amoxicillin 500mg - Patient J.D."                |
| Legal      | Contract signing           | "Sign NDA with Vendor Corp - expires Jan 2027"              |
| IT Admin   | Privileged action          | "Delete user account [john@acme.com](mailto:john@acme.com)" |
| E-commerce | High-value order           | "Confirm order #8821 - $12,500 - Ship to..."                |
| HR         | Access approval            | "Grant Sarah admin access to payroll system"                |


All of these are served by the **same simple feature**: optional structured context on auth attempts.

### Why This Is a True Differentiator

1. **Regulatory alignment** -- PSD2/SCA dynamic linking is a legal requirement in the EU financial sector. EZKey becomes compliance-ready.
2. **Market positioning** -- Moves from "yet another MFA" to "business approval platform". Broader TAM.
3. **Developer stickiness** -- Once devs use contextual auth for workflows, switching cost increases dramatically.
4. **Open-source uniqueness** -- No open-source project offers simple REST-based transaction signing. PrivacyIDEA doesn't have it.
5. **Audit trail enrichment** -- Context stored with auth attempts creates a rich, searchable approval audit log (SOC 2 CC6.1, CC7.2).

### Risks Mitigated

- **Not complex**: 80/20 rule -- adding 3-4 optional fields is trivial compared to implementing a full signing protocol.
- **Backward compatible**: All fields optional. Existing integrations work unchanged.
- **No crypto changes**: Context is for display only. The existing cryptographic proof/signature model remains untouched.

---

## Technical Design

### Data Model: `AuthAttemptContext`

Add a new concept of optional structured context to auth attempts. The context is provided at creation time by the integrating application and displayed to the user on the mobile device.

**New fields on AuthAttempt entity** (all nullable, backward-compatible):

- `contextTitle` (VARCHAR 200) -- Short title: "Payment Approval", "Deploy Confirmation"
- `contextMessage` (VARCHAR 2000) -- Descriptive message: "Authorize payment batch #1497 to Acme Corp for $1,400"
- `contextDetails` (TEXT, JSON) -- Structured key-value pairs for rich display: `{"Amount": "$1,400", "Vendor": "Acme Corp", "Batch": "#1497"}`
- `contextLevel` (VARCHAR 20) -- Severity/importance: `INFO`, `WARNING`, `CRITICAL` (default: `INFO`)

**Why not a single JSON blob?** Separating `contextTitle` and `contextMessage` from `contextDetails` allows:

- Simple use cases to just pass a title+message (80% case)
- Rich use cases to add structured details (20% case)
- Mobile UI to render consistently without parsing

### Files to Modify

**Backend (ezkey-core):**

- [AuthAttempt.java](ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java) -- Add 4 new nullable fields
- [AuthAttemptDto.java](ezkey-core/src/main/java/org/ezkey/authattempt/dto/AuthAttemptDto.java) -- Add context fields
- [AuthAttemptCreateResponseDto.java](ezkey-core/src/main/java/org/ezkey/authattempt/dto/AuthAttemptCreateResponseDto.java) -- Echo context back
- [AuthAttemptPendingResponse](ezkey-core/src/main/java/org/ezkey/authattempt/domain/AuthAttemptPendingResponse.java) -- Add context fields for mobile
- [AuthAttemptService.java](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java) -- Pass context through create flow
- New Flyway migration `V{next}__add_auth_attempt_context.sql`

**Backend (ezkey-admin-api):**

- [AuthAttemptCreateRequestDto.java](ezkey-admin-api/src/main/java/org/ezkey/authattempt/dto/AuthAttemptCreateRequestDto.java) -- Add optional context fields to creation request

**Backend (ezkey-auth-api):**

- [AuthAttemptPendingResponseDto.java](ezkey-auth-api/src/main/java/org/ezkey/authattempt/dto/AuthAttemptPendingResponseDto.java) -- Add context fields to pending response

**Mobile (ezkey_mobile):**

- [types.ts](ezkey_mobile/app/services/api/types.ts) -- Add context fields to `PendingAuthResponse`
- [PendingAuthScreen.tsx](ezkey_mobile/app/screens/PendingAuth/PendingAuthScreen.tsx) -- Display context card

**No changes needed to:**

- Crypto flow, proof tokens, signatures
- Wait API response structure (it wraps AuthAttemptDto which gains fields automatically)
- Respond API (context is display-only, not part of response)
- Enrollment flow
- Security model

### API Changes

**Create Auth Attempt** -- new optional fields in request body:

```json
POST /api/v1/auth-attempts
{
  "enrollmentId": 456,
  "challengeRequested": false,
  "contextTitle": "Payment Approval",
  "contextMessage": "Authorize payment batch #1497 to Acme Corp",
  "contextDetails": {
    "Amount": "$1,400.00",
    "Vendor": "Acme Corp",
    "Batch Number": "#1497",
    "Due Date": "2026-03-15"
  },
  "contextLevel": "WARNING"
}
```

**Pending Response** -- context forwarded to mobile:

```json
{
  "authAttemptId": 123,
  "authAttemptProofToken": "...",
  "authAttemptProofTokenSignedByIntegration": "...",
  "authAttemptChallengeRequired": true,
  "contextTitle": "Payment Approval",
  "contextMessage": "Authorize payment batch #1497 to Acme Corp",
  "contextDetails": {
    "Amount": "$1,400.00",
    "Vendor": "Acme Corp"
  },
  "contextLevel": "WARNING"
}
```

### Migration Script

```sql
ALTER TABLE ezkey_auth_attempt
  ADD COLUMN context_title VARCHAR(200),
  ADD COLUMN context_message VARCHAR(2000),
  ADD COLUMN context_details TEXT,
  ADD COLUMN context_level VARCHAR(20) DEFAULT 'INFO';

COMMENT ON COLUMN ezkey_auth_attempt.context_title IS 'Optional short title describing the action requiring approval';
COMMENT ON COLUMN ezkey_auth_attempt.context_message IS 'Optional detailed message explaining the approval request';
COMMENT ON COLUMN ezkey_auth_attempt.context_details IS 'Optional JSON key-value pairs for structured context display';
COMMENT ON COLUMN ezkey_auth_attempt.context_level IS 'Severity level: INFO, WARNING, CRITICAL';
```

### Mobile UI Enhancement

The `PendingAuthScreen` currently shows a minimal card. With context, the UI would render:

- **Context title** as card header (replacing generic "PENDING" when present)
- **Context message** as card body text
- **Context details** as a key-value list (label: value rows)
- **Context level** as visual styling (INFO=blue, WARNING=amber, CRITICAL=red border/accent)
- Graceful fallback to current UI when no context provided

### Documentation Updates

- Update [ENDPOINT.md](docs/ENDPOINT.md) with new request/response fields
- Update [PRD.md](PRD.md) to mention contextual authentication as a differentiator
- Add a new `docs/CONTEXTUAL_AUTH.md` explaining the feature with examples (payment approval, deployment approval, etc.)
- Update competitive analysis

---

## Effort Estimate


| Component                     | Estimated Effort |
| ----------------------------- | ---------------- |
| Flyway migration              | ~15 min          |
| Entity + DTOs (core)          | ~1 hour          |
| Admin API request DTO         | ~30 min          |
| Auth API response DTO         | ~30 min          |
| Service layer pass-through    | ~30 min          |
| Mappers                       | ~30 min          |
| Mobile types update           | ~15 min          |
| Mobile UI (PendingAuthScreen) | ~2 hours         |
| Tests                         | ~2 hours         |
| Documentation                 | ~1 hour          |
| **Total**                     | **~8-10 hours**  |


This is a high-ROI feature: ~1 day of work for a fundamental repositioning of EZKey's value proposition.
