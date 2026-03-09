---
name: Admin UI Tooltip Strategy
overview: Comprehensive UX analysis and implementation plan for introducing a balanced, pragmatic tooltip strategy across the EZKey Admin UI, targeting technical operators without over-engineering or creating maintenance burden.
todos:
  - id: tooltip-component
    content: Create `Tooltip` hover component (tooltip.tsx) with neo-brutalist styling, hover/focus triggers, 300ms delay, keyboard accessible
    status: completed
  - id: help-text-constants
    content: Create `help-text.ts` centralized constants file with all tooltip and ContextHelp text; refactor existing HELP object from audit-logs.tsx
    status: completed
  - id: status-badges
    content: Add tooltips to EnrollmentStatusBadge and AuthAttemptStatusBadge using help-text constants
    status: completed
  - id: audit-logs-tooltips
    content: Add tooltips to HMAC column header and checkpoint type badges in audit-logs.tsx
    status: completed
  - id: encryption-keys-tooltips
    content: Add ContextHelp section header + tooltips to key status badges and batch statuses in encryption-keys.tsx
    status: completed
  - id: api-keys-tooltips
    content: Add tooltips to integration key, IP whitelist indicator, and status badges in api-keys.tsx
    status: completed
  - id: enrollment-detail-tooltips
    content: Add tooltips to Proof Token and Binding Challenge Code labels in enrollment-detail.tsx
    status: completed
  - id: dashboard-tooltips
    content: Add tooltips to audit chain alert terms (undeclared gaps, anchor checkpoint) in dashboard.tsx
    status: completed
  - id: replace-native-titles
    content: Replace native `title` attributes on icon-only buttons with Tooltip component across all pages for consistency
    status: completed
isProject: false
---

# Admin UI — Tooltip Strategy and Contextual Help Plan

## Current State

The Admin UI has **14 main screens** with a single help mechanism: the `ContextHelp` click-to-open popover component ([context-help.tsx](ezkey-admin-ui/src/components/ui/context-help.tsx)), used in **4 places** on the Audit Logs page only. The rest of the UI relies on:

- Inline hints under form fields (e.g. "Alphanumeric, hyphens, underscores. Unique per tenant.")
- A few native `title` attributes on icon buttons (e.g. `title="View details"`, `title="Copy integration key"`)
- Static info banners (e.g. API Keys "Max 5 active keys per integration...")

There are **no hover tooltips** anywhere.

---

## Analysis: Should We Add Tooltips?

### The Audience

EZKey operators fall into two profiles:

- **Global Admins (IT/DevOps)**: manage the whole instance, tenants, encryption, audit chain integrity. Technically strong, but may not use all screens daily.
- **Tenant Admins (domain users)**: manage their tenant's integrations, enrollments, API keys. Know their domain but may not understand all EZKey-specific terms.

Both profiles are **technical but not omniscient** about EZKey's specific vocabulary. They won't need help understanding what a "table" or "button" is, but they will benefit from quick reminders about:

- What an enrollment status like "BOUND" actually means in the EZKey lifecycle
- What "HMAC" signifies in the audit log context
- What "Seal Archive" does vs "Declare Gap"
- What "Primary" means for an encryption key

### The Verdict: Yes, But With Restraint

Tooltips have their place, but **the current minimalist approach is mostly correct**. The risk is not "too few tooltips" but "tooltips on the wrong things." The strategy should be:

> **Explain domain-specific vocabulary; never explain standard UI patterns.**

A "Deactivate" button does not need a tooltip. A "BOUND" status badge does.

---

## Two-Tier Help Strategy

The UI needs **two distinct help mechanisms**, each serving a different purpose:

### Tier 1 — Hover Tooltip (NEW)

**Purpose**: Quick "what does this label/term mean?" on hover/focus.
**Trigger**: Hover (desktop) or long-press/focus (touch).
**Content**: One sentence, max ~15 words. No links, no rich formatting.
**Visual**: Small floating label with arrow, neo-brutalist styling (border-2, shadow-brutal-sm).

**Use for**:

- Status badges (enrollment, auth attempt, checkpoint type)
- Column headers with domain-specific meaning (HMAC, Challenge, etc.)
- Icon-only action buttons (already using native `title` — upgrade to proper tooltip)
- Abbreviated or technical labels (e.g. "M2M" in dashboard quick actions)

### Tier 2 — Click-to-Open Popover (EXISTING `ContextHelp`)

**Purpose**: "How does this feature/section work?" Provides 1-3 sentences of explanation.
**Trigger**: Click (already implemented).
**Content**: Short paragraph, optionally with bold terms or a "Learn more" link.

**Use for**:

- Section headers on complex screens (audit integrity, encryption, checkpoint timeline)
- Concepts that require multi-sentence explanation
- Workflow guidance ("do X first, then Y")

---

## Where to Apply — Screen-by-Screen Recommendations

### Priority: HIGH (significant UX improvement, domain-specific terms)

**1. Status Badges — All Screens** (Tier 1 tooltip)

Enrollment statuses, auth attempt statuses, and checkpoint types are domain-specific vocabulary that even experienced operators may need to recall. Add a hover tooltip to each badge:


| Badge                    | Tooltip text                                              |
| ------------------------ | --------------------------------------------------------- |
| **CREATED** (enrollment) | "Enrollment created, waiting for device to bind."         |
| **BOUND**                | "Device bound; pending verification."                     |
| **VERIFIED**             | "Device verified and active for authentication."          |
| **INVALID**              | "Verification failed; enrollment is permanently invalid." |
| **REVOKED**              | "Enrollment permanently revoked by an administrator."     |
| **EXPIRED**              | "Enrollment expired past its expiration date."            |
| **PENDING** (auth)       | "Waiting for mobile device to pick up the request."       |
| **READ** (auth)          | "Device received the request; waiting for user decision." |
| **ACCEPTED**             | "User approved the authentication request."               |
| **REJECTED**             | "User denied the authentication request."                 |
| **EXPIRED** (auth)       | "No response within the allowed time window."             |
| **INVALID** (auth)       | "Cryptographic validation failed."                        |
| **REGULAR** (checkpoint) | "Automatic checkpoint created by the scheduler."          |
| **ARCHIVE_SEAL**         | "Checkpoints sealed for archival by an operator."         |
| **GAP_DECLARATION**      | "Declared gap covering a period the system was offline."  |


This is the **single highest-value change** — it touches [enrollment-status-badge.tsx](ezkey-admin-ui/src/components/feature/enrollment-status-badge.tsx), [auth-attempt-status-badge.tsx](ezkey-admin-ui/src/components/feature/auth-attempt-status-badge.tsx), and the checkpoint type badges in [audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx).

**2. Audit Logs — Integrity Panel** (already Tier 2, enhance)

The existing `ContextHelp` instances are well placed. No changes needed for the 4 existing placements. However, add a **Tier 1 tooltip** to:

- The **HMAC column header** in the audit log table: "Hash-based message authentication code; ensures entry tamper-evidence."
- The **Chain / Entry integrity result** badges if verification has been run.

**3. Encryption Keys Page** (Tier 1 + Tier 2)

Add a **Tier 2 `ContextHelp`** next to the page section header:

- "AES-256 encryption keys protecting sensitive data at rest. The PRIMARY key encrypts new data. Old keys remain ENABLED until all their records are re-encrypted."

Add **Tier 1 tooltips** to:

- **Primary** badge: "Active key used for all new encryption operations."
- **Enabled** badge: "Key can decrypt existing data but is not used for new encryption."
- **Disabled** badge: "Key is retired; all data has been re-encrypted to a newer key."
- **Re-encrypt** button: "Migrate records encrypted with this key to the current primary key."
- Batch status badges (PENDING, IN_PROGRESS, COMPLETED, FAILED).

### Priority: MEDIUM (helpful but not critical)

**4. Dashboard — Audit Chain Alert** (Tier 1)

When the audit chain alert appears for Global Admins, terms like "undeclared gaps" and "anchor checkpoint" are opaque. Add:

- Tooltip on "undeclared gaps": "Periods with no checkpoints that have not been formally declared."
- Tooltip on "anchor checkpoint": "The last checkpoint before the gap; used as the link point."

**5. API Keys Page** (Tier 1)

- **Integration Key** column: "Public identifier for the API key pair; safe to log."
- **IP Whitelist** indicator: "Requests restricted to these IP addresses/CIDR ranges."
- **Expiring Soon** badge: "Key expires within 30 days; plan rotation."
- **Revoked** badge: "Key permanently disabled; cannot be reactivated."

**6. Enrollment Detail — Token Section** (Tier 1)

- **Proof Token** label: "Cryptographic token used by the mobile device to bind to this enrollment."
- **Binding Challenge Code** label: "One-time code the device must enter to complete enrollment."

### Priority: LOW (not needed now)

These screens use standard terminology and their audience already understands the context:

- **Login page**: guided flow, self-explanatory.
- **Tenants list/detail**: "Tenant", "System Tenant" are explained once in onboarding.
- **Integrations list/detail**: straightforward CRUD.
- **Admins list**: "Global Admin" / "Tenant Admin" are self-evident to the audience.
- **Auth Attempts list**: status badges (covered above) are the only domain terms.

---

## Implementation Approach

### Step 1 — Create `Tooltip` Component

Create a new [ezkey-admin-ui/src/components/ui/tooltip.tsx](ezkey-admin-ui/src/components/ui/tooltip.tsx) hover tooltip component:

- Pure CSS positioning (no Radix/Floating UI dependency — keeping the zero-dependency approach)
- Uses the existing neo-brutalist styling tokens (`border-2 border-fg`, `shadow-brutal`, `bg-surface`)
- Shows on hover after a ~300ms delay (prevents flickering on mouse pass-through)
- Hides on mouse leave
- Keyboard accessible: shows on focus, hides on blur
- Accepts `content: string` and `position?: 'top' | 'bottom'` (default top)
- Small arrow/caret pointing to the trigger element
- Max width ~220px, text wrapping

### Step 2 — Centralize Help Text

Create [ezkey-admin-ui/src/lib/help-text.ts](ezkey-admin-ui/src/lib/help-text.ts) — a single file containing all tooltip and ContextHelp text as typed constants:

```typescript
export const ENROLLMENT_STATUS_HELP = {
  CREATED: 'Enrollment created, waiting for device to bind.',
  BOUND: 'Device bound; pending verification.',
  VERIFIED: 'Device verified and active for authentication.',
  INVALID: 'Verification failed; enrollment is permanently invalid.',
  REVOKED: 'Enrollment permanently revoked by an administrator.',
  EXPIRED: 'Enrollment expired past its expiration date.',
} as const;

export const AUTH_ATTEMPT_STATUS_HELP = { ... } as const;
export const CHECKPOINT_TYPE_HELP = { ... } as const;
export const ENCRYPTION_KEY_HELP = { ... } as const;
// etc.
```

Move the existing `HELP` object from [audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx) (lines 69-107) into this file as well, so all help content lives in one place.

This **single file is the maintenance surface**. When a term changes, there is one place to update.

### Step 3 — Enhance Status Badge Components

Wrap the badge render in `EnrollmentStatusBadge` and `AuthAttemptStatusBadge` with the new `Tooltip`, pulling text from `help-text.ts`. This automatically adds tooltips everywhere those badges appear (list pages, detail pages, dialogs).

### Step 4 — Apply Tooltips to Targeted Locations

Add tooltips to the HIGH and MEDIUM priority locations listed above. This involves light edits to:

- [audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx) — HMAC column, checkpoint type badges
- [encryption-keys.tsx](ezkey-admin-ui/src/pages/encryption-keys.tsx) — key status badges, section header, batch statuses
- [api-keys.tsx](ezkey-admin-ui/src/pages/api-keys.tsx) — integration key, IP whitelist, status badges
- [enrollment-detail.tsx](ezkey-admin-ui/src/pages/enrollment-detail.tsx) — proof token, binding challenge
- [dashboard.tsx](ezkey-admin-ui/src/pages/dashboard.tsx) — audit chain alert terms

### Step 5 — Upgrade Native `title` Attributes

Replace existing native `title` attributes on icon-only buttons with the new `Tooltip` component for visual consistency. This affects buttons like "View details", "Copy integration key", "IP restricted" across several pages.

---

## What We Deliberately Do NOT Do

- **No tooltip on every label.** Standard terms (Name, Email, Created, ID) do not need explanation.
- **No tooltip on buttons with text labels.** "Create Integration", "Deactivate", "Refresh" are self-explanatory.
- **No tutorial/walkthrough system.** The audience does not need onboarding wizards.
- **No external documentation links** except in ContextHelp popovers where appropriate.
- **No i18n layer for tooltip text.** All content is English per project rule. A flat constants file is sufficient.

---

## Estimated Effort


| Task                                | Files changed           | Effort       |
| ----------------------------------- | ----------------------- | ------------ |
| Create `Tooltip` component          | 1 new file              | Small        |
| Create `help-text.ts` constants     | 1 new file + 1 refactor | Small        |
| Status badges (enrollment + auth)   | 2 files                 | Small        |
| Audit Logs (HMAC, checkpoint types) | 1 file                  | Small        |
| Encryption Keys (badges, header)    | 1 file                  | Small        |
| API Keys (badges, integration key)  | 1 file                  | Small        |
| Enrollment Detail (tokens)          | 1 file                  | Small        |
| Dashboard (audit alert)             | 1 file                  | Small        |
| Replace native `title` attrs        | ~5 files                | Small        |
| **Total**                           | **~12 files**           | **Moderate** |


Each step is independently deployable. No backend changes required.

---

## Design Principle Summary

> **"Explain EZKey vocabulary, not standard UI patterns. One centralized file for all text. Two help tiers: hover for terms, click for concepts."**

