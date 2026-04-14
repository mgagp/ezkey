---
name: tenant timezone strategy
status: archived
archived_date: 2026-04-13
completion_status: fully_implemented
overview: Assess the current tenant timezone behavior in Admin UI/API and propose a pragmatic timezone strategy that keeps UTC as the storage standard, avoids accidental UX complexity, and identifies a safe quick win versus a fuller tenant-timezone display mode.
todos:
  - id: decide-timezone-policy
    content: "Confirm the product policy: introduce a profile-based persisted local-vs-tenant display mode rather than a login-time prompt."
    status: completed
  - id: validate-tenant-timezone
    content: If execution is approved, add real IANA timezone validation on tenant create/update instead of max-length-only validation.
    status: completed
  - id: design-profile-entry-point
    content: Define a lightweight profile entry point in the header for user preferences, starting with timezone selection and remaining extensible for future profile settings.
    status: completed
  - id: design-ui-timezone-layer
    content: If tenant mode is approved, centralize display timezone resolution in Admin UI formatting and date-range helpers behind the profile preference.
    status: completed
isProject: false
---

# ⚠️ ARCHIVED PLAN

**This plan has been fully implemented and archived for historical reference.**

**Completion Date:** 2026-04-13  
**Status:** ✅ Fully implemented and validated

**What was delivered (summary):** Backend IANA validation on tenant `timezone`; Admin login/session `tenantId` / `adminId` for scoped tenant fetch; Admin UI display timezone preference (local vs tenant, persisted in `localStorage`), centralized `Intl` formatting and date-range → API bounds; header **Time zone** control (clock + label) and first-level **Logout** (icon + label).

---

# Tenant Timezone Analysis

## Current State

- Tenant timezone is captured in the Admin UI create/edit forms and sent to the backend from [ezkey-admin-ui/src/pages/tenants.tsx](ezkey-admin-ui/src/pages/tenants.tsx) and [ezkey-admin-ui/src/pages/tenant-detail.tsx](ezkey-admin-ui/src/pages/tenant-detail.tsx).
- Backend tenant timezone is stored on the `Tenant` entity and returned by tenant APIs via [ezkey-core/src/main/java/org/ezkey/integration/domain/entity/Tenant.java](ezkey-core/src/main/java/org/ezkey/integration/domain/entity/Tenant.java), [ezkey-admin-api/src/main/java/org/ezkey/admin/dto/request/TenantCreateRequestDto.java](ezkey-admin-api/src/main/java/org/ezkey/admin/dto/request/TenantCreateRequestDto.java), and [ezkey-admin-api/src/main/java/org/ezkey/admin/dto/request/TenantUpdateRequestDto.java](ezkey-admin-api/src/main/java/org/ezkey/admin/dto/request/TenantUpdateRequestDto.java), and [ezkey-admin-api/src/main/java/org/ezkey/admin/service/TenantService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/TenantService.java).
- The current backend only validates `timezone` by max length; it does not validate against IANA `ZoneId`, and there is no downstream business logic using tenant timezone today.
- The Admin UI currently formats dates with `Intl.DateTimeFormat` without a `timeZone` option in [ezkey-admin-ui/src/lib/utils.ts](ezkey-admin-ui/src/lib/utils.ts), so displayed timestamps follow the browser local timezone.
- Date filters also use local-browser calendar semantics in [ezkey-admin-ui/src/lib/date-range-presets.ts](ezkey-admin-ui/src/lib/date-range-presets.ts).
- Login/session data does not include tenant timezone or even tenant metadata in [ezkey-admin-ui/src/lib/auth.ts](ezkey-admin-ui/src/lib/auth.ts) and [ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/AdminLoginResponseDto.java](ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/AdminLoginResponseDto.java).

## Product Positioning

- Keep **UTC** as the only storage and API transport standard for timestamps. That is already aligned with the documented API contract in [docs/ENDPOINT.md](docs/ENDPOINT.md).
- Treat **tenant timezone** as the tenant's business-operating timezone, not as a passive profile field. It should define how tenant-scoped business dates are interpreted when the product explicitly chooses a tenant-centric view.
- Treat **browser timezone** as a convenience signal only. It is a good bootstrap default for a personal preference, but it should not be the authoritative timezone for tenant operations.
- Do **not** introduce a login-time modal asking “local vs tenant timezone”. That adds friction, arrives too early in the flow, and is not a common best practice in comparable admin products.
- Prefer a stable **display preference** model instead, exposed from a lightweight **profile entry point** in the header rather than a one-off control hidden in date screens.

## Comparable Guidance

- Atlassian exposes timezone as a **user/profile preference**, which supports distributed operators without making organization timezone the only display source.
- Shopify exposes a **store timezone** as part of the business configuration, which is appropriate for business operations tied to a store/tenant.
- The pragmatic pattern is therefore: **UTC storage + business/account timezone for business semantics + optional user display preference**.

## Recommended Strategy

- Short term: acknowledge that the current product stores tenant timezone but does not use it, and avoid pretending otherwise in UX or docs.
- Quick win: keep current browser-local rendering, but make the timezone context explicit on timestamp-heavy screens when needed (for example, “Displayed in your local timezone”). This is low-risk and removes ambiguity immediately.
- Medium-term preferred direction: add a small **profile affordance** in the header where “connected as …” appears today. Make the admin identity visually interactive with a profile icon, dropdown, or compact account menu, and use that as the home for personal UI preferences.
- Phase 1 of that profile surface should focus only on **timezone display preference** with `local` and `tenant` modes, persisted locally. Default rules should stay simple:
  - Tenant Admin: default to `tenant` once the tenant timezone is known.
  - Global Admin / cross-tenant views: default to `local`.
  - Fallback to `local` whenever tenant timezone is unavailable or invalid.
- The profile entry point is the right long-term pattern because it is typical, discoverable, and naturally extensible for future user-level preferences without adding accidental complexity now.
- If tenant mode is introduced, apply it consistently to both **timestamp rendering** and **date-range filter semantics**. Doing display-only conversion while filters remain local would create confusion.

## Header Profile Concept

- Recommended UX direction: make the current “connected as `username` / admin type” area visibly actionable.
- Preferred pattern: a compact **account menu** in the header, not a full profile page for the first iteration.
- Phase 1 menu content should stay intentionally small:
  - Timezone display preference
  - Possibly a read-only identity summary
  - Existing logout action if that improves coherence
- Avoid overbuilding a broad profile model now. The value is in establishing the **entry point** and the **preference pattern**, not in inventing many settings before they exist.
- If the surface grows later, it can evolve from dropdown to dedicated profile/preferences screen without breaking the mental model.

## Scope Guardrails

- Avoid embedding tenant timezone into the login response unless there is a broader session-enrichment need. A post-login fetch of the current tenant is sufficient for Tenant Admin flows and keeps auth payloads lean.
- Avoid a per-login prompt, per-screen ad hoc toggles, or cross-tenant automatic switching. Those add accidental complexity and cognitive noise.
- Before relying on tenant timezone operationally, strengthen backend validation to reject invalid IANA identifiers.
- Keep the first profile iteration intentionally narrow: do not couple it to editable admin identity fields unless there is a separate product need.

## Likely Implementation Path If Approved

- Add explicit backend validation for tenant timezone using Java `ZoneId` semantics in tenant create/update DTO handling.
- Introduce a lightweight header account/profile trigger near the existing “connected as” area and persist the chosen timezone mode as a user preference on the client first.
- Introduce a small UI timezone preference layer near [ezkey-admin-ui/src/lib/utils.ts](ezkey-admin-ui/src/lib/utils.ts) so all formatters use one resolved display timezone.
- Fetch current tenant context after login for Tenant Admin flows instead of changing the login API first.
- Update date-range preset calculation in [ezkey-admin-ui/src/lib/date-range-presets.ts](ezkey-admin-ui/src/lib/date-range-presets.ts) only when tenant display mode is enabled, so calendar boundaries match displayed timestamps.
- Add subtle UI labeling of the active display timezone on screens where operational timestamps matter.

