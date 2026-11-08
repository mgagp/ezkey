# Executable intent and mutation testing — research draft

**Status:** draft record of research to date. Not a finished audit, not a tool adoption, and not a new gate.

| | |
| --- | --- |
| Captured by | Marc Gagnon (dictation, 2026-11-08) |
| Base inspected | `275c5ea8a2070a9ccdbc0da8e874936a0d55aec7` on `main` |
| Method | File and primary-source reading. No test suite, build, mutation tool, or stack was executed. |
| Source | [Appendix](appendix-source-intention.md) — quasi-verbatim dictation plus the same-day clarification |
| Tracker | One framing issue, two axes. URL is recorded in the draft pull request when the tracker accepts the create. Opening that issue does not adopt a practice. |

This note follows the existing documentary habit: product research stays under `product-docs/global/`, linked from the test-strategy entry, and does not change the published methodology pack. Design principle 8 already says behavior is specified before it is implemented and mapped to tests. This draft does not add a second principle.

## How to read the chain

```text
dictated source  →  clarified human intention  →  named example or invariant (proposal)
        →  test that can fail  →  gate that actually runs
```

Do not turn each dictated sentence into a test. The appendix feeds the compass. A named example or invariant is what can become executable. Contradictions stay visible. Human decisions stay distinct from agent suggestions.

The same-day clarification prevails on ablation, regrouping, and simplification. That topic is a secondary, consultative vigilance: ask before removing anything, because deletion can lose information. It is not an objective of this note. Nothing is deleted here. Any later deletion waits for Marc.

## What was opened, and what was not

Opened for this draft:

- Root `AGENTS.md` cold-start and working conventions, `product-docs/methodology/README.md`, `product-docs/global/README.md`, `test-strategy.md`, `spec-test-traceability.md`, `design-principles.md` (principles 3, 4, 8, 9, 12).
- Published essays, at heading and opening level: [Le retour aux sources](../../../../sites/ezkey-org/fr/retour-aux-sources-developpeur-ere-ia.html) (13 May 2026), [Stratégie de test](../../../../sites/ezkey-org/fr/strategie-de-test.html) (April 2026), [Piloter par l'intention](../../../../sites/ezkey-org/fr/steering-by-intention-closing-the-loop.html) (20 September 2026).
- CI workflow `.github/workflows/ci.yml`, mobile reusable workflow, `ezkey-tests/pom.xml` Surefire groups, `product-docs/global/hygiene/quality-gate/README.md`, `ezkey_mobile/docs/MOBILE_TEST_STRATEGY.md`, `ezkey_mobile/maestro/README.md`, Admin UI Playwright specs.
- Package-level counts of `*Test.java`, Admin UI test files, mobile Jest files, Maestro flow files. Assertion quality was sampled, not read line by line.
- Primary pages listed under Sources. Numeric claims below are limited to what those pages support.

Not re-read, so not treated as evidence here: the full text of every method log under `product-docs/global/backlog/method-logs/`, the essay `sites/ezkey-org/fr/l-intention-prochaine-frontiere-partenariat-humain-ia.html` (present, not opened), BDD / Specification by Example / property-based primary pages, Arcmutate docs, and a systematic literature review. "Le retour aux sources" and the two retrospectives above were opened. The others are only known to exist.

## Axis A — from lived intention to a failing check

### Practices: established, emergent, or not shown

| Topic | How this draft treats it | Limit |
| --- | --- | --- |
| TDD, and BDD as a way to state examples of behavior | Established background. The dictation's "fusion" is the speaker's report of conferences, not a new method. BDD grew as a way to express examples of behavior; this draft did not reopen Dan North or Adzic. | Conferences in the dictation stay unidentified. No "industry consensus" is claimed. |
| Specification by Example, property-based tests | Established families. Not reopened from primary pages here. No Gherkin framework is proposed. | A property library is an option for a later decision, not a finding that Ezkey lacks one. |
| Mutation score as a measure of whether tests detect changes | Established. It does not say whether the tests encode the human intention. | See axis B. |
| Concern-guided mutation with an LLM (Meta ACH, 2025) | One published industrial deployment. Promising for "never do X". Not a replicated result in this draft. | No systematic search for replications was done. Absence of a second paper is not established. |
| Spec Kit and "spec-driven" agent workflows | Vendor essay, 2 September 2025. Real movement. The blog calls specifications living artifacts that drive generation. | That is not the same as a gate that fails when code diverges. No effectiveness study was opened. |
| "Convergence" of mutation and executable specs as one intent gate | Not demonstrated. Plausible combination. The dictation reports conferences; those talks were not identified. | Do not publish this as a settled practice. |

Two distinctions the dictation needs, without correcting it:

- A specification written so an agent can generate code is not yet an executable specification. Executable here means a check that passes or fails in a build.
- A mutation score is not validation of intention. A test can kill mutants and still lock in the wrong behavior.

### What Ezkey already carries

The documentary system already asks for spec-before-code and for a feature-to-test matrix (`design-principles.md` principle 8, `spec-test-traceability.md`). The global test strategy separates a fast unit line from slower journeys and says the repository, not a tracker, is the meaning of a case. The April 2026 test retrospective already states the same split in operator language: unit tests are the first fast defense; functional tests are the heart of the Docker-backed strategy; tags separate fast, elective, and operational compression. "Le retour aux sources" argues that analysis and design have to be living because an agent is punished when intent stays implicit. "Piloter par l'intention" adds that delegation needs an explicit, observable meaning of done.

That is already a compass. The distance is not "Ezkey has no specs and no tests". The distance is that the matrix records feature status (`implemented`, `in-progress`, `planned`) more often than a named user-lived rule tied to one check and to the gate that actually runs it. A test file present in the tree is not a pass. This draft did not execute suites.

### Interfaces, and who starts

Before judging a countdown gap, name the surfaces and the initiator.

| Surface | Who starts | What this draft saw |
| --- | --- | --- |
| Protected integrating application | The person at that screen. They type username and password and choose to authenticate with Ezkey, then turn to the phone. | Sample in-repo: ACME demo login. `challenge-wait.html` renders a countdown from the server `expiresAt`. `LoginController` also treats a past `pendingExpiresAt` as expired on the ACME side. |
| Admin UI login | The operator, when the protected application is the admin console itself. Same "person in front of the initiating screen" pattern, different role. | `ezkey-admin-ui/src/pages/login.tsx` drives a countdown from server `expiresAt`. Recovery has its own countdown. No Vitest file references `login.tsx` or `formatCountdown`. |
| Auth API and `ezkey-core` auth-attempt services | The server. Durable expiration is a backend decision (principle 3). | TTL bounds in `AuthAttemptServiceTtlTest` (default 120 seconds, reject 0 and 14, clamp above 120). Expiry scheduler test. Functional `AuthenticationFlowSecurityTest` expects `409` with a type ending in `/auth-attempt-expired` when the owner responds to an expired attempt. |
| Mobile app | The person consumes the request. The phone is not the place the dictation puts the countdown. | React Native and TypeScript, not a Kotlin application. `authApiProblem.ts` maps `auth-attempt-expired` to non-retryable copy. A Jest case covers that mapping. No `countdown` symbol in mobile TypeScript. |
| Demo Device | Lab stand-in for the phone, not the protected application. | A controller test maps a typed `409` auth-attempt-expired body to an `expired` view flag. |

**Human decision, from the dictation:** the countdown lives on the initiating screen. Do not duplicate it on the phone. Do not add clock-drift handling without a demonstrated need.

**Agent proposals, not extra words from Marc:** the server remains the single expiration truth (an expired attempt is refused on consume); the phone shows a clear refusal when the server says so, and does not grow its own countdown. Those proposals match code that already exists in part. They are not new product decisions. Naming the link in the traceability matrix is a later choice.

### Light link, not a new policy

Candidate shape, undecided:

1. Keep the dictated source discoverable (this appendix).
2. Write the clarified intention in one sentence, marked as human decision or as proposal.
3. Point at one existing test, or say that none was found.
4. Name the gate that runs that test. If the gate is on-demand, say so.

Attaching a verbatim source to an upstream doc is a candidate working habit Marc raised. It is not adopted by this draft. The appendix is linked from `test-strategy.md` so the habit can be judged on a real example.

## Axis B — mutation, as a measure of bite

Mutation changes existing code and asks whether a test notices. It does not invent a missing security check. Just et al. (FSE 2014) found a coupling effect for 73% of 357 real faults in five programs, and 17% of faults coupled to no mutant. Mutant detection correlated with real-fault detection more strongly than statement coverage did. A requirement that was never written sits in that residue, or outside mutation entirely.

At Google (ICSE-SEIP 2018), the system that engineers actually used was diff-scoped, skipped uncovered and "arid" lines, and surfaced a few mutants in code review. The ACM abstract says about 6,000 engineers, more than 13,000 authors, and about 30% of diffs that have statement coverage. The Google Research page states more than 14,000 authors. This draft did not read the paper line by line and does not claim there is no score threshold inside Google; the described mechanism is review, not a repository gate Ezkey can copy as a plugin.

Equivalent mutants are a structural limit. PIT's Maven guide tells the reader to think carefully before setting `mutationThreshold` because equivalent mutants exist. `testStrengthThreshold` is killed / (killed + survived), excluding mutants with no coverage. Integer thresholds have a documented blind spot (`thresholdPrecision` defaults to 0). Papadakis et al., ICSE 2015, report that trivial compiler equivalence detected about 30% of equivalent mutants in that C study. The often-cited ~54% figure is Java, from the later TSE paper (DOI 10.1109/TSE.2017.2684805): about 54% of known equivalent mutants in that Java benchmark were trivially equivalent. Do not cite 54% as the 2015 result.

Meta's ACH (FSE Companion 2025; Foster, Gulati, Harman, and others; arXiv 2501.12862) generates few mutants for a concern written in text, then tests that kill them. On 10,795 Android Kotlin classes it reported 9,095 mutants and 571 tests; engineers accepted 73%, and judged 36% relevant to the privacy concern. An equivalent-mutant detector reported precision 0.79 and recall 0.47 (0.95 and 0.96 with preprocessing). One industrial context. The intention is still supplied by a human.

LLM oracles often describe the code as it is. Konstantinou, Degiovanni, and Papadakis (arXiv 2410.21136; the fetched page also marks a JACM appearance) studied 24 Java repositories and report that LLM oracles mainly capture actual behavior, with accuracy under 50% in some classification settings, and better results when names are meaningful. Mathews and Nagappan (arXiv 2412.14137) evaluated Python tools on a filtered Refactory subset (287 samples, four assignments). For CoverUp, 68.1% of tests in the final suites passed on the buggy program and failed on the reference solution. That figure is that tool, on that subset, not a universal rate. The design they criticize — drop failing tests, chase coverage — can lock a bug in. For Ezkey, where an agent may write both the code and the test, a security oracle needs a source other than the implementation just produced.

### Tools next to the stack that is actually here

No PIT, Stryker, jqwik, fast-check, or Cucumber dependency was found in the repository. A few Gherkin fences exist inside `docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md`. They are examples in a document, not a runner.

| Surface | Stack verified in-tree | Tool that matches, if a later experiment is funded | Not a fit as the main story |
| --- | --- | --- | --- |
| Java APIs and `ezkey-core` | JUnit 5, Maven reactor, JDK 25. Unit tests run in the `backend` CI job with `mvn test -pl '!ezkey-tests'`. | PIT (`targetClasses`, `targetTests`, `withHistory` for a local incremental file). Default is not a failing score; a threshold is optional and PIT warns about equivalents. | A whole-reactor score gate. `ezkey-tests` is HTTP against Docker, a poor mutation target. |
| Admin UI | Vite, React, TypeScript, Vitest in the `admin-ui` CI job. | StrykerJS can scope `mutate` to a file or a line range, can run incrementally (default off), and defaults `thresholds.break` to `null` (the run does not fail the build). TypeScript checker is a documented plugin. | The configuration page opened names Jest among coverage-aware runners and does not, in that text, name Vitest. Confirm a Vitest runner before any experiment. Playwright is a journey suite, not the mutation target. |
| Mobile | React Native 0.88, TypeScript, Jest (`yarn validate:ci` in CI). Android native layer is Kotlin (crypto, appearance, locale, window security, Play update) plus JVM unit tests (`testDebugUnitTest`) and an instrumented Keystore test that CI does not run. Swift exists for the in-progress iOS layer. | StrykerJS on Jest matches the page that was opened, for a small TypeScript slice. | Treating the app as a Kotlin codebase. A commercial Kotlin mutation plugin was not opened; no license or version is stated. |

No runtime was measured. Any cost claim would be invented.

## Repository findings

Counts are files named `*Test.java`, or Jest/Playwright/Maestro files, on the base SHA. They are not coverage, not assertion quality, and not a pass.

### Inventory (file presence)

| Area | What exists | What CI runs on a normal PR | On-demand, not `ci-gate` |
| --- | --- | --- | --- |
| `ezkey-core` | 98 test files. Dense around `authattempt.service` (12), `enrollment.service` (12), `audit.integrity` (22), `security` (13). | Yes, via `backend` (`!ezkey-tests`). | — |
| `ezkey-core-security` | 9 test files for keyset, rotation, re-encryption. | Yes, same job. | — |
| `ezkey-admin-api` | 125 test files. Controllers 35, services 31, `admin.security` 9. Many DTO and exception types have no dedicated test file. | Yes. | — |
| `ezkey-auth-api` | 16 test files. Controllers: auth-attempt, enrollment, public instance info. Protocol logic lives in `ezkey-core`. DTO packages mostly have no dedicated test file. | Yes. | — |
| `ezkey-integration-api` | 15 test files, mostly the API package (14). | Yes. | — |
| `ezkey-crypto-api` | 3 test files (controller and app). DTO types have none. | Yes. | — |
| `ezkey-tests` | 49 test files. RestAssured against a live stack. Default Surefire excludes `slow`, `time-dependent`, `elective`, operational-churn, `mobile-real-device`, `gate-c1`, `connector-mock`. | The module is compiled. Its tests are excluded from the backend unit step. | `./scripts/quality-gate.sh` runs functional, then elective, then Playwright, then churn. Exit codes include GO, NO-GO, and INCONCLUSIVE. The CI job named `quality-gate` only runs helper tests of those scripts when their paths change. |
| Admin UI | 25 Vitest files under `src/`. Two Playwright specs: `e2e/admin-ui-browser.spec.ts`, `e2e/enrollment-bind-material.spec.ts`. | Vitest, typecheck, lint, production build. | Playwright, through the on-demand quality gate or `ezkey-admin-ui/scripts/run-ui-tests.sh`. Needs the stack and Demo Device. |
| Mobile | 50 Jest files (`app/services` 17, `__tests__` 16, `app/utils` 12, `app/hooks` 5). `MOBILE_TEST_STRATEGY.md` names workflow zones with one nominal case and one fail-closed case. Kotlin JVM crypto tests. Maestro flows for home, pending approve/deny/challenge, and iOS chrome. | `yarn validate:ci` and `testDebugUnitTest`. iOS lint/tests only on the heavy `mobile-ios` job. | Instrumented Keystore tests. Maestro via `ezkey-tests/scripts/run-mobile-real-device.sh` (device, excluded Surefire group). |

Adjacent surfaces that the three-priority list does not center, but that the countdown story touches: `ezkey-demo-app-acme` (20 test files) and `ezkey-demo-device` (12). Both are in the reactor and in the backend unit job.

### Sampled assertion quality

This is a sample, not a verdict that "the tests are good" or "the tests are weak".

| Rule looked for | Evidence sampled | Risk if read only as a file count | Options, undecided |
| --- | --- | --- | --- |
| Server owns auth-attempt lifetime | `AuthAttemptServiceTtlTest` asserts the 120-second clamp and the rejections. Functional test asserts `409` `/auth-attempt-expired` on respond. | TTL tests encode the numeric policy. They do not, by themselves, say where a human should see the countdown. | Leave as is. Optionally name the existing functional case in the traceability matrix. |
| Expired enrollment bind is refused | `EnrollmentExpirationSecurityTest` sets `expires_at` in the past through SQL, then expects bind rejection. | The check is real and stack-backed. It is not in PR CI. A green `ci-gate` does not include it. | Status quo (on-demand gate). Do not promote it to every PR without a measured cost. |
| Phone does not own the countdown | Mobile maps the server problem type to "This request has expired." Jest covers that mapping. No countdown symbol in mobile TypeScript. | An agent can still reintroduce a phone countdown; nothing in CI fails on the word "countdown". | A negative Jest assertion would be a small later experiment, only if Marc wants the compass executable. Not done here. |
| Initiating screen shows the countdown | Implemented on ACME wait page and Admin UI login. ACME has controller tests around expired login states. Admin UI login countdown has no Vitest file. | The operator-visible countdown can regress without a unit failure. Playwright smoke covers login shell and Demo Device approval, not the timer. | Status quo is reasonable: the server refusal is the security check; the timer is presentation on the initiator. A browser assertion is optional, not required by the dictation. |
| Signature and one-time protocol behavior | `AuthAttemptRespondServiceTest` includes a success path that expects an integration signature over the result payload, and invalid-signature cases. Enrollment signature payload tests exist. Not re-read in full. | Dense files can still assert the implementation's current return shape. | No mutation run was done, so survivors are unknown. Do not infer a score. |

DTO and exception types without a dedicated test file are recorded as inventory, not as defects. Many are covered through controllers or services, and many are not security decisions.

## Five signals

1. **The fast line and the slow line already exist, and they are not the same gate.** Unit tests for Java, Admin UI Vitest, and mobile Jest/JVM run on the path-filtered PR jobs. Functional tests, Playwright, Maestro, and the full `quality-gate.sh` sequence do not. A file in `ezkey-tests` is not a pass on `ci-gate`.
2. **The countdown compass is already partly implemented, across surfaces, without a new framework.** Server refusal, phone copy, and initiator countdown are in the tree. The missing piece is the explicit sentence that ties the lived story to those checks, and the honesty that the phone must not grow a second clock.
3. **Counts, coverage, and intention are three different things.** Integrity and auth-attempt packages are test-rich. That does not prove each assertion came from a human rule. A mutation score would speak only to bite.
4. **When the same agent writes the code and the oracle, the test can freeze the bug.** The two 2024 studies above are the caution. Security oracles should cite a rule that is not "whatever the method returns today".
5. **If mutation is ever tried, the comparable shape is a small, reviewed slice, not a global percentage.** Google's published practice and PIT/Stryker's own defaults point that way: few mutants, human eyes, no failing score until someone chooses one. Meta's concern-guided variant is a single industrial report, relevant to security wording, not a template to install.

## Candidates Marc can accept or refuse

Each one includes staying put. None is started in this change. Decision test, for any of them: does it reveal a security miss the current gates cannot see, is the oracle independent of the code under test, is the cost acceptable for the loop (PR versus on demand), can a person read the result quickly, and does it avoid proving the same rule twice?

| Id | Option | Cost / value | Refuse means |
| --- | --- | --- | --- |
| 0 | Status quo | No new tool. Current unit CI plus on-demand quality gate. | This draft was enough as a compass, and no experiment is funded. |
| 1 | Name two or three existing checks in `spec-test-traceability.md` (expired auth respond, expired enrollment bind, mobile expired copy) with the gate that runs them | Small doc edit. Makes the chain visible to an agent. Does not add protection. | The matrix is already enough at feature grain. |
| 2 | One manual, non-blocking PIT report on one class (for example respond or expiry), read the survivors, then stop | Unknown CPU until measured. Value is the survivor list, not a percentage. | Survivors are not worth a session, or the class is already well judged. |
| 3 | One manual, non-blocking Stryker run on one Jest file (`authApiProblem`) or, after confirming a Vitest runner, one Admin UI lib file | Same: report, not a gate. | Same as 2. |
| 4 | Keep verbatim dictation linked from an upstream doc when a future intention is dictated | This appendix is the specimen. Value is nuance and contradiction. Risk is noise if every aside is archived. | Do it only when Marc asks, as here. Do not write it into `product-docs/methodology/README.md` unless he asks. |

Ablation of docs, tests, or gates is not a candidate in this list. If a duplicate is noticed later, the proposal comes back to Marc. It is not done in passing.

## Sources consulted

Dates are the publication or the page date. "Opened" means the text or abstract was fetched for this draft.

- René Just, Darioush Jalali, Laura Inozemtseva, Michael D. Ernst, Reid Holmes, and Gordon Fraser. *Are Mutants a Valid Substitute for Real Faults in Software Testing?* FSE 2014. DOI [10.1145/2635868.2635929](https://doi.org/10.1145/2635868.2635929). Author PDF text and ACM abstract opened. 357 faults, five programs, 73% coupled, 17% coupled to no mutant.
- Goran Petrović and Marko Ivanković. *State of Mutation Testing at Google.* ICSE-SEIP 2018. DOI [10.1145/3183519.3183521](https://doi.org/10.1145/3183519.3183521). ACM abstract opened. Author-count mismatch with the [Google Research page](https://research.google/pubs/state-of-mutation-testing-at-google/) noted above. Full paper not read line by line.
- Mike Papadakis et al. *Trivial Compiler Equivalence.* ICSE 2015. DOI [10.1109/ICSE.2015.103](https://doi.org/10.1109/ICSE.2015.103). Abstract opened (~30% of equivalent mutants in that study). Java ~54% is the later paper *Detecting Trivial Mutant Equivalences via Compiler Optimisations*, TSE, DOI [10.1109/TSE.2017.2684805](https://doi.org/10.1109/TSE.2017.2684805). Abstract and a PDF text opened.
- PIT Maven quickstart, page fetched 2026-11-08. <https://pitest.org/quickstart/maven/>. Thresholds, `withHistory`, `targetClasses`.
- StrykerJS configuration, page fetched 2026-11-08. <https://stryker-mutator.io/docs/stryker-js/configuration/>. `thresholds.break` default `null`, `mutate` ranges, `incremental`, `coverageAnalysis`, TypeScript checker. Vitest runner not named on the sections read.
- Christopher Foster, Abhishek Gulati, Mark Harman, et al. *Mutation-Guided LLM-based Test Generation at Meta.* FSE Companion 2025. arXiv [2501.12862](https://arxiv.org/abs/2501.12862). Abstract and introduction opened.
- Michael Konstantinou, Renzo Degiovanni, and Mike Papadakis. *Can we find bugs using LLM-generated oracles?* arXiv [2410.21136](https://arxiv.org/abs/2410.21136). Abstract and introduction opened. The fetched page also labels a JACM appearance (2026).
- Noble Saji Mathews and Meiyappan Nagappan. *Design choices made by LLM-based test generators prevent them from finding bugs.* arXiv [2412.14137](https://arxiv.org/abs/2412.14137). Abstract and results text opened. 68.1% is CoverUp's final-suite share on that subset.
- Den Delimarsky, GitHub Blog, 2 September 2025. *Spec-driven development with AI.* <https://github.blog/ai-and-ml/generative-ai/spec-driven-development-with-ai-get-started-with-a-new-open-source-toolkit/>. Full post opened. Specify, plan, tasks, implement. Human checkpoints. No effectiveness numbers.

## Checks actually performed

- `git rev-parse` of `HEAD` and `origin/main` after fetch: both `275c5ea8a2070a9ccdbc0da8e874936a0d55aec7`.
- File counts, workflow reads, and the sampled tests named above.
- Primary pages listed under Sources.
- No `./scripts/build.sh`, no `mvn test`, no Vitest, no Playwright, no Maestro, no PIT, no Stryker, no quality gate.
