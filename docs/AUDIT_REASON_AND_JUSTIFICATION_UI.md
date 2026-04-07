# Audit reason and justification — Admin UI

Single reference for **product rules**, **API/UI alignment**, and the **reason/justification quick-pick** work completed in the Admin UI. It replaces the former split across analysis, UX review, and implementation design documents.

For HTTP details, see **`docs/ENDPOINT.md`**. For Admin UI conventions (i18n, demo mode, builds), see **`ezkey-admin-ui/AGENTS.md`**.

---

## 1. Product rules

- **Optional `reason` / `justification`:** Empty is valid. When the operator enters text, the client should enforce **min 10 and max 500 characters** (after trim) wherever the API does, so the backend does not return 400 for length.
- **Mandatory reason:** Only where the product requires it (notably **single enrollment revoke**). Same length rules: **10–500**.
- **Audit trail:** Values remain **free text**; there is no normalized reason code in the log for this feature set.
- **Naming:** Most lifecycle APIs use **`reason`**. Audit chain lifecycle (archive seal, gap declaration) uses **`justification`** in the body.

---

## 2. API vs Admin UI (representative)

| Area | API shape | UI behavior (summary) |
|------|-----------|-------------------------|
| Tenant deactivate / activate | Body `reason`, optional, 10–500 when present | Toggle dialog; optional; trim; min 10 when non-empty |
| Admin deactivate / activate | Query `reason`, optional, 10–500 when present | Deactivate dialog + activate block; same |
| Enrollment revoke | Query `reason`, **required**, 10–500 | Required; presets + inline error if 1–9 chars |
| Enrollment deactivate / reactivate | Query `reason`, optional; **max 500** (no min in API) | Optional; UI uses min 10 when non-empty (stricter than API for consistency) |
| Enrollment delete | Query `reason`, optional, 10–500 when present | As implemented on detail page |
| Integration retire / delete / revoke-all | Query `reason`, optional; min 10 when present for retire/delete/revoke-all | Danger dialogs + presets |
| Integration deactivate-all / reactivate-all | Query `reason`, optional; **max 500** only | Same strict client rule as other optional flows |
| API key revoke | Query `reason`, optional, 10–500 when present | Revoke dialog + presets |
| Encryption key rotate | Body `reason`, optional, 10–500 when present | Rotate dialog + presets (`encryption_key_rotate`) |
| Audit seal archive / declare gap | Body `justification`, **required**, 10–500 | Seal/gap dialogs + presets (`audit_chain_justification`); trim on submit |

**Note:** Some endpoints accept short optional strings (pattern B). The UI still blocks **1–9** characters when the field is non-empty, which is **stricter** than those endpoints but keeps one mental model for operators.

---

## 3. What we implemented (Admin UI)

### 3.1 Approach

- **Localized suggestion lists** owned by the UI: `ezkey-admin-ui/src/locales/en|fr/reasonPresets.json`, namespace **`reasonPresets`**.
- **`ReasonQuickPick`:** native select that **replaces** the text field content when a row is chosen; user can edit afterward.
- **`ReasonFieldRow`:** quick-pick (when applicable) + label + input (`maxLength={500}`) + optional demo badges + shared inline error for non-empty text under 10 characters. Error copy uses `reasonPresets` keys: `minLengthErrorOptional`, `minLengthErrorRequired`, or `minLengthErrorJustification` (audit dialogs).
- **Demo mode:** `DemoReasonBadges` remains under **`isDemoMode && sessionDemoOn`**, below the production row, for QA; production bundles do not ship demo strings when demo is off.

### 3.2 Preset groups (`ReasonPresetGroupId`)

| Group id | Typical surfaces |
|----------|------------------|
| `api_key_revoke` | API key revoke |
| `tenant_toggle` | Tenant activate/deactivate |
| `admin_lifecycle` | Admin deactivate / activate |
| `enrollment_revoke` | Enrollment revoke |
| `enrollment_lifecycle` | Enrollment lifecycle dialog |
| `integration_bulk` | Integration bulk deactivate/reactivate/revoke-all |
| `integration_retire` | Retire integration |
| `integration_delete` | Delete integration (after retirement) |
| `encryption_key_rotate` | Rotate primary encryption key |
| `audit_chain_justification` | Archive seal + gap declaration |

Code keys live in **`ezkey-admin-ui/src/lib/reason-preset-groups.ts`**.

### 3.3 Rollout (completed)

- **M1–M2:** Wire quick-picks across tenant, admin, enrollment, integration, API key flows; trim and `maxLength` aligned.
- **M3:** Shared `ReasonFieldRow` and uniform handling of 1–9 character inputs.
- **Phase 2:** `encryption_key_rotate` and `audit_chain_justification` presets; justification fields trimmed on submit.

---

## 4. Conclusions from analysis

- **Ownership:** Keep presets in the **Admin UI i18n** unless multiple clients need identical lists, policy owners need non-developer edits without deploys, or reporting needs **codes**—then reconsider a shared catalog or code+text model.
- **Pattern:** Lightweight **editable suggestions** per operation family fit Ezkey better than a mandatory taxonomy at this stage.
- **Consistency:** Prefer **trim**, **max 500**, and blocking **1–9** when non-empty across dialogs, even where the API would accept shorter optional text.

---

## 5. Optional follow-ups (not committed)

- **E2E:** One or two Playwright scenarios (e.g. revoke enrollment with a preset)—risk-based, not mandatory for every dialog (`ezkey-admin-ui/AGENTS.md`).
- **CI / lint:** Optional check that `reasonPresets` EN/FR share the same option keys.
- **Admin activate:** Still uses manual `fetchApi`; migrating to Orval is unrelated to presets but improves consistency.
- Any **new** audited surfaces should reuse **`ReasonFieldRow`** / **`ReasonQuickPick`** and add options under `reasonPresets` with EN/FR parity.

---

## 6. Related code paths

| Item | Location |
|------|----------|
| Preset groups | `ezkey-admin-ui/src/lib/reason-preset-groups.ts` |
| Components | `ezkey-admin-ui/src/components/feature/reason-quick-pick.tsx`, `reason-field-row.tsx` |
| Demo badges | `ezkey-admin-ui/src/components/feature/demo-reason-badges.tsx`, `ezkey-admin-ui/src/lib/demo-mode.ts` |
| i18n registration | `ezkey-admin-ui/src/i18n.ts` |
