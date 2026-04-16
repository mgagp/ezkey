## Plan: Admin UI Lifecycle Governance — Phase 2

> **Status: COMPLETED — April 16, 2026**
> Archived after functional validation on live Docker stack.

Propagate the Phase 1 backend governance changes to ezkey-admin-ui, entity-by-entity top-down. Core deliverables: `operational` discrepancy warnings, required-reason enforcement on all irreversible actions, INACTIVE removal cleanup, and full contextual help coverage for all entities.

---

### Locked Decisions

- Integration filter: rename "Operational" → "Active" (aligns with actual backend behavior: no params sent = active only)
- Operational discrepancy display: **inline warning strip** below status badges (amber/red, non-intrusive)
- Help topics: **Full coverage** — ContextHelp popovers on danger zone sections + help drawer topics for all 4 missing entities (tenants, enrollments, api-keys, admins)

---

### New DTO Fields in Scope (from Phase 1)

All 5 entities now have `operational: boolean` in their Orval-generated DTOs:

| Entity | `operational` is `true` when |
|--------|------------------------------|
| Tenant | `active` |
| Integration | `lifecycleStatus === 'ACTIVE'` + tenant active |
| Enrollment | `enrollmentStatus === 'VERIFIED'` + `enrollmentActive` + integration ACTIVE + tenant active |
| API Key | `active` + not expired + integration operational + tenant active |
| Admin (Tenant) | `active` + tenant active |

Integration `lifecycleStatus` is now `'ACTIVE' | 'RETIRED'` only — `INACTIVE` is gone.

---

### Display Rule for `operational` Warning Strip

Show the `OperationalWarning` strip **only** when the entity's own native status looks healthy but `operational === false` (i.e., parent chain is degraded):

| Entity | Condition |
|--------|-----------|
| Tenant | Never shown — redundant with `active` |
| Integration | `lifecycleStatus === 'ACTIVE' && operational === false` |
| Enrollment | `enrollmentStatus === 'VERIFIED' && enrollmentActive === true && operational === false` |
| API Key | `active && !revokedAt && !isExpired && operational === false` |
| Admin | `adminType === 'TENANT_ADMIN' && active && operational === false` |

---

### Phase A — Foundation + INACTIVE Cleanup *(prerequisite for everything)*

**Must change:**
1. Remove `IntegrationLifecycleStatus` local type that includes `'INACTIVE'` from `integrations.tsx`
2. Remove the `'inactive'` option from `IntegrationListFilter` type and dropdown
3. Rename filter dropdown label: `'operational'` option → `'active'` (EN + FR i18n)
4. Remove `IntegrationWithLifecycleStatus` transitional type — use `IntegrationResponseDto.lifecycleStatus` directly from Orval
5. Remove `getLifecycleStatus()` transitional fallback (the one returning `'INACTIVE'` when `active === false`)
6. Simplify integration status badge — only handles `ACTIVE` / `RETIRED`
7. Create shared `OperationalWarning` component: compact inline strip, icon + message prop, amber/warning variant for degraded-parent, red/error variant for blocked

**Files:**
- `ezkey-admin-ui/src/pages/integrations.tsx`
- `ezkey-admin-ui/src/pages/integration-detail.tsx`
- **New:** `ezkey-admin-ui/src/components/feature/operational-warning.tsx`
- `ezkey-admin-ui/src/locales/en/integrations.json` + `fr/integrations.json`

**Depends on:** nothing. *Prerequisite for C. Parallel with B.*

---

### Phase B — Tenant *(simplest, top of hierarchy)*

**Must change:**
1. `operational` field: **not** surfaced separately — identical to `active`, redundant — no badge added
2. Add `ContextHelp` popover to the deactivate/activate danger zone section; text explains downstream blast radius: "Deactivating a tenant blocks authentication runtime for all its integrations and enrollments — no persistent cascade, reversible at any time"
3. Create `tenants` and `tenant-detail` help drawer topics (EN + FR)

**Files:**
- `ezkey-admin-ui/src/pages/tenant-detail.tsx`
- `ezkey-admin-ui/src/locales/en/help.json` + `fr/help.json` (add `tenants`, `tenant-detail` topics)
- `ezkey-admin-ui/src/locales/en/tenants.json` + `fr/tenants.json` (add contextHelp keys)

**Depends on:** A. *Parallel with A.*

---

### Phase C — Integration *(primary entity under tenant)*

**Must change:**
1. Add `OperationalWarning` strip in `integration-detail.tsx` when `operational === false && lifecycleStatus === 'ACTIVE'` — message: "Tenant is currently inactive — authentication will be blocked for this integration"
2. Integration list status column: add subtle warning icon/indicator on rows where `operational === false && lifecycleStatus === 'ACTIVE'`
3. **Retire reason → required**: change `DangerConfirmDialog` for retire to `optionalReason={false}` (currently `optionalReason=true`)
4. **Delete reason → required**: same change for delete `DangerConfirmDialog`
5. Add `ContextHelp` popovers for the retire section and the delete section in the danger zone
6. Update `integrations` and `integration-detail` help drawer topics to mention: INACTIVE removal, `operational` concept, retire/delete require reason

**Files:**
- `ezkey-admin-ui/src/pages/integration-detail.tsx`
- `ezkey-admin-ui/src/pages/integrations.tsx`
- `ezkey-admin-ui/src/locales/en/integrations.json` + `fr/integrations.json`
- `ezkey-admin-ui/src/locales/en/help.json` + `fr/help.json` (update existing integration topics)

**Depends on:** A, B.

---

### Phase D — Enrollment *(most complex — FSM status + active flag + parent chain)*

**Must change:**
1. Add `OperationalWarning` strip when `operational === false && enrollmentStatus === 'VERIFIED' && enrollmentActive === true` — message: "Authentication blocked — parent integration or tenant is not operational"
2. **Disable `Test Auth` button** when `enrollment.operational === false`; add `Tooltip`: "Authentication not available — parent entity is not operational"
3. `canEditMetadata` guard: **leave as-is** (`VERIFIED + active`) — editing metadata is independent of runtime auth capability
4. **Delete reason → required**: add `ReasonFieldRow` to the inline delete expand section (currently has **no reason field at all**) — min 10 chars, required, preset group `enrollment_delete`
5. Enrollment list: add warning indicator on rows that are `VERIFIED + enrollmentActive + operational === false`
6. Add `ContextHelp` popovers for: deactivate/reactivate section, revoke section, delete section
7. Create `enrollments` and `enrollment-detail` help drawer topics (EN + FR); cover FSM states, deactivate vs revoke distinction, `operational` status

**Files:**
- `ezkey-admin-ui/src/pages/enrollment-detail.tsx`
- `ezkey-admin-ui/src/pages/enrollments.tsx`
- `ezkey-admin-ui/src/locales/en/enrollments.json` + `fr/enrollments.json`
- `ezkey-admin-ui/src/locales/en/help.json` + `fr/help.json`

**Depends on:** A–C. *Parallel with E, F.*

---

### Phase E — API Key *(revoke-only model, simpler)*

**Must change:**
1. Update local `StatusBadge` in `api-key-detail.tsx`: add "Blocked by parent" variant computed when `active && !revokedAt && !isExpired && !operational`
2. Add `OperationalWarning` strip when `operational === false && isActive && !isExpired` — message: "Integration or tenant not operational — this key cannot be used for authentication"
3. **Revoke reason → required** in `RevokeApiKeyDialog`: empty reason → submit button disabled (change from optional to required validation)
4. API keys list: warning indicator on rows where `active && operational === false`
5. Add `ContextHelp` popover for the revoke section
6. Create `api-keys` and `api-key-detail` help drawer topics (EN + FR); cover revoke-only model, expiry, `operational` status, no-reactivation policy

**Files:**
- `ezkey-admin-ui/src/pages/api-key-detail.tsx`
- `ezkey-admin-ui/src/pages/api-keys.tsx` — `RevokeApiKeyDialog`
- `ezkey-admin-ui/src/locales/en/api-keys.json` + `fr/api-keys.json`
- `ezkey-admin-ui/src/locales/en/help.json` + `fr/help.json`

**Depends on:** A–C. *Parallel with D, F.*

---

### Phase F — Admin *(role-sensitive — operational depends on adminType)*

**Must change:**
1. **Tenant Admin only**: add `OperationalWarning` strip when `active === true && operational === false` — message: "Admin is active but their tenant is currently inactive — they cannot authenticate"
2. **Wire activate reason**: the activation `ReasonFieldRow` already exists in the UI (optional); wire its value to the `reason` parameter of the `useActivateAdmin()` Orval hook (Phase 1 added this param on the backend — currently not passed)
3. **Admins list**: check if `ListAdminsParams` now has an `active` filter after Phase 1; add filter dropdown if available; document as known gap if param does not exist yet
4. Add `ContextHelp` popovers for deactivate and activate sections
5. Create `admins` and `admin-detail` help drawer topics (EN + FR); cover adminType distinction, deactivation vs enrollment revocation, `operational` for tenant admins

**Files:**
- `ezkey-admin-ui/src/pages/admins.tsx`
- `ezkey-admin-ui/src/locales/en/admins.json` + `fr/admins.json`
- `ezkey-admin-ui/src/locales/en/help.json` + `fr/help.json`

**Depends on:** A–B. *Parallel with D, E.*

---

### Phase G — Reason Policy Verification Pass *(single-pass audit)*

After C, D, E — confirm alignment:

| Action | Entity | Required? | Change made |
|--------|--------|-----------|-------------|
| Retire | Integration | **Yes** | Phase C |
| Delete | Integration | **Yes** | Phase C |
| Delete | Enrollment | **Yes** | Phase D |
| Revoke | API Key | **Yes** | Phase E |
| Revoke | Enrollment | Yes | Already correct |
| Deactivate | All | Optional | Correct — no change |
| Reactivate | All | Optional | Correct — no change |
| Activate | Admin | Optional | Correct — no change |

**Depends on:** C, D, E.

---

### Phase H — Playwright Validation *(formality)*

Run existing Playwright browser suite on clean-start stack. Expected: no regression — tests are intentionally kept at summary level with no deep lifecycle-specific assertions.

Check specifically:
- Integration list and detail load without INACTIVE reference errors
- Retire / delete dialogs open and validate
- Enrollment danger zone actions still work
- Admin deactivate flow still works

**Depends on:** A–G.

---

### Execution Order

```
A ──────────────────────────────────────────┐
B (parallel with A)                         │
                                             └── C ────────────────────────────────┐
                                                                                    ├── D ─┐
                                                                                    ├── E  ├── G → H
                                                                                    └── F ─┘
```

**Minimum critical path:** A + B → C → D / E / F → G → H

---

### Implementation Waves (context management)

| Wave | Phases | Description |
|------|--------|-------------|
| 1 | A + B + C | Foundation (INACTIVE cleanup, shared `OperationalWarning`) → Tenant → Integration |
| 2 | D + E + F | Enrollment + API Key + Admin (parallel) |
| 3 | G + H | Verification + Playwright |

---

### Verification Checkpoints

1. `npm run build` passes in `ezkey-admin-ui/` with no TypeScript errors
2. `INACTIVE` fully gone from admin-ui TypeScript source (`grep -r INACTIVE src/`)
3. Integration filter dropdown shows "Active" (not "Operational")
4. All 4 new help drawer topics render without error in browser (tenants, enrollments, api-keys, admins)
5. `OperationalWarning` strip appears: deactivate tenant on clean-start → navigate to one of its integrations → strip visible under status badge
6. Revoke/retire/delete dialogs have submit disabled when reason field is empty
7. `Test Auth` button is disabled/tooltip shown on an enrollment whose integration is RETIRED or whose tenant is inactive
8. Playwright suite: `npx playwright test` — no regression baseline

---

### Scope Exclusions

- No new backend API params (frontend-only phase)
- `canEditMetadata` on enrollments: **not** updated — metadata editing is independent of runtime auth capability
- DELETE `/api-keys/{id}` → POST `/revoke` rename: still deferred
- No Admin UI navigation structure changes
- No bulk action UI changes
- Tenant `operational` badge: not added (identical to `active`, redundant)

---

### Relevant Files

**Pages:**
- `ezkey-admin-ui/src/pages/integrations.tsx` — Phase A + C
- `ezkey-admin-ui/src/pages/integration-detail.tsx` — Phase A + C
- `ezkey-admin-ui/src/pages/tenant-detail.tsx` — Phase B
- `ezkey-admin-ui/src/pages/enrollment-detail.tsx` — Phase D
- `ezkey-admin-ui/src/pages/enrollments.tsx` — Phase D
- `ezkey-admin-ui/src/pages/api-key-detail.tsx` — Phase E
- `ezkey-admin-ui/src/pages/api-keys.tsx` — Phase E
- `ezkey-admin-ui/src/pages/admins.tsx` — Phase F

**New:**
- `ezkey-admin-ui/src/components/feature/operational-warning.tsx` — Phase A

**Locales (EN + FR):**
- `integrations.json` — Phase A + C
- `tenants.json` — Phase B
- `enrollments.json` — Phase D
- `api-keys.json` — Phase E
- `admins.json` — Phase F
- `help.json` — Phases B–F (new topics: tenants, tenant-detail, enrollments, enrollment-detail, api-keys, api-key-detail, admins, admin-detail; updated: integrations, integration-detail)

---

### Suivi post-implémentation

Décisions de design prises après validation fonctionnelle (exploration navigation réelle sur stack Docker).

#### 2025-04-16 — Chip composite "dégradé-actif" dans les listes

**Contexte :** Lors de la navigation, l'icône `AlertTriangle` (`size-3.5`, `text-warning`) à côté du badge statut dans les colonnes de liste était trop discrète — visuellement détachée du badge et trop petite pour être repérée sans effort.

**Options considérées :**
- **A** — Chip composite : badge + icône encadrés d'un contour `border border-warning/40 rounded-sm px-1.5`, icône à `size-4`. Cohésion visuelle sans surcharger la ligne.
- **B** — Remplacer le badge `success` par `warning` avec texte modifié. Rejeté : perd l'information "actif à ce niveau", induit en erreur.
- **C** — Agrandir l'icône uniquement (`size-5`). Rejeté : sans groupement, l'icône reste visuellement flottante.

**Décision :** Option A retenue. Inspirée du pattern "degraded active" (Grafana, Linear). Le contour ambré léger dit visuellement *"actif, mais quelque chose mérite attention"* sans écraser les autres colonnes. Conforme au style neo-brutalist du projet (joue sur les bordures).

**Implémentation :**
- Icône : `size-3.5` → `size-4` dans toutes les listes (`integrations.tsx`, `enrollments.tsx`, `api-keys.tsx`, `admins.tsx`).
- Contour conditionnel : `border border-warning/40 rounded-sm px-1.5` appliqué uniquement quand `operational === false` (state dégradé).
- `border-2` non utilisé — réservé aux éléments interactifs forts dans ce projet.
- Tooltip conservé tel quel (texte explicatif au survol).

#### 2025-04-16 — Tooltip sur chip entier plutôt que sur l'icône seule

**Contexte :** Après validation visuelle, le `Tooltip` sur l'icône `⚠` seule (zone de survol ~16 px) passait inaperçu. L'utilisateur positionnait la souris sur le badge "Active / Yes" et n'obtenait aucun feedback.

**Décision :** Remonter le `Tooltip` pour envelopper le chip entier (badge + icône) uniquement quand `operational === false`. Sans état dégradé, aucun tooltip — le badge seul est auto-explicatif. Conforme au pattern Grafana/Datadog : l'état dégradé rend l'élément entier hoverable.

**Implémentation :**
- Restructure conditionnelle dans les 4 listes : si `operational === false`, le `Tooltip` contient le chip ; sinon, juste le badge sans wrapper.
- Zone de survol : de ~16 px (icône) → toute la largeur du chip composite.
- Texte du tooltip inchangé — les clés `list.operationalWarningTooltip` existantes sont calibrées pour une phrase courte.
