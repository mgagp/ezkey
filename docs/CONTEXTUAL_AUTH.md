# Contextual Authentication

## Overview

Ezkey's **Contextual Authentication** feature extends the standard binary approve/deny MFA model
with optional plain-text context fields. Integrating applications can attach business context to
an authentication attempt, which is forwarded to the mobile device and displayed to the approver
before they make a decision.

**Before:** "Approve login to ACME App?" — the approver has no idea what they are authorizing.

**After:** "Authorize payment of $5,000 to Suppliers Ltd." — the approver sees exactly what they
are authorizing, making the decision informed and meaningful.

---

## Context Fields (Phase 1)

| Field            | Type   | Max Size    | Description                                                  |
|------------------|--------|-------------|--------------------------------------------------------------|
| `contextTitle`   | String | 200 chars   | Short heading displayed as the card header on mobile         |
| `contextMessage` | String | 2 000 chars | Descriptive body explaining what the approver is authorizing |

Both fields are **optional** and **nullable**. Existing integrations that do not send these fields
continue to work without any changes — the mobile device falls back to the standard MFA approval
screen.

> **Phase 2 — Richer Business Approvals (future):** A dedicated business-approval workflow will
> extend this foundation with structured metadata (key-value detail pairs), severity levels, and
> longer-lived approval lifecycles suited to asynchronous business processes. Phase 1 is
> intentionally minimal: keep the surface small, prove the concept, ship value now.

---

## Use Cases

| Domain     | Use Case                     | Example context                                               |
|------------|------------------------------|---------------------------------------------------------------|
| Financial  | Payment authorization        | Title: "Payment Authorization" · "Authorize $5,000 to Suppliers Ltd." |
| Financial  | Batch payment release        | Title: "Batch Payment Authorization" · "Release $18,400 across 6 suppliers" |
| Financial  | Wire transfer confirmation   | "Authorize international wire of $25,000 to Zurich"          |
| DevOps     | Production deployment        | Title: "Deploy Confirmation" · "Deploy v2.3.1 to production" |
| Healthcare | Prescription approval        | "Prescribe Amoxicillin 500mg — Patient J.D."                  |
| Legal      | Contract signing             | "Sign NDA with Vendor Corp — expires Jan 2027"               |
| IT Admin   | Privileged action            | "Delete user account john@acme.com"                          |
| E-commerce | High-value order             | "Confirm order #8821 — $12,500 — Ship to..."                 |
| HR         | Access approval              | "Grant Sarah admin access to payroll system"                 |

---

## API Usage

### Create an authentication attempt with context

```http
POST /api/v1/auth-attempts
Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)
Content-Type: application/json

{
  "enrollmentId": 456,
  "challengeRequested": false,
  "contextTitle": "Payment Authorization",
  "contextMessage": "Authorize payment of $5,000 to Suppliers Ltd. for invoice INV-2025-042."
}
```

**Response (201 Created):**
```json
{
  "authAttemptId": 123,
  "authAttemptChallenge": null,
  "timeoutSeconds": 120,
  "expiresAt": "2026-03-01T15:34:00.123456Z",
  "contextTitle": "Payment Authorization",
  "contextMessage": "Authorize payment of $5,000 to Suppliers Ltd. for invoice INV-2025-042."
}
```

The context fields are **echoed back** in the response for confirmation.

### By user identifier (Integration API / API key)

```http
POST /api/v1/auth-attempts
Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)
Content-Type: application/json

{
  "userIdentifier": "jane",
  "challengeRequested": false,
  "contextTitle": "Batch Payment Authorization",
  "contextMessage": "Release batch payment run B-2025-18 — $18,400 across 6 pending suppliers."
}
```

### Mobile device receives context via pending endpoint

When the mobile device polls `POST /api/v1/auth-attempts/pending`, the response includes the
context fields:

```json
{
  "authAttemptId": 123,
  "authAttemptProofToken": "abc123-def456-ghi789",
  "authAttemptProofTokenSignedByIntegration": "AAAA...=",
  "authAttemptChallengeRequired": false,
  "contextTitle": "Payment Authorization",
  "contextMessage": "Authorize payment of $5,000 to Suppliers Ltd. for invoice INV-2025-042."
}
```

---

## Mobile UI Rendering

When context fields are present, the Ezkey mobile app renders an enriched approval card:

- **`contextTitle`**: Replaces the generic integration name as the card header
- **`contextMessage`**: Displayed in the card body, giving the approver full visibility into what
  they are authorizing

When no context is provided, the card falls back to the standard minimal display (integration
name, tenant name, auth attempt ID, response window).

---

## Validation Rules

| Field            | Constraint                      |
|------------------|---------------------------------|
| `contextTitle`   | Optional. Max 200 characters.   |
| `contextMessage` | Optional. Max 2 000 characters. |

---

## Regulatory Alignment

**PSD2 / Strong Customer Authentication (SCA):** EU regulation requires "dynamic linking" —
authentication must be cryptographically bound to transaction details (amount, payee). With
`contextMessage` containing transaction details and the proof token cryptographically linking
the approval to a specific attempt, Ezkey becomes PSD2-compliant-ready for payment authorization
workflows.

**SOC 2 Audit Trail:** Context fields are persisted with the authentication attempt in the
database, enriching the audit trail with business context. Reviewers can see not just that an
authentication occurred, but what was approved (CC6.1, CC7.2).

---

## Security Notes

- Context fields are **display-only** and have no effect on the cryptographic proof/signature model
- `contextTitle` and `contextMessage` are stored as plain VARCHAR/TEXT columns alongside the auth
  attempt in the database
- The existing one-time token, anti-replay, and cryptographic validation mechanisms are unchanged
- Integrations are responsible for ensuring the content is appropriate and does not include
  sensitive data beyond what is needed for the approval decision

---

## Backward Compatibility

Both context fields are nullable with no changes to existing fields or flows. Integrations that
do not send context fields continue to work exactly as before. The mobile app gracefully falls
back to the standard minimal display when context is absent.
