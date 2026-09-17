# Inventory — Admin authorization status quo

## Metadata

- **Pack:** [`README.md`](README.md)
- **Date:** 2026-09-16
- **Method:** white-box read of Admin API + existing assessments / hygiene notes
- **Claim discipline:** only behaviors evidenced in the tree; known debt called out as debt

## 1. Authority assignment (not JWT)

Admin sessions are **opaque DB-backed tokens**, not JWTs. On each validated request,
`AdminTokenAuthenticationFilter` builds an `AdminPrincipal` and grants authorities:

```135:143:ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminTokenAuthenticationFilter.java
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        AdminType adminType = admin.getAdminType();
        if (adminType == AdminType.GLOBAL_ADMIN) {
          authorities.add(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN"));
        } else if (adminType == AdminType.TENANT_ADMIN) {
          authorities.add(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
        }
```

Scope fields on the principal (`tenantId` for Tenant Admin; null for Global Admin) are set in
the same filter when constructing `AdminPrincipal`:

```125:133:ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminTokenAuthenticationFilter.java
        Integer tenantId = null;
        if (admin.getAdminType() == AdminType.TENANT_ADMIN) {
          tenantId = adminToken.getTenant() != null ? adminToken.getTenant().getTenantId() : null;
        }
        Integer integrationId =
            adminToken.getIntegration() != null ? adminToken.getIntegration().getId() : null;

        AdminPrincipal principal =
            new AdminPrincipal(admin.getAdminId(), admin.getAdminType(), tenantId, integrationId);
```

`AdminPrincipal` is an immutable record (`adminId`, `adminType`, `tenantId`, `integrationId`)
with `isGlobalAdmin()` / `isTenantAdmin()` helpers
(`ezkey-admin-api/.../AdminPrincipal.java`).

DB / domain type lives on `EzkeyAdmin.AdminType` (`GLOBAL_ADMIN`, `TENANT_ADMIN`,
`INTEGRATION_ADMIN`) in `ezkey-core/.../EzkeyAdmin.java`. Product language for the two human
operator types: `docs/LIFECYCLE_GOVERNANCE.md` §3.5.

## 2. Two-check model (function + object)

Cold-agent canon is already written:

```15:36:ezkey-admin-api/AGENTS.md
`AdminTokenAuthenticationFilter` always grants `ROLE_ADMIN`, then adds
`ROLE_GLOBAL_ADMIN` or `ROLE_TENANT_ADMIN`. `@PreAuthorize("hasRole('ADMIN')")` means
**any authenticated human admin**. It is not tenant isolation and not a Global-only
gate.
...
- **Greenfield note:** if assigning authorities from scratch, issue only the two type
  roles (no umbrella). Isolation still is not a Spring role.
```

| Layer | What it answers | Typical mechanism |
|-------|-----------------|-------------------|
| **Function** | May this *class* of API be called? | `@PreAuthorize` (`ADMIN` umbrella, `GLOBAL_ADMIN`, rarely `hasAnyRole`) |
| **Object** | May this *row* be touched? | `AccessControlService.canAccess*` / principal `tenantId` / dialect 2–3 |

Method security is enabled (`@EnableMethodSecurity` on
`ezkey-admin-api/.../SecurityConfig.java`). **No `@PreAuthorize` on Admin API services** was
found under `org.ezkey.admin.service` — role gates live on controllers. Services either receive
an already-authorized call or take `AdminPrincipal` and apply domain rules (dialect 3).

## 3. Quantitative surface (Admin API `src/main`, 2026-09-16 tree)

Approximate counts from ripgrep (method annotations; one class-level on encryption keys):

| Pattern | Approx. count | Controllers (examples) |
|---------|---------------|------------------------|
| `hasRole('ADMIN')` | **36** | Enrollment, Integration, ApiKey, AuthAttempt, Dashboard, Audit (list), AdminProvisioning (shared) |
| `hasRole('GLOBAL_ADMIN')` / `ROLE_GLOBAL_ADMIN` | **25** | EncryptionKey (class-level), Tenant, Alert, Audit chain ops, some AdminProvisioning |
| `hasAnyRole(...GLOBAL..., ...TENANT...)` | **1** | `AdminProvisioningController` |

Object checks: `AccessControlService` used from Enrollment, Integration, ApiKey, AuthAttempt
controllers (dialect 1). Integration retire/delete still uses inline Tenant Admin + tenant id
(dialect 2 — documented legacy). Admin provisioning / onboarding uses service principal rules
(dialect 3).

Platform example (function split done correctly after SEC-017):

```108:114:ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
...
public class EncryptionKeyController {
```

Object check shape:

```91:106:ezkey-admin-api/src/main/java/org/ezkey/admin/security/AccessControlService.java
  public boolean canAccessAuthAttempt(Authentication auth, Integer authAttemptId) {
    // ...
    if (hasRole(auth, "ROLE_GLOBAL_ADMIN")) {
      return true;
    }
    if (hasRole(auth, "ROLE_TENANT_ADMIN")) {
      return canAccessAuthAttemptForTenant(auth, authAttemptId);
    }
    return false;
  }
```

## 4. How Global vs Tenant is expressed (summary)

| Concern | Expression |
|---------|------------|
| Identity type | DB `admin_type` / `EzkeyAdmin.AdminType` |
| Session scope | `AdminPrincipal.tenantId` (Tenant Admin only) |
| Spring roles | Always `ROLE_ADMIN` + exactly one of `ROLE_GLOBAL_ADMIN` / `ROLE_TENANT_ADMIN` (for those types) |
| UI branching | Session `adminType === 'GLOBAL_ADMIN'` (unified SPA) — e.g. `ezkey-admin-ui/AGENTS.md`, pages such as `dashboard.tsx`, `audit-logs.tsx` |
| List filtering | Automatic from principal tenant (Tenant Admin) vs all tenants (Global Admin) — Admin API `AGENTS.md` § Tenant-scoped list endpoints |
| Cross-tenant Global operator | Intentional on shared surfaces; distinct from “no impersonation” on **integration create** (Global → system tenant only) |

## 5. Cross-tenant deny semantics (404 vs 403 debt)

Target product story (hide existence): get-by-id style deny → **HTTP 404** + Problem type
`https://ezkey.io/problems/admin/resource-not-found` (`ezkey-admin-api/AGENTS.md`).

**Already aligned:** enrollment QR (`canAccessEnrollment` → 404) — closed with CTRL-ROLE-001 /
[PR #470](https://github.com/mgagp/ezkey/pull/470).

**Known residual debt (do not mass-change in a drive-by):** JSON GET/PATCH/revoke often still
empty **403** on `canAccess*` false; API-key get after load 403 foreign vs 404 missing; admin
GET foreign 403 / onboarding PATCH foreign 400. Documented in the same AGENTS subsection and in
`CTRL-ROLE-003` / `003b`.

Functional tests already accept mixed oracles on some rows (e.g.
`TenantCrossIsolationSecurityTest` enrollment get “403/404”).

## 6. Admin UI implications

One SPA serves Global and Tenant Admins. Role-dependent chrome uses `session.adminType`, not
Spring authorities. Backend remains the security boundary; UI hiding is convenience, not
authorization (`docs/SECURITY_CHALLENGE_REPORT_2026-07.md` notes backend 403 tests must prove
SEC-017 — not Playwright alone).

## 7. Failure modes for agent / human drift

These are the realistic failure modes under the **current** umbrella — not claims of live
exploits beyond what assessments already closed or flagged.

| Failure mode | How it happens | Precedent / evidence |
|--------------|----------------|----------------------|
| **Global-only op callable by Tenant Admin** | New or edited controller uses only `hasRole('ADMIN')` on a platform surface | **SEC-017** — encryption keys were umbrella-gated; closed with class-level `GLOBAL_ADMIN` (`docs/SECURITY_CHALLENGE_REPORT_2026-07.md`, `EncryptionKeyControllerSecurityWebMvcTest`) |
| **Cross-tenant leak on get-by-id / mutate** | Umbrella passes; **missing** `canAccess*` (or dialect 2/3 equivalent) | **CTRL-ROLE-001** enrollment QR invite-secret distribution; fixed with `canAccessEnrollment` + 404 |
| **False confidence from “precise” annotation** | `hasAnyRole(GLOBAL, TENANT)` without object check still admits Tenant Admin to the *route* | Explicitly warned in Admin API `AGENTS.md` |
| **Service-layer role annotation myth** | Adding `@PreAuthorize("hasRole('ADMIN')")` on a service looks like authz but still admits both admin types; isolation is not a role | No service `@PreAuthorize` today; challenge is conceptual — services must not be treated as the isolation boundary unless they encode tenant rules with the principal |
| **List over-share** | Unscoped repository/`findAll` used from a controller path | SEC-015 residual / CTRL-ROLE-008 — unscoped helpers with no controller caller noted; companion SQL isolation work ([PR #539](https://github.com/mgagp/ezkey/pull/539)) addresses related existence-oracle / loader dialects |
| **Agent copy-paste** | New `GET /{id}/…` copied from a neighbor that only has the umbrella | Same class as QR; AGENTS now states this explicitly |

**Critical property:** under the umbrella, tenant isolation is **opt-in per method** (second
check). Missing the second check fails **open** for Tenant Admins who already hold `ROLE_ADMIN`.

## 8. What is already documented vs tribal

| Topic | Documented? | Where |
|-------|-------------|--------|
| Umbrella meaning + two-check rule | Yes | `ezkey-admin-api/AGENTS.md` |
| Keep-umbrella Path A (2026-08-20) | Yes | Hygiene campaign note; AGENTS “Settled 2026-08-20” |
| Role story in security matrix | Yes (corrected) | `docs/API_SECURITY_MATRIX.md` — endpoint tables still a lagging sketch |
| Assessment register CTRL-ROLE-* | Yes | `docs/java-controller-role-validation-assessment-2026-08.md` |
| SEC-017 / July challenge | Yes | `docs/SECURITY_CHALLENGE_REPORT_2026-07.md` |
| Lifecycle Global vs Tenant purpose | Yes | `docs/LIFECYCLE_GOVERNANCE.md` §3.5 |
| Greenfield “drop umbrella” note | Yes (one paragraph) | Admin API `AGENTS.md` |
| Formal ADR keep vs split | **No until this pack** | Proposed ADR-0013 |
| Cursor always-applied rule mirroring AGENTS | **No** | Risk: agents that skip module `AGENTS.md` |
| Complete annotation honesty (`hasAnyRole` everywhere shared) | **Not done** (optional hygiene) | 002b explicitly deferred |
| Unit tests for Tenant Admin `canAccess*` deny/allow | **Thin** | `AccessControlServiceTest` covers Global Admin + invalid auth only (see decision brief) |

## 9. Corpus that already challenged the umbrella

| Source | Signal |
|--------|--------|
| Assessment register 2026-08 | CTRL-ROLE-002: shared gate makes isolation opt-in; greenfield would drop umbrella |
| Hygiene 2026-08-16 pass-1 | 002b Path A: keep issuing `ROLE_ADMIN`; annotation honesty optional; filter cutover not funded |
| SEC-017 | Concrete Global-only failure under umbrella-only gate |
| `docs/API_SECURITY_MATRIX.md` (post-fix) | Explicitly: `ROLE_ADMIN` is not tenant isolation and not Global-only |
| `plan-securityChallengeCryptographicBackendAudit.prompt.md` Domaine 5 | Asks whether `@PreAuthorize` is correctly used for Global vs Tenant |
| Admin API `AGENTS.md` greenfield note | Already states how a from-scratch model would look |

## 10. Related but distinct workstreams

Do **not** conflate with this decision:

- **403 → 404 hide-existence peels** (CTRL-ROLE-003b) — deny *shape*, not role model.
- **Dialect convergence** (retire/delete → dialect 1) — object-check location.
- **Tenant SQL isolation assessment** ([PR #539](https://github.com/mgagp/ezkey/pull/539)) — service/SQL existence oracles and unscoped loaders.
- **Integration API `ROLE_API_KEY`** — machine surface; Admin API does not authenticate API keys.
