# Grill Me — Blitz 2026-05-08-1, D3 (API key on Admin API)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-1.md` (D3) |
| **Vision** | `V-2026-0003` |
| **Backlog** | `I-2026-0004` |
| **Profiles** | `V-2026-0010` (no profile-aware code in platform) |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions (operator-confirmed)

### R1 — Single configuration flag

- Add one **boolean** property on Admin API (name TBD at implementation, e.g. `ezkey.admin.auth.api-key-auth-attempts-enabled`).
- **Default: `false`** everywhere — clean-start, docker dev/QA, HA, and new installs (**D3-A**). No dev-only permissive override in compose.
- Rationale for uniform `false`: a real misconfiguration was observed (Demo ACME + Java SDK targeting **Admin API** while operators assumed **Integration API**). Default deny makes the trust boundary explicit; M2M auth attempts belong on Integration API unless the operator deliberately opts in.
- **`true` opt-in** only for documented **minimal** deployments (Admin API + Auth API only, no Integration API binary) — **D3-B**.
- Aligns with `V-2026-0010`: configuration in properties / elaboration docs, not profile switches in code.

### Scope — `ROLE_API_KEY` M2M only

- Flag gates **API-key authentication for auth-attempt M2M** on Admin API (existing `ROLE_API_KEY` surface).
- **Unchanged:** Integration API always accepts API keys; admin session flows; API key lifecycle management on Admin API (create/revoke/list).
- **No** per-endpoint granularity in R1 (`Design Principle #1`).

### No removal in R1

- Do **not** remove API-key support from Admin API code paths.
- Flag = security-by-default + PME escape hatch; full removal → future separate idea if ever needed.

### Error model and DX

- On reject: **RFC 9457** with dedicated problem type; `detail` points to Integration API and/or the opt-in property.
- Update SDK examples and operator docs: **Integration API** = canonical M2M base URL.
- Review functional tests and Demo ACME config to target Integration API for API-key auth attempts.

### Deployment posture (`V-2026-0010`)

| Context | `api-key-auth-attempts-enabled` |
|---------|--------------------------------|
| Standard (Admin + Integration + Auth, incl. HA 2+2+2) | `false` — M2M via Integration (HAProxy) |
| Minimal (Admin + Auth only) | `true` — documented conscious trade-off |
| Phase 2 elaboration | Client doc records the choice; Phase 3 generates properties |

`I-2026-0004` is **not** blocked on a platform profile catalog (`V-2026-0002` archived); Phase 2 refines wording only.

### Migration / visibility

- Default **`false`** is a visible behavior change for anyone using API keys against Admin API for auth attempts.
- **No production deployments today** — brief release-note / docs mention only; no extended migration program.
- Note that SDK/samples historically referenced Admin API base URL; posture going forward is Integration API for M2M.

## Links

- [`../blitz-archive/blitz-2026-05-08-1.md`](../blitz-archive/blitz-2026-05-08-1.md)
- [`../ideas/I-2026-0004-admin-api-key-acceptance-flag.md`](../ideas/I-2026-0004-admin-api-key-acceptance-flag.md)
- [`../../vision/product-orientation-notes.md`](../../vision/product-orientation-notes.md) (`V-2026-0003`, `V-2026-0010`)
