# Hardcoded values curated

Mandate-driven look at hardcoded literals and magic numbers. Weight class:
**`assessment-curated` instance** (same family as
[`java-transactional-boundaries/`](../java-transactional-boundaries/)). Tools alone shortlist this
badly, so there is no doctor script and no `AGENTS.md` keyword. Invoke it with an
`assessment-curated` mandate that points at this folder.

Index: [`../README.md`](../README.md). Method canon:
[`../assessment-curated/README.md`](../assessment-curated/README.md).

## Posture

**This is not a witch hunt.** The default outcome is **keep the value in code**, with a one-line
comment when that helps discoverability. Externalization is the **exception**. It needs
discoverability **and** a real product intent (a credible requester, a use case, an issue). It is
never dogmatic adherence to "no magic numbers". Avoid accidental complexity. Keep the report small.

**Expected externalization rate: low.** A small minority of a shortlist (target ≤ 10–15 %). The
first pass shortlisted 12 rows; re-checking the one candidate left 0 rows with a requester for a
different value ([`2026-11-17-pass-1.md`](2026-11-17-pass-1.md)). Most operator-relevant knobs are
already `@ConfigurationProperties` with validation and docs. What remains is mostly coherence and
discoverability.

Patrick's question, verbatim: « **qui a une raison légitime de vouloir une autre valeur ?** »
No credible requester means no property.

Edgar's deployment rule, verbatim: « **Une valeur externalisée ne change jamais le comportement
d'une instance déjà installée.** »

## Canon this method applies

No new product principle. The lines below are the existing posture, re-checked on `main` @
`392392ec` (2026-11-17):

- [`design-principles.md`](../../design-principles.md) line 17 (principle #1): "Add accidental
  complexity only when it buys something observable."
- [`design-principles.md`](../../design-principles.md) line 21 (principle #2): "If an abstraction
  does not change outcomes, it does not belong."
- [`design-principles.md`](../../design-principles.md) lines 69–76: principle #14, "Beautiful
  problems (defer sophistication until earned)".
- [`CONTRIBUTING.md`](../../../../CONTRIBUTING.md) line 14 and [`AGENTS.md`](../../../../AGENTS.md)
  line 269: "Accidental complexity (extra indirection, unnecessary abstraction) must be minimised
  to keep maintenance and evolution manageable."
- [`TB-2026-08-29-admin-api-remove-m2m-hatch.md`](../../backlog/TB-2026-08-29-admin-api-remove-m2m-hatch.md)
  line 87: "**Do not** keep the property “just in case” for a local compatibility artifact."

A verbatim rule "do not create a parameter if it brings no value" was **not found** elsewhere.
This lane states it once: do not add a configuration parameter that brings no value. A parameter
needs a credible requester, a use case, and an issue. That sentence stays here. It is not copied
into `AGENTS.md`.

The code-review checklist line that said "All configurable values use environment variables"
([`cr.md`](../../../../.cursor/rules/commands/cr.md)) is reworded to this posture: values an
operator has a legitimate reason to change are externalized; other values stay in code.

## Scope

**In:** Java backend modules (core, core-security, admin-api, auth-api, integration-api,
crypto-api, connectors), Flyway migrations, Admin UI TypeScript, and Ezkey Mobile where a value
mirrors a backend contract.

**Out:** tests and fixtures, generated code (OpenAPI clients, `specs/`), demos and lab
(`ezkey-demo-*`, `ezkey-jenkins`, `didactic`, `experimental-hybrid`), i18n, licenses.

**Excluded lane:** PAM (`ezkey-pam` and its CI) is owned by Edgar and Mathieu (Marc, 2026-10-27).
No inventory.

**Crypto parameters** (key sizes, iterations, algorithms, key age, keyset limits) are flagged
**hand to Christophe**. They are never proposed for externalization by default.

## Rules per constant

0. **Default is keep.** Apply Patrick's question. No credible requester means no property.
1. **Trace before judging.** Find the role, `git blame` / `git log`, the PR, the issue, and the
   product intent (vision, ADR, TB), plus the real-world context (Cloudflare Free 100 s edge,
   community host, HA).
2. **Keeping is often right.** When the value is not obvious, add a one-line comment with the why
   and the issue or PR, where the value lives.
3. **Externalize only with a requester.** Use typed, validated `@ConfigurationProperties`
   (`@Min` / `@Max`, or a clamp). Keep metadata generated or in
   `META-INF/additional-spring-configuration-metadata.json`. The default must equal current
   behavior. Add one line in the module `CONFIGURATION.md` and in `docs/configuration/README.md`.
   Admin UI / mobile equivalent: a named module constant, or a value served by the API (one source).
4. **Deployment impact.** Apply Edgar's rule and the checklist below to every externalization
   candidate.
5. **Best gain, lowest cost.** Weigh scope, risk, and deployment complexity. One change at a time.
   No regression, no accidental complexity, no catch-all `Constants` class.
6. **Owners.** Security properties go to Christophe (a relaxed default is a blocker). Product
   defaults go to Alex. Behavior changes need Marc's go. An agent's approval never counts as Marc's.

## Triage grid

« Qui a une raison légitime de vouloir une autre valeur ? »

Externalize only when there is a credible requester. Anti-dogme : on externalise seulement s'il y
a un demandeur crédible.

| Answer | Disposition |
| --- | --- |
| Personne, invariante de domaine ou de protocole | **keep** as-is |
| Personne, mais la valeur n'est pas évidente | **keep + comment**: one line, why + issue/PR |
| Le dev, quand le littéral est dupliqué ou porte un sens métier (TTL, plafond, lot) | **named constant** near its owner; no catch-all `Constants` |
| L'opérateur ou l'adopteur, avec un cas réel (rétention, horizon, timeout, rate limit, cron) | **externalize**: typed `@ConfigurationProperties`, validated, generated metadata, default identical, one config-doc line |
| Java↔SQL/Flyway duplicate | **coherence test** rather than config |
| Backend↔Admin UI duplicate | **one source** exposed by the API |

**Strong signals:** security TTLs; network timeouts and retries; caps (rate limit, page, batch,
horizons); crons; literals duplicated across modules or between Java and SQL/Flyway; literals
copied between the backend and the Admin UI.

**Noise:** `0`, `1`, and `-1`; indices; tests and fixtures; log text; standard buffers; HTTP codes
already in an enum; single-use values already named. Also: annotations that are API docs, enum
ordinals, i18n, licenses, generated code.

**Guardrails:**

- One change at a time, with a characterization test before extraction.
- Fail fast at startup, never fail-open.
- Security properties go through Christophe; product defaults through Alex.
- No "au cas où" property: each one cites its use case and issue.
- No value duplicated across profiles.
- A durable decision adds one line to the owning doc, in the same PR.
- The report ranks and recommends; lots stay small and separate.

**Zone order:**

1. **(e)** Java↔SQL literals (coherence test).
2. **(a)** Auth and onboarding TTLs (auth attempts, challenges, proof tokens, admin activation codes).
3. **(b)** Schedulers and retention (partition horizon and clamp, crons, the #743 8-day cap, purge, ShedLock).
4. **(c)** Connectors and network (SMTP, HTTP, PAM delays; Cloudflare's 100 s cap).
5. **(d)** API pagination and limits, with their copies in the UI.

Start with **(e)** and **(a)**, read and rank, then **(b)**.

## Deployment checklist

For every externalizing PR:

1. The default is exactly the old value, proven by a test or a PR note. An upgrade that does not
   touch `.env` keeps the same behavior.
2. The key is documented in `.env.example` (and the compose overlay, when there is one): env var,
   default, service, and whether a restart is enough or the container must be recreated. Spring
   config is read only at JVM start.
3. Add a "Nouvelles options (facultatives)" line to the release note, or to
   `docs/lightsail/community/DEPLOYED.md` or `docs/lightsail/community-host.md`. No mandatory key
   without a default. A mandatory key without a default is a breaking change and needs an explicit
   runbook step.
4. Add an optional recommendation for the existing install only when it has real value. The
   operator applies it with Marc's go.
5. No secret as a default. A secret lives in a separate env file in mode `600`, never in the
   versioned `.env`.

**Runbook.** Before each upgrade, Edgar diffs `.env.example` keys between the deployed commit and
the target commit, and lists new keys (default kept, or an adjustment suggested). A cloud agent may
add these boxes to the community-host runbook, on Marc's go.

## Tooling

Signals, never gates. Record counts before and after filtering in each pass note.

- **ripgrep** (primary): inline durations; `@Scheduled` without `${…}`; named TTL / timeout / cap
  constants; Flyway `DEFAULT` / `INTERVAL` / `CHECK` / `VARCHAR(n)`; `ezkey.*` keys with no Java
  reader (orphans); Admin UI `size` / `staleTime` / `max` literals. The read-only commands are in
  the pass note. This lane does not add a doctor script. A later helper, if one is extracted, is
  read-only `rg` at `scripts/hygiene/hardcoded-values-scan.sh`.
- **Checkstyle `MagicNumber`** is configured
  (`checkstyle-config/src/main/resources/checkstyle.xml` line 198) and suppressed globally
  (`checkstyle-suppressions.xml` line 113, since `57941b95`, 2025-11-03). Keep the suppression. A
  pass may run once with a temporary suppressions file for a raw count. Do not unsuppress in CI.
- **PMD / Semgrep:** optional, through `config/java-doctor/`, only if a rule earns its keep. No new
  ruleset by default.
- **ESLint `no-magic-numbers`** (Admin UI, mobile): one-off CLI with
  `ignore: [-1, 0, 1, 2]`, `ignoreArrayIndexes: true`, `enforceConst: true`. Not added to the
  config.
- **History:** `git blame -L`, `git log -S`, then the PR or issue.

## Pass notes

Copy [`TEMPLATE.md`](TEMPLATE.md) to `YYYY-MM-DD-pass-N.md`. First instance:
[`2026-11-17-pass-1.md`](2026-11-17-pass-1.md).

## Cadence

- **Now:** one initial pass (shortlist of 12), then HITL one lot at a time, simplest first. Lots
  1–5 in [#1393](https://github.com/mgagp/ezkey/issues/1393) are later PRs. This folder does not
  implement them.
- **Afterwards:** on mandate, plus at most a quarterly delta (new literals since the last pass SHA,
  via `git diff`), aligned with the quarterly document-hygiene slot. Quiet when nothing is new: no
  note, no message.
- **Why this slot:** low churn, rare externalization, and a cost dominated by reading history. A
  weekly or biweekly slot would produce noise and pressure to change values that should stay. Not
  a CI gate, and not a zero-warning campaign.

Repo-is-memory line: [`../README.md`](../README.md) § Cadence & automation (Marc, 2026-11-17).
