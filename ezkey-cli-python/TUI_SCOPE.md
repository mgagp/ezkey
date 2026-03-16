# Ezkey TUI — Scope and positioning

The Ezkey Admin **TUI** (`ezkey --tui`) is a **read-only investigation and audit fallback**. Use it when the web Admin UI is not available (e.g. SSH session, no browser) to consult audit logs and core entities.

**Primary admin interface:** Use the **Admin UI** (web) for all day-to-day operations. The TUI is not a substitute; it is a narrow, read-only tool for occasional investigation.

## In-scope (read-only)

The TUI exposes the following in **read-only** form. We keep these in sync with the Admin API list/detail contracts when they change.

| Area           | Capability                          |
|----------------|-------------------------------------|
| **Audit logs** | List, filter, follow (tail)          |
| **Auth attempts** | List, detail, filter, follow    |
| **Enrollments**   | List, detail, filter             |
| **Integrations**  | List, detail, filter             |
| **Tenants**       | List, detail, filter             |
| **Home / Dashboard** | Status overview, refresh     |

No create, update, delete, revoke, activate, deactivate, or other write operations are available in the TUI. Use the Admin UI or the CLI commands for those.

## Out of scope (not in TUI)

- Admin provisioning (create/deactivate admins, onboarding)
- API key management (create, revoke)
- Encryption keys and reencryption batches
- Crypto API tools (proof token, sign, validate, encrypt, decrypt) — use `ezkey crypto` CLI commands instead

## Maintenance commitment

We keep TUI **read-only** screens in sync with Admin API list/detail contracts for:

- `audit-logs`
- `auth-attempts`
- `enrollments`
- `integrations`
- `tenants`

We do **not** promise to add new entity types or new write flows to the TUI. If the Admin API adds or changes list/detail DTOs or pagination for the above, we will update the TUI when practical.

## Quick start

```bash
ezkey --tui
```

First run: setup wizard (Admin URL, username, passwordless auth). Subsequent runs: session loads automatically. Then use the keyboard shortcuts to open Audit Logs, Auth Attempts, Enrollments, Integrations, or Tenants (all read-only).
