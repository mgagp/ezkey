# Tracer Bullet Brief — `TB-2026-08-23-integration-api-cloudflare-schema` Integration API Cloudflare schema (EXP1)

## Metadata

- **ID:** `TB-2026-08-23-integration-api-cloudflare-schema`
- **Status:** `done`
- **Related idea:** `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`
- **Lane:** `A` — next executable slice of an existing program
- **Posture:** `iterative`
- **GitHub PR:** `#479` (merged 2026-08-23)
- **Created at:** `2026-08-23`
- **Updated at:** `2026-08-25`
- **Closed at:** `2026-08-23`
- **Captured by:** Marc

## Objective

Apply the proven Auth API Cloudflare API Shield recipe to the Integration API on
`https://exp1-integration-api.ezkey.org`. Prove that the same host-neutral canonical spec plus
EXP1-localized OAS 3.0 packaging path works for the three machine-to-machine auth-attempt
operations, without copying EXP1 Auth **Block** and without starting Admin Cloudflare upload.

The Integration API operations that must appear **once** under
`https://exp1-integration-api.ezkey.org` are:

- `POST /api/v1/auth-attempts`
- `GET /api/v1/auth-attempts/{id}/wait`
- `POST /api/v1/auth-attempts/{id}/cancel`

Cloudflare schema validation is ingress *shape* enforcement (method, path, JSON body). It does
not check API-key validity, IP whitelist, or enrollment ownership.

## Boundaries in scope

- `scripts/update-specs.sh --integration-only` host-neutral normalize (`jq 'del(.servers)'`).
- `specs/integration-api/openapi-spec.json` as the host-neutral canonical Integration API spec.
- Generated EXP1/Cloudflare Integration API schema artifact and packaging script.
- Cloudflare upload script for a distinct schema name (`ezkey-integration-api-exp1`).
- Downstream Integration spec copies dispatched by `update-specs.sh` (portal
  `sites/ezkey-org/api-specs/integration-api-openapi.json`).
- Operator runbook and one Bruno negative probe through the EXP1 Integration hostname.
- Bruno environment `exp1` (`base_url_integration_api` already present).

## Out of scope

- Admin API Cloudflare upload or Block.
- Copying EXP1 Auth **Block** as the Integration default. Product default remains **None**.
  Ask the operator before Set action.
- Lightsail / Wrangler coupling.
- Fallthrough “block unknown endpoints” WAF rule.
- New Cursor skill or rule.
- A second OpenAPI lifecycle idea (`I-*` / `V-*`).
- Treating localized specs as versioned release artifacts.
- Hand-editing generated OpenAPI under `specs/` or dispatch copies.

## First executable slice

1. Extend `update-specs.sh` so Integration uses the same `jq 'del(.servers)'` normalize as Auth
   (`jq` required).
2. Add `scripts/package-integration-api-cloudflare-schema.sh` modeled on the Auth packager: one
   EXP1 server, OAS 3.0 downlevel, `--self-test`.
3. Add `scripts/cloudflare/upload-integration-api-schema-exp1.sh` modeled on the Auth uploader
   (`--list` / `--upload` / `--delete`). Same `CLOUDFLARE_API_SHIELD_TOKEN` rules. Do not delete
   the Auth schema.
4. Document the operator workflow and origin vs Cloudflare 1020 distinction.
5. Add one Bruno negative probe (`enrollmentId` as string on create). Do not add it to
   `bruno-health`.
6. Refresh the canonical Integration spec only through `./scripts/update-specs.sh
   --integration-only` when a stack is available. No Java/OpenAPI annotation changes in this
   slice.

Optional same-PR (dropped if it grows): Admin host-neutral `servers` strip only, no Admin
Cloudflare upload. This slice keeps Admin out.

## Rollback or fallback posture

- If Cloudflare rejects the packaged artifact, extend only the packaging downlevel (same Auth
  lesson: OAS 3.0 / error 50010). Do not change the canonical 3.1 spec.
- If a false Block appears, Set action stays **None** (escape hatch). Scripts never change
  mitigation.
- If Integration normalize causes unexpected portal-copy drift, keep Auth as-is and pause
  further API rollout.
- Do not restore localhost / EXP1 hosts into Integration `@Server` annotations (none exist
  today). Scripted `del(.servers)` remains the real gate.

## Critical flows

- **Canonical refresh path:** local Integration API emits `/api-docs`;
  `scripts/update-specs.sh --integration-only` fetches it, strips top-level `servers`, validates
  JSON, and dispatches the host-neutral spec to `sites/ezkey-org/api-specs/`.
- **Deployment localization path:** packaging reads the canonical spec and writes
  `specs/integration-api/deployments/exp1-cloudflare-openapi.json` with exactly one server:
  `https://exp1-integration-api.ezkey.org`.
- **Cloudflare manual validation path:** operator uploads the localized spec and confirms each
  Integration endpoint above is listed once under `exp1-integration-api.ezkey.org`. Product
  default mitigation is **None**. Do not copy Auth EXP1 Block without an explicit operator
  decision for this hostname.
- **Consumer safety path:** Java SDK (`EzkeyClient.baseUrl`) and the TypeScript Integration SDK
  (sibling repo) keep using runtime base URL, not canonical `servers`. Portal copy must stay
  host-neutral after normalize.

## Evidence plan

- Canonical spec check: `specs/integration-api/openapi-spec.json` has no top-level `servers`
  after refresh.
- Dispatch check: `sites/ezkey-org/api-specs/integration-api-openapi.json` matches the
  normalized canonical spec.
- Localization check: generated EXP1 artifact has exactly one server URL,
  `https://exp1-integration-api.ezkey.org`, and the three paths above once.
- Cloudflare OAS 3.0 check: packaging downlevels `type` arrays and sets `openapi: 3.0.3`.
- Packaging `--self-test` on a fixture.
- Documentation check: operator runbook indexed from `docs/cloudflare/README.md` and
  `scripts/README.md`.
- Cloudflare `--list`: new schema present; Auth schema
  `9d7d279b-9eff-4661-b536-8afb5a9b6dbf` (`exp1-cloudflare-openapi.json` / Auth name) remains.
- Operator upload + Set action: ask before changing mitigation; recommend **None**.
- Bruno negative probe through the Cloudflare hostname (403 / 1020 only when Block is on).
- Positive path when credentials exist: schema-valid create that fails binding is origin
  ProblemDetail (`https://ezkey.io/problems/…`), not 1020.

## Test strategy

- **Minimum checks:** packaging `--self-test`, live package assertions, no-`servers` canonical
  assertion after scripted refresh.
- **Operational/manual check:** `--list`, operator upload, Set action decision, Bruno probe.
- **Rejected for this slice:** Playwright UI tests (no Admin UI change). Maven baseline (no
  Java change). `bruno-health` (probe is not a health suite).

## Quality gates

- **Contract gate:** endpoint paths, request/response schemas, tags, and security schemes remain
  unchanged except for host metadata packaging.
- **Self-hosting gate:** no deployment-specific host is introduced in Java annotations or
  canonical repository specs.
- **Packaging gate:** generated deployment-localized specs are deterministic and independently
  runnable outside Lightsail deployment.
- **Operational gate:** scripts do not change Block / None. Product default is None.

## Traceability updates required at implementation closeout

- Update this `TB-*` with implementation outcome and evidence.
- Update `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`: Integration slice done
  or in-progress; Admin still deferred pending analysis (plan size, body-inspection limit,
  contract volatility).
- Update `TB-2026-06-02-auth-api-cloudflare-schema-next-phase.md` and
  `V-2026-06-02-openapi-spec-lifecycle`.
- Do not invent a new `I-*` / `V-*`.

## Exit criteria

`TB-2026-08-23-integration-api-cloudflare-schema` is validated when:

1. the canonical Integration API spec is host-neutral;
2. the portal dispatch copy is host-neutral;
3. the EXP1 Cloudflare artifact has one and only one target server and the three operations
   above;
4. packaging `--self-test` passes;
5. `--list` shows the new schema and the Auth schema remains;
6. the operator has uploaded the artifact and confirmed the three operations once under
   `exp1-integration-api.ezkey.org`;
7. Set action is an explicit operator decision (EXP1 Integration: **Block**, maintainer-only);
8. parent `I-*` / next-phase notes record Integration done and Admin still deferred.

## Implementation outcome (2026-08-23)

Packaging, docs, host-neutral refresh, EXP1 upload, and Set action landed on branch
`tb/2026-08-23-integration-api-cloudflare-schema`. Mitigation on Integration EXP1 is **Block**
(maintainer-only exception, same reason as Auth).

| Criterion | Evidence |
| --------- | -------- |
| Canonical Integration spec is host-neutral | `./scripts/update-specs.sh --integration-only` against local Integration API; `servers` block removed only (no contract drift) |
| Portal copy is host-neutral | `sites/ezkey-org/api-specs/integration-api-openapi.json` matches the normalized canonical spec |
| EXP1 artifact has one server | `./scripts/package-integration-api-cloudflare-schema.sh` writes `https://exp1-integration-api.ezkey.org` |
| Cloudflare OAS 3.0 | Packaging downlevels 3.1 `type` arrays and sets `openapi: 3.0.3` |
| Paths once | `POST /api/v1/auth-attempts`, `GET /api/v1/auth-attempts/{id}/wait`, `POST /api/v1/auth-attempts/{id}/cancel` |
| Packaging `--self-test` | passed |
| Docs | [`docs/cloudflare/integration-api-schema-validation.md`](../../../docs/cloudflare/integration-api-schema-validation.md) |
| EXP1 `--list` | Auth `9d7d279b-9eff-4661-b536-8afb5a9b6dbf` remains; Integration `bf5cab36-d09c-4b0e-b551-5c23bc17682f` (`ezkey-integration-api-exp1`) enabled |
| EXP1 operations once | `POST /api/v1/auth-attempts`, `GET …/{id}/wait`, `POST …/{id}/cancel` under `exp1-integration-api.ezkey.org`, mitigation **block**, bound to the Integration schema |
| EXP1 Set action | **Block** (2026-08-23 operator exception: no external EXP1 traffic; maintainer-only). Zone default stays None. Not the product default. |
| EXP1 negative probe | `enrollmentId` string → Cloudflare **403 / 1020** (`error code: 1020`). No `via: Caddy`. |
| EXP1 positive shape | schema-valid create with dummy Basic → origin **401** (`via: 1.1 Caddy`). Request reached Spring; Cloudflare did not 1020. |

Notes:

- EXP1 Integration **Block** (2026-08-23) is the same maintainer-only exception as Auth.
  Escape hatch is None. Do not treat Block as the default for Admin or a later public host.
- After upload, schema operations must be added to Web Assets before Block applies. The
  upload script does not change mitigation.
- Java SDK injects `baseUrl` at runtime. TypeScript Integration SDK lives in the sibling
  `ezkey-sdk-typescript` repo and is not dispatched by `update-specs.sh`.
- Admin host-neutral strip was left out of this slice (optional cheap add; would expand
  dispatch-copy review).

## Closeout (2026-08-23 / canon synced 2026-08-25)

Exit criteria met on merge of PR `#479`. EXP1 Integration schema validation is live with Set
action **Block** (maintainer-only exception, same reason as Auth). Parent `I-2026-06-02` stays
`active`; Admin Cloudflare remains deferred.
