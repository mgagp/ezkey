# Didactic protocol lab — agent notes

This folder hosts **English-only** reproducible protocol walkthroughs and tooling. It is **not** part of the public `sites/ezkey-org/` article pipeline unless content is deliberately promoted later.

## Authority order

When describing canonical payloads or field semantics, cite in this order:

1. [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md)
2. [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)
3. [`docs/MOBILE_DEVELOPER_GUIDE.md`](../docs/MOBILE_DEVELOPER_GUIDE.md), [`docs/ENDPOINT.md`](../docs/ENDPOINT.md)

Bruno folders under [`bruno/`](../bruno/) are the **parity reference** for request ordering; parity is summarized in [`BRUNO_PARITY.md`](BRUNO_PARITY.md).

## Secrets and generated output

- **Never commit** real admin bearer tokens, API keys, or private keys. Use scenario `run.yaml` with placeholders and override via environment variables (see [`README.md`](README.md)).
- Generated `artifacts/` directories are **ignored by git** (see `didactic/.gitignore`).
- **Clean-start article generation:** render Markdown with **`--full-transcript`** (same as `--publish-secrets`) so the document shows **complete** protocol material (tokens, PKCS#8 material, signatures)—appropriate only for **local disposable** labs.
- When sharing traces **outside** this machine, run **`protocol_lab redact`** or re-render **without** `--full-transcript`.

## Scenario implemented by [`templates/article-full.md`](templates/article-full.md)

**In scope:** an operator with **Admin Bearer** credentials creates **integration-scoped enrollments** and **authentication attempts** via **Admin API**; a **simulated device** completes **bind, verify, pending, respond** on **Auth API**. That is the **end-user enrollment + MFA protocol** for an integration, exercised from an **administrator** posture.

**Explicitly out of scope for the HTTP transcript:** the **passwordless admin login** used only to obtain `EZKEY_ADMIN_TOKEN` (Demo Device approval for e.g. `tuteur`)—bootstrap only, not enrollment rows in the article body.

**Future / separate template:** integration provisioning with **API keys** and enrollment flows driven primarily by **integration credentials**—see [`FUTURE_SCENARIOS.md`](FUTURE_SCENARIOS.md).

## Tooling stack

Python 3 under [`protocol_lab/`](protocol_lab/) using `requests`; optional YAML via PyYAML.

## Didactic naming (generated articles, `run.yaml`, free-text fields)

### Enrollments — **Alice & Bob (and friends)**

Use **person-like** display names so integrators read **end-user / roster** semantics, not generic lab tokens.

**Default theme:** the usual **cryptography cast** — **Alice**, **Bob**, **Carol**, and (when you need more rows) **Dave**, **Eve**, **Mallory**, etc. Pair with ordinary **surnames** for a realistic roster line, e.g. **Alice Chen**, **Bob Okonkwo**, **Carol Martins**.

This stays clearly **didactic** (same tradition as security papers and RFC-style explanations) and avoids tying examples to third-party fictional franchises.

### Descriptions, notes, and “product copy” — **astronomy** (public-domain flavor)

For **`description`**, run comments, optional narrative blurbs, or any **short realistic prose** that is *not* a legal person name, prefer a **neutral astronomy / space exploration** vocabulary: **moons**, **planets**, **constellations**, famous **missions** or **hardware** in generic terms (e.g. *relay console for the ops team managing the Europa rollout*, *staging review before TRL handoff* — adjust register to match Admin vs integrator tone).

Keep it **plain language** and **non-marketing**; do not copy slogans from agencies or entertainment. The goal is a **coherent lab voice**: people = **Alice/Bob/Carol**; everything else descriptive = **astronomy-inspired** metaphor, still readable as internal IT/integration text.

### Integration records (outside this tool)

When you create an integration via Admin API for a lab, use **operator-realistic** names and **descriptions** (see [`README.md`](README.md)). Detailed **integration-first/API-key** narratives are a **separate** article track — [`FUTURE_SCENARIOS.md`](FUTURE_SCENARIOS.md).

Avoid opaque strings such as `First didactic protocol article instance` when the narrative target is **people + products**.

## Article template — editorial contract (reader-facing)

These rules apply to [`templates/article-full.md`](templates/article-full.md) and any rendered instance:

1. **Stand-alone publication:** generated Markdown must **not** depend on Ezkey-internal documentation URLs or filenames. Canonical rules live **in prose** beside the payloads and in the verbatim helper inputs/outputs reproduced in artifacts. Maintainers may optionally cross-link private specs when authoring **outside** the exported article.
2. **Value-add is cryptographic narrative:** raw request/response dumps alone are insufficient. Each major section should state **who acts**, **what canonical string is signed**, **which key**, and **why verification matters**, using wording **self-contained with the transcripts**—not “see annex X”.
3. **Crypto API vs mobile:** state clearly that **Crypto API** is a **lab oracle** for canonical strings and test signing; the **production mobile app** performs the same operations **on-device**. Do not blur this into “the app calls Crypto API” without that qualification.
4. **TTL / timing:** introduce **auth-attempt time budget** only where it matters—immediately **before** the MFA chapter (create auth attempt → pending → respond), not in the opening scope, unless you add an outline that already orients the reader.
5. **Outline early:** the full template includes a **step table** after scope so readers see the whole choreography before deep dives.
6. **Render for local lab articles:** use **`python -m protocol_lab render ... --full-transcript`** so signatures and keys are **not redacted** in the Markdown (clean-start disposable output only).
7. **Templates are the shipped reader voice:** do not stash “author leftovers” in [`templates/article-full.md`](templates/article-full.md) or [`templates/article-thin.md`](templates/article-thin.md)—see **Author-only content (not for exported prose)** below. Post-render tinkering stays **outside** Ezkey automation unless an operator edits a Markdown copy by hand after export.

### Author-only content (not for exported prose)

Keep tooling, naming, `--full-transcript`, and checklist language **here** (`AGENTS.md`, `README.md`)—never in wording that survives `protocol_lab render` when the Markdown is treated as publishable documentation.

Avoid in templates (and purge if drift appears):

- Phrases inviting a **later edit** (“you may tighten…”, “optional polish”, “after generation”).
- **Meta-staging:** “what this template implements”, “the template adds static explanation”, “if you only skim one box…”.
- **Author-facing field glosses** (“should read as…”, “Field focus…” as checklist items)—exported prose should explain fields **declaratively** (“`integrationId` scopes…”) for readers.
- **Thin-template operator blocks:** “Naming cues for regenerated instances”, CLI flags—that belongs above in **Didactic naming** and **Secrets and generated output**.

## Delegated “article instance” workflow (operator → agent)

Use this when asking an agent to produce one **filled** Markdown instance from [`templates/article-full.md`](templates/article-full.md) (or thin) plus a reproducible **`run.yaml`** slice.

### What the agent can do autonomously (once stack + inputs are valid)

From repo root / `didactic/`:

1. Create or adjust a scenario folder under [`scenarios/`](scenarios/) (e.g. `scenarios/<slug>/`).
2. Copy [`scenarios/cleanstart-example/run.example.yaml`](scenarios/cleanstart-example/run.example.yaml) or `.reject` variant → `run.yaml` (gitignored pattern; see [`README.md`](README.md)).
3. Run `python -m protocol_lab run --config … --artifacts-dir …` (non–dry-run) and fail fast with HTTP errors surfaced.
4. Run `python -m protocol_lab render ... --full-transcript` → e.g. `artifacts/article.<slug>.generated.md`.
5. Optionally `redact` for shareable `steps.redacted.jsonl`.
6. Report `summary.json` **auth-attempt TTL** and any notes (empty respond signature paths, etc.).

### Optional bootstrap — admin Bearer via passwordless (reference admin `tuteur` + Demo Device)

For a clean-start stack without pasting secrets into chat:

1. Use a known **global admin username** enrolled on the Demo Device. The Ezkey **`username`** must match provisioning **exactly** (case-sensitive in practice). Reference didactic deployments use **`tuteur`** (“Tuteur” in conversation may map to lowercase in the DB/UI).
2. **`POST`** `/api/v1/admin/auth/login` on Admin API (see [`docs/ENDPOINT.md`](../docs/ENDPOINT.md)) with e.g. `username: "tuteur"`, `challengeRequested: false`, `nonBlocking: false` (Bruno folder: [`bruno/authentication-login-admin/`](../bruno/authentication-login-admin/)). The call **blocks** until the operator approves on the Demo Device; on success the JSON includes **`token`**.
3. Export that value as **`EZKEY_ADMIN_TOKEN`** for `protocol_lab run` (never commit it; do not echo it in logs or PR text).

Alternate **non-blocking** pattern: `nonBlocking: true` on login, then **`POST /api/v1/admin/auth/passwordless-wait`** with `authAttemptId` (same Bruno folder). Matches UI countdown / long-poll ergonomics.

The agent may ask the operator explicitly: **“Approve the pending admin login for `tuteur` on the Demo Device.”** before or while the blocking request runs.

Environment override (optional): set **`EZKEY_ADMIN_LOGIN_USERNAME=tuteur`** when a script wraps login (not required by `protocol_lab` today).

### What the agent must **not** guess

- **`integration_id`** the operator’s admin can use (tenant scope).
- **Admin bearer token** (use `EZKEY_ADMIN_TOKEN` or bootstrap above; never commit).
- Whether the scenario should be **accept** vs **reject** (`scenario.respond_accepted`) and whether **auth challenge** is required (`scenario.auth_challenge_requested` / enrollment flag).
- **Output naming** (`<slug>`, locale, thin vs full) if the operator cares for filing or handoff.

### Minimal briefing the operator should give (copy-paste checklist)

1. **Slug** for the instance (kebab-case), e.g. `cleanstart-demo-accept-2026-05-09`.
2. **`integration_id`** (integer).
3. **Branch / variant**: accept | reject | (optional) challenge path.
4. **Template**: `article-full.md` | `article-thin.md`.
5. **URLs** only if non-default (`base_urls.*`); else assume clean-start localhost ports from [`README.md`](README.md).
6. **Token delivery**: env var on the machine running the agent, or explicit “use existing `run.yaml`” if already present.
7. **Full transcript for lab (default expectation):** use **`--full-transcript`** on `render` for clean-start disposable articles; skip only when producing share-safe excerpts.

### Clarifying questions the agent should ask if anything is missing

- Which **integration** should enrollments attach to (id)?
- **Approve or deny** respond for this article instance?
- **2-digit auth challenge** path needed, or keep `challengeRequested: false`?
- Desired **output path** for generated Markdown (under `artifacts/` recommended).
- Should **`admin.auth_attempt_wait`** run (default on) or `--skip-admin-wait` for speed?

### Skill vs this file

- **This `AGENTS.md`** is the versioned, repo-local contract: enough for repeatable delegation inside Ezkey.
- A **Cursor user skill** is optional: use only if you want the same checklist outside this repo or without opening `didactic/AGENTS.md`.
