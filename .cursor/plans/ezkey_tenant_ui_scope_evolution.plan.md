# EZKey Tenant UI — Scope & Strategic Positioning

## Original Scope: Tenant Admin UI (Docker Standalone)

The initial goal of `ezkey-tenant-ui` was to provide a **web-based SPA for Tenant Administrators**,
positioned as a more ergonomic, business-oriented alternative to the CLI TUI, which was to remain
the primary interface for Global Admins (IT/DevOps audience).

### Initial positioning rationale

| Interface | Audience | Rationale |
|---|---|---|
| `ezkey-cli` (TUI mode) | Global Admin | IT/ops-first, terminal-native, automation-friendly |
| `ezkey-tenant-ui` | Tenant Admin | Business users, web UI, process-oriented UX |

### Initial functional scope (Phases 1–3)

- **Phase 1** — Layout, visual identity (neo-brutalism), login screen (passwordless), mock dashboard
- **Phase 2** — Live dashboard widgets, Integrations CRUD, Enrollments CRUD
- **Phase 3** — Admins management (create + onboarding), Auth Attempts (read-only), Audit Logs (read-only), API Keys (create + revoke)

### Docker standalone assumption

The original plan assumed the UI would be deployed as a tenant-scoped, standalone web container.
The hypothesis was that restricting the UI to tenant operations would limit the attack surface.

---

## Scope Expansion — Observed & Validated

During testing, it was discovered that a **Global Admin** can log in to the tenant UI and it works
correctly. This is by design: the backend scopes all API responses via the JWT, so the caller's
role determines what data is returned and which operations are permitted. The UI naturally
reflects this.

This observation triggered a **strategic reassessment** (Feb 2025).

### Features added beyond the original tenant-only scope

| Feature | Type | Trigger |
|---|---|---|
| Admin deactivation (`POST /api/v1/admins/{id}/deactivate`) | Global Admin only | Discovered during review; conditionally shown (`isGlobalAdmin`) |
| Test Authentication simulator (`POST /api/v1/auth-attempts`) | All admins | Intentional expansion for operational value |

### Test Authentication feature (enrollment-detail page)

**Use case**: A Tenant Admin creates an enrollment, shares credentials with an end-user, and wants
to confirm the device binding is working before going to production.

**Implementation**:
- Button "Test Authentication" visible on `enrollment-detail` page only when `enrollmentStatus === 'VERIFIED'`
- Opens a 3-step dialog:
  1. **Configure** — optional challenge code checkbox (pre-checked if `authAttemptChallengeRequired`)
  2. **Live** — shows attempt ID, MM:SS countdown from `expiresAt`, 2-digit challenge code (if requested), live status polling every 3s
  3. **Done** — contextual result (Accepted / Rejected / Expired / Invalid / Cancelled)
- Cancel button calls `POST /api/v1/auth-attempts/{id}/cancel`
- Polling stops automatically on any final status

---

## Strategic Direction — Future Phases

The following decision was made (Feb 2025): **do not rename or expand scope now**; complete the
current tenant UI iteration first, then plan a dedicated phase to generalize.

### Planned evolution

| Phase | Scope | Decision |
|---|---|---|
| **Current** | Complete `ezkey-tenant-ui` as-is | ✅ In progress |
| **Next** | Add Global Admin features to the web UI (tenant CRUD, crypto key rotation, system-wide monitoring) | Planned post-release |
| **Future** | Rename project to `ezkey-admin-ui`; retire the TUI interactive mode | Planned |

### CLI Python fate

- **CLI (non-interactive) mode**: Retains full value for automation, CI/CD, scripting. Not deprecated.
- **TUI (interactive) mode**: Will be phased out once the web UI covers 100% of its use cases.
- The Python CLI will eventually be repositioned as a pure automation/scripting tool.

### Guiding principle

> The backend is the authoritative gate. Role-based access (JWT claims) determines visibility
> and permissions. The UI should reflect what the backend allows — not artificially restrict it.
> Expanding UI scope is low-risk because the backend enforces all security boundaries.
