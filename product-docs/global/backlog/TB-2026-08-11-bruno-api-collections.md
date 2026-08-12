# Tracer Bullet Brief — `TB-2026-08-11-bruno-api-collections` Postman → Bruno cutover

## Metadata

- **ID:** `TB-2026-08-11-bruno-api-collections`
- **Status:** `under-review`
- **Related idea:** none (Plan-mode challenge completed in-session)
- **GitHub issue:** none
- **GitHub branch:** `chore/bruno-api-collections`
- **GitHub PR:** none
- **Created at:** `2026-08-11`
- **Updated at:** `2026-08-11`
- **Captured by:** Marc / agent

## Objective

Replace git-exported Postman Collection v2.1 JSON with a single git-native Bruno collection under
`bruno/`, preserve env-chained Admin/Auth/Integration/Crypto exploratory flows, pin
`@usebruno/cli` for migration health, and rewrite AGENTS/didactic contracts accordingly.

## Boundaries in scope

- Convert 17 Postman collections (~138 requests) + 3 environments into `bruno/`
- Pin `@usebruno/cli` 4.0.0 (+ converters/filestore for one-shot import) via **local npm**
- Ship `scripts/bruno-health.sh` (G0 suite green on clean-start Crypto + public endpoints)
- Docs/contract cutover: root `AGENTS.md`, admin-api/crypto AGENTS, didactic parity, CONTRIBUTING,
  LOCAL_STACK_PORTS, MOBILE_DEVELOPER_GUIDE, Admin API product-docs mappings
- Remove `postman/` and stale `Requestly/` after conversion

## Out of scope

- Replacing `ezkey-tests` RestAssured as contractual E2E
- Mandatory GitHub Actions job for `bru run`
- OpenAPI-driven regeneration of exploratory scripts
- Bruno Ultimate edition features

## Validation evidence

| Gate | Result |
|------|--------|
| G0 (`./scripts/bruno-health.sh --suite g0`) | PASS against live Crypto + public instance-info |
| G4 catalog smoke | PASS (8/8) with `EZKEY_ADMIN_TOKEN` after collection Bearer + pagination/dashboard assertion fixes |
| G2 golden device chain | Still optional / operator Demo Device path beyond health CD |

## Agent director prompt (resume)

> Migrate Ezkey from `postman/` to a single git-native Bruno collection at `bruno/`. Source:
> existing Collection v2.1 JSON via `@usebruno/converters` + filestore (`format: bru`). Preserve
> env-chained Admin/Auth/Integration/Crypto exploratory flows and didactic numbered request parity.
> OSS Bruno only; pin `@usebruno/cli` under `bruno/` with **local npm** (not pnpm/Yarn). Ship
> `scripts/bruno-health.sh` and use it as primary migration health proof. Update AGENTS/didactic/docs;
> delete `postman/` and `Requestly/` when G0 green. Do not hand-edit OpenAPI specs. Do not weaken
> `ezkey-tests`.

## Closeout notes

- Bruno CLI 4 dropped `bru import postman`; conversion uses `@usebruno/converters` +
  `@usebruno/filestore` with `{ format: "bru" }` (default OpenCollection YAML is not CLI-runnable).
  Re-import helper: `scripts/bruno-import-from-postman.js`.
- Pre-request body builders must use `req.setBody(...)`, not `req.getBody().raw = ...`.
- Install contract documented in `bruno/README.md`: Node LTS ≥ 18, local `npm install`, `npx bru`.
- G0 CLI health green; G2/G4 left for Demo Device / bearer token when the operator has a full stack.
