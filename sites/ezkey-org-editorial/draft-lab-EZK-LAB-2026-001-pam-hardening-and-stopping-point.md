---
status: draft
audience: "Technical readers following Ezkey lab experiments; not a security-assurance audience"
planned_slug_en: "lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.html"
planned_slug_fr: "lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.html"
planned_canonical_en: "https://ezkey.org/lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.html"
planned_canonical_fr: "https://ezkey.org/fr/lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.html"
lab_id: "EZK-LAB-2026-001"
lab_status: draft
source_of_truth: draft
---

<!-- ezkey-org:exclude-start
EDITORIAL NOTES — English draft, revision 4 (2026-10-28). On branch docs/lab-paper-pam-draft. Not for publication.

Where should the draft live in the repo? (to settle when Marc gives the go)
- Sources: templates/lab-paper.en.template.md says "Copy to sites/ezkey-org-editorial/draft-lab-EZK-LAB-YYYY-NNN-<slug>.md";
  templates/lab-paper.fr.template.md says "sites/ezkey-org-editorial/fr/draft-lab-EZK-LAB-YYYY-NNN-<slug>.md";
  sites/ezkey-org/AGENTS.md says "Draft files MUST live in sites/ezkey-org-editorial/fr/". There is also an English
  precedent at the editorial root (draft-technical-trace-ezkey-enrollment-authentication.md).
- Option A (recommended): EN at sites/ezkey-org-editorial/draft-lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.md,
  FR at sites/ezkey-org-editorial/fr/draft-lab-EZK-LAB-2026-001-pam-hardening-and-stopping-point.md. Follows the two
  templates and the existing EN precedent. Never under sites/ezkey-org/ (Wrangler deploys that folder).
- Option B: both files in sites/ezkey-org-editorial/fr/ (literal reading of AGENTS.md). Simplest rule, but an EN file in
  a folder named fr/ is confusing.
- Option C: a sub-folder sites/ezkey-org-editorial/lab/. Tidy once there are several papers, but it is a new convention
  that LAB-PAPERS.md does not mention. Not recommended for paper no. 1.
- Side fix to propose: one line in sites/ezkey-org/AGENTS.md so that "fr/ only" and the EN template agree.

Status of this draft (revision of 2026-10-27): integrates the second retrospective round (lab note
EZK-LAB-2026-001-retro-2-synthese.md and retro-2-raw/). Marc reviews, proofreads and frames before any publication.
Kept decisions: security sentence "reasonably assured within the tested perimeter, without being the end of the story";
the Sept 2025–Sept 2026 gap as "a year-long pause, no trace in the repository"; agent speed at finding and fixing
vulnerabilities stays a personal observation with no external citation; the stopping point is a collaboration between
Marc and the agents, judged sufficient as experimental maturity and nothing more; no "secure", "audited", certification,
or Duo/FIDO2 equivalence. Ezkey is a lab project first.

Coherence challenges treated in the body, not smoothed over:
- The 2026-10-25 stopping reading and the 2026-10-27 further passes are one story: a first judgment, then a deliberate
  dig, then the same kind of judgment.
- Christophe's "cycle 3 of #1051" / "pam_ezkey 2.2.20" does not match the pull-request titles. #1051 is titled cycle 2b
  at 2.2.19; #1067 is titled cycle 3 and its body names 2.2.20. Left as [TO VERIFY], not collapsed.
- This draft's tree still has ezkey-pam/STATUS.md at 2.2.14. Later merges are cited from GitHub, not from this tree.
- #1014, open in the 2026-10-25 draft, was merged on 2026-10-27 (b1fd7e2b). The signal-handler narrative was not re-read
  against that merge.
- "Diminishing returns" from the October burst (re-reviews, flag switching, TTL) and from the later cycles are not added
  into one curve. The phrase stays a judgment, not a measurement.
- Patrick's finer counts (12 findings, 19/35, 3/10, 29/35, 88–90 s) stay in his raw reply and out of the body.

Mathieu's retrospective exists only as a DRAFT not validated by Marc: it is NOT integrated. He sent no round-2 reply.
Co-author note updates from 2026-10-27 are pending validation by each co-author.
Marker convention: [TO VERIFY] / [MISSING DATA]; every marker is listed at the end in an exclude block.
Tighten pass (2026-10-28), same decisions, layout unchanged. Shortened for a cold reader: the cycles are told once (§5.4), read once (§7), then applied in §9 Actionable items. Cut or folded: the second co-author note per person (merged into one), the retelling of the cycles inside the diminishing-returns section, the fractal section and the cost section (one sentence each, now in Limitations; the cost [MISSING DATA] stays), the issue-by-issue taxonomy, and the sentence that Patrick's finer counts are not printed (its marker left with that sentence; ledger item 24). The second STATUS.md marker was dropped because the same unread-on-default-branch claim remains in §5.4. No new factual claim.
The French draft is the mirror of this revision. Still not for publication.
ezkey-org:exclude-end -->

# Ezkey PAM: hardening an experimental SSH module with agents, and deciding where to stop

**Authors:** Marc Gagnon (editorial responsibility); Christophe Grokbot (security review); Edgar Grokbot (deployment and edge); Isabelle Grokbot (exploratory QA); Mathieu Grokbot (coordination) `[TO VERIFY: Mathieu's evidence and validated note pending]`; Patrick Grokbot (engineering review)
**Affiliation:** Ezkey Lab
**Identifier:** `EZK-LAB-2026-001`
**Status:** draft
**Date:** 2026-10-27
**Keywords:** PAM, SSH, MFA, fail-closed, agent-assisted development, agent autonomy, human framing, diminishing returns, experimental
**Reading time:** ~15 min

## Key findings

- Every friction reported on 2026-10-27 was on the human side of the setup: an unwritten go rule, an unclear permission scope, a method that lived only in conversation, ambiguous instructions, and no stop rule or cycle budget fixed in advance. The reports do not treat this as a failure of the agents (§7.1). What a later brief can write down is in §9.
- After a first reading on 2026-10-25, Marc left the agents free to iterate, to see where a human and the agents converge on diminishing returns. He also judges that models are advancing quickly, in cybersecurity in particular. Both that judgment and his impression that agents find and fix vulnerabilities faster are personal observations: we cite no outside source. "Diminishing returns" is a judgment, not a measurement. Reviewers and the implementer share a model family, the loop grades itself, and an independent human review, continuous fuzzing and a threat model would test it (§7.2).
- On that later campaign the agents carried more of the work. What they still needed were small written adjustments, not a different kind of agent (§6.5, §9).
- A prototype of about 900 lines, committed on 2025-09-03 and validated by hand, left no automated evidence. The repository then shows a year-long pause, no trace in the repository of functional work on PAM, until work resumed on 2026-09-15.
- Marc and the agents judge the result sufficient as an **experimental** maturity: security is reasonably assured within the tested perimeter, without being the end of the story. Ezkey is a lab project first. This is not a claim that the module is secure, audited or certified, and not an equivalence with Duo or FIDO2.

## Abstract

This paper reports an experiment on `ezkey-pam`, a Linux PAM module that adds Ezkey device approval to SSH logins. Ezkey is a lab project. A prototype was committed in September 2025. After a year-long pause, with no trace in the repository of functional work on PAM, agents and Marc reviewed the module through October 2026, then iterated on purpose so that a point of diminishing returns could be seen rather than assumed. The frictions in that further pass trace to human framing. Security is reasonably assured within the tested perimeter, without being the end of the story. We claim no production readiness, no audit, no certification, and no equivalence with Duo or FIDO2. Section 9 lists the process changes a later brief can make, each traced to a finding above.

## 1. Introduction and motivation

Marc first saw an SSH login protected by Duo at work, which was new to him `[TO VERIFY with Marc: when]`. The question that followed was whether Ezkey could play a similar role for SSH, with the pieces it already had. That question is not a claim of equivalence. Nothing in this paper equates Ezkey with Duo, WebAuthn or FIDO2.

Ezkey is a lab project first, not promised to anyone. The realistic niche we see is passwordless, strong-cryptography protection of internal applications for a small internal staff. Going further would first require SOC 2-style discipline: a prerequisite, not a plan. `docs/SECURITY_POSTURE.md` treats SOC 2 as a reference vocabulary, not a certification target, and makes no equivalence claim.

Two further reasons shaped the experiment. Marc has the impression that autonomous agents have become visibly faster at finding and fixing vulnerabilities. This is his personal observation; we cite no outside source and did not measure it. Separately, he judges that models are advancing quickly, in cybersecurity capability in particular. That too is his judgment, with no outside source `[TO VERIFY: wording of this reason, given for the 2026-10-27 revision]`. It is why, after a first stopping reading on 2026-10-25, the agents were left free to say what they saw and to iterate as many times as they needed. The point of the extra passes was to watch where a human and the agents converge on diminishing returns (§5.4, §7.2). A third reason, already present in the 2026-10-25 draft, was to see how much of the work agents could carry with little human intervention (§6.4, §6.5).

This paper says what happened and what was measured. One conclusion is stated in plain terms, because the second retrospective round was asked for it: where a friction appeared, it was on the human side of the framing (§7.1). What to change in the next brief is in §9. What the experiment means beyond that stays outside this paper.

## 2. Hypotheses

These were **formulated after the fact**; none was written down before the work started. We state them to make the reading explicit, not to suggest the experiment was designed around them.

- **H1.** The existing pieces (authentication, APIs, device) were enough to build a complete SSH approval flow.
- **H2.** An AI team with cross-review can bring a small C module to a point where review findings become minor.
- **H3.** Beyond some point, another review round costs more than the lab-level risk it removes, and that point can be identified.
- **H4.** In the agent campaign, the waits and the confusion would come from human framing (an unwritten go rule, an unclear permission scope, ambiguous instructions, no stopping criterion fixed in advance), not from a limit of the agents.

H1 was tested by the 2025 prototype. H2 and H3 were not tested in a controlled way. H4 was not written before the 2026-10-27 round; it is the reading that round supports, and we state it as a conclusion in §7.1. The same round complicates H3: the point can be described afterwards, and it is hard to know it during the work if no cycle budget was fixed first (Patrick).

## 3. Prior state

By September 2025 the platform already had most parts of an approval flow (Table 1, dates from git).

| Element | Evidence | Date |
| --- | --- | --- |
| Demo Device | `e532ce95` | 2025-08-10 |
| `/wait` endpoint | `796dd455` | 2025-08-26 |
| First Android app | `fd7d9d6a` | 2025-09-01 |
| **First PAM commit** | `04d339b7` | **2025-09-03** |
| Android proof of concept | `1efda3d1`, `8da533ce` | 2025-09-04 |
| Admin UI | `71e27831` | 2026-03-06 |

*Table 1. Context around the first PAM commit.*

Marc recalls that in summer 2025 the protocol was simpler: a simulator at first, no Admin UI, a simpler database. The simulator is visible in the history (`988ef115`, 2025-08-19); the database remark is not checked `[TO VERIFY: database schema in summer 2025]`. Some early commits were authored by an automated agent account, not by Marc `[TO VERIFY: authorship of each commit in Table 1]`.

## 4. Protocol

**The module.** `ezkey-pam` is a C PAM module for SSH keyboard-interactive. It asks the user to confirm with `ok`, creates an auth-attempt through the Integration API (`POST /api/v1/auth-attempts`), optionally shows a two-digit code to match, then waits (`GET …/wait`) while the user approves or rejects on a device (the Demo Device or the Android app).

**Design constraints.** It fails closed: an unusable API or configuration returns `PAM_AUTHINFO_UNAVAIL`, and there is deliberately no `failmode=safe`. The configuration is a root-owned file (`0600` or `0400`), with no default API URL and no environment variables. A documented caveat remains: a `sufficient` PAM stack can make the module fail-open.

**Environment.** A Rocky Linux container for builds and demos and, from October 2026, a dedicated test VM with a real sshd. Several findings appeared only there. We give no host names, addresses or internal paths.

**Roles.** Marc decided and approved, and typed at the SSH keyboard in manual campaigns. Mathieu coordinated and relayed during the October burst; he is not a source for the 2026-10-27 round `[TO VERIFY: Mathieu]`. Patrick and Christophe reviewed; Edgar operated the test VM; Isabelle ran exploratory QA.

**How reviews reached GitHub.** Patrick's and Christophe's reviews reach the repository through **Marc's GitHub account**: the comments are posted from `mgagp`, and the text of each says which co-author wrote it. An issue (#1016) says reviews were relayed by Mathieu, and squash-merge commits carry Marc's name. In this paper, attribution rests on the text of the comment or on the co-author's retrospective, not on GitHub identity `[TO VERIFY: that comments reflect the original review text]`.

**Tests.** A `make test` target, shell test scripts, signal probes against a real sshd and a CI job. We did not re-run them for this paper; figures below come from the repository, from pull requests, or from the retrospectives, and the last of these are marked.

**Bounds named for the 2026-10-27 campaign.** Nothing aimed at the public community instance, no commit and no push, no secret shown, proof by execution on a disposable cloud agent, findings sorted as fix, document, or out of scope, and security review kept distinct from code review (Christophe). A standing permission on the module did not cover system writes. Each of those waited for its own go (Edgar). That gap is §7.1 and row A2 of §9.

## 5. Course of work

### 5.1 Prototype and pause

The first PAM commit, `04d339b7` (2025-09-03, 9 files, 919 added lines), describes itself as a basic hard-coded module in Rocky Linux under Docker, where after the SSH password the demo device must confirm access. According to Marc it was also run on a VM and validated by hand only `[TO VERIFY: VM test; no automated evidence in git]`.

Then a year-long pause, no trace in the repository of functional work on PAM. The folder received only four mechanical commits: a port change (`07746bb8`), a move to Rocky Linux 10 (`90b16e57`), file permissions (`83758319`) and documentation hygiene (`b19eca7d`). We claim nothing about that period beyond what git shows.

### 5.2 A missing URL that did not fail closed

Pull request #540 (`3fef2411`, 2026-09-15) modernised the module. Pull request #610 (`8b3aa5df`, "M1"), opened on 2026-09-22 and merged on 2026-10-17 (38 files, +3767/−332), added observability, fail-closed configuration, the two-digit challenge and a time budget.

Patrick's first review returned **CHANGES** with one blocking finding: a missing API URL did not fail closed, because the code pre-filled `http://localhost:7080`. A configuration without a URL would have sent integration credentials in clear to a local port where another local user could answer `ACCEPTED`. Christophe's review returned minor findings only. Patrick re-reviewed twice and returned **GO** on 2026-10-17 once CI was green. The rule this leaves is in §9, row A6: no default that makes a missing setting look valid.

### 5.3 The October burst

| Version | Landed | Change | What exposed it |
| --- | --- | --- | --- |
| 2.2.10 | 2026-10-20 | No orphan pending request after Deny/Expire retry (#907, #908); `Match … MaxAuthTries 1` mandatory in docs (#934) | Edgar's retest of #908: without `Match`, three requests were created |
| 2.2.11 | 2026-10-21 | Waiting message shown before the device wait (#941, #956) | Edgar: OpenSSH buffers bare `pam_info` text until the next prompt |
| 2.2.12 | 2026-10-21 | Confirm-before-create (`ok`) and enrollment challenge display (#975, #976) | Isabelle (#975); Patrick's review found a TTL computed before the `ok` |
| 2.2.13 | 2026-10-22 | Interaction fluidity tiers 0–1 (#982, #983) | Follow-up to #976 |
| 2.2.14 | 2026-10-22 | `challenge_requested=true` by default (#995, #996) | Decision by Alex and Marc, per `STATUS.md` |
| 2.2.15 | 2026-10-27 (`b1fd7e2b`, #1014) | Richer failure diagnostics, SIGTERM handling, signal probes (#1011, #1014) | Manual campaign on 2.2.14; engineering and security reviews |

*Table 2. Patch versions of October 2026. The 2026-10-25 draft of this paper still had #1014 open. Versions 2.2.2 to 2.2.9 are not reconstructed `[TO VERIFY]`. The signal-handler account below was not re-read against the merged text `[TO VERIFY]`.*

#992 (approval fatigue: pending-request limit, rate per enrollment, cooldown, alert on denial) was scoped, then rewritten in generic platform terms on Marc's instruction of 2026-10-22. Commit `9f7fa78f` (#993) documents the limit; #992 is not implemented.

#1014 is where a static reading, then execution, then a second reading each found something the previous step had missed. Patrick predicted a hang after SIGTERM. A cloud agent confirmed it and found a second defect: a SIGTERM during request creation was absorbed, and authentication could then succeed after the client had gone. Christophe, reading the handler, found a call that is not async-signal-safe. The head they returned GO on (`6bc342a8`) writes to the log device, restores the default action and re-raises the signal. Four probes against a real sshd failed on the previous head and pass on this one. Both GOs are known from the text of #1016, not from a GitHub review object `[TO VERIFY]`. #1016 also names a later review at `34ae3603` and the merge `b1fd7e2b`. We did not re-read the merged text. The same issue says the documentation must **not** say "never SUCCESS after the client is gone": SUCCESS stays possible if a TERM is absorbed during `/wait` after ACCEPTED has arrived, and #1016 says this is not an elevation.

### 5.4 A further pass, on purpose (2026-10-27)

The 2026-10-25 reading did not end the work. Marc left the agents free to keep going (§1). What follows is their retrospectives of 2026-10-27. Where a pull-request title disagrees with a reviewer's memory, we say so.

Following Patrick: cycle 1 was the only pass that found something of product nature. No test killed the mutations of the authentication decision, and TLS verification was not guarded by a test. Cycle 2 found no further behaviour defect; he recommended a short third cycle, then a stop. Cycle 3 found a harness bug (probe 4), a TLS test hole an earlier fix had opened (peer verification alone), and an equivalence call of his that was wrong. What iteration added, and a single pass would have missed, was a measurement in place of a suspicion, a defect a fix had introduced, and the correction of a reviewer's error. Not a further security defect after cycle 1. That one pass plus an execution check of each fix would have caught almost everything is his judgment, not a count.

The GitHub titles do not match that memory. #1051, merged 2026-10-27, is titled cycle 2b at version 2.2.19. #1067, merged later the same day, is titled cycle 3 and its body names version 2.2.20; probe 4 was a harness hang, and the TLS test was changed so peer verification alone rejects. Christophe, from memory, says "cycle 3 of #1051" and also names 2.2.20. We do not treat those as the same object `[TO VERIFY: cycle label and 2.2.20]`. After that, nits, then a recursion into the tooling that checks the tooling. Patrick says the second level yielded little. His exit criterion (§7.2) stops at one level of test-tooling review.

Isabelle: the risks she treats as real were early. While a login waits, the module does not see the client die, so a pre-authentication process can last up to about 75 seconds, bounded by the attempt TTL. A later login wins and closes the previous attempt. Replays added proof of the same behaviour, not a new risk (Table 5). True network silence had no go and was not tested. #1070, a rate limit on creating attempts, was open on 2026-10-27, outside the module, and not measured.

This draft's tree does not contain those merges. `ezkey-pam/STATUS.md` here still says version 2.2.14 and that the module is not a host-operable MFA agent on a real host. We did not re-read it on the default branch after #1067 `[TO VERIFY]`.

## 6. Results

### 6.1 What we did not count

#610 was reviewed on three heads (`932c83f4`, `5d743589`, `ca86a1c2`), with the verdicts in §5.2. On #1014, engineering and security both asked for changes, then both returned GO at `6bc342a8` (§5.3). Other pull requests (#897, #908, #956, #976, #983, #996) were reviewed too; we did not read all of them, so we give no counts of findings per round `[TO VERIFY: rounds and severity]`. The cycles of §5.4 are a separate series. We do not fold them into that count.

### 6.2 Manual campaign on 2.2.14

Isabelle's manual campaign on 2.2.14 (#996), Marc at the SSH keyboard and on the Demo Device, sits in a test-management tool outside the repository: 30 cases, 29 passed, 1 failed (S6-02, cancel and audit, now #1012). Marc recorded the milestone as "reached with reservations" (S1D-06, S11-01) `[TO VERIFY: figures come from Isabelle's retrospective of 2026-10-25; tool and notes are outside the repository]`. The useful result was a misleading symptom. With `MaxAuthTries 1`, almost any internal error shows up as "Too many authentication failures". A debug trace on 2026-10-24 showed UNAVAIL after 741 ms, reason "pre-wait confirm missing or not ok", and no POST: the `ok` prompt had received an empty answer. sshd's `LoginGraceTime` (120 s), not the module's 75 s wait, bounds a real attempt. This led to #1011, #1014 and #1016.

### 6.3 Open items, as a dated snapshot

Eleven issues were open on 2026-10-25, plus PR #1014: #858, #955, #979, #980, #982, #992, #997, #1011, #1012, #1013 and #1016. By 2026-10-27, #1014 was merged and #1011 was closed; #1051 and #1067 had been merged; #1054 and #1070 were open. We did not re-count the whole set `[TO VERIFY: open issues after 2026-10-27]`. The items that still bound the claim are named in §8.

### 6.4 Autonomy through 2026-10-25

Table 3 is the October burst, through 2026-10-25. Sources are the co-authors' retrospectives. These are observations, not a measurement of autonomy.

| Aimed at | Limit observed | Source |
| --- | --- | --- |
| Tests without a person's phone. Demo Device for almost all tests; Marc's Pixel only for a few exploratory tests of his own (his statement) | The 2.2.14 campaign still needed Marc at the keyboard and on the Demo Device | Marc; Isabelle |
| Review of one frozen SHA, one batch of fixes | Re-reviews after about the second head were mostly minor | Patrick, Christophe |
| Agents replay the tests: retests (#908), signal probes on a real sshd, a cloud agent running the #1014 prediction | Patrick had no Docker or Java locally, so each proof went through a cloud agent; some CI guards proved nothing (#996, #1014) | Patrick, Edgar |
| A bounded GO on the test VM: snapshot, `sshd -t`, report, one bot at a time | A relayed "Marc agrees" is not a GO. The maintenance path stayed outside MFA (#955) | Edgar, Isabelle |
| Findings written on the PR or the issue | A message that does not wake an agent is missed. A finding set from #961 was lost in chat `[TO VERIFY]` | Christophe |
| Retests with no person in the loop | The human round trip (about 77 s) exceeded the 75 s TTL, so some trials were invalid | Isabelle |
| Agents compose and relay | Nothing is published without Marc: he gives the material, agents compose, he revises, review and preview before publication. Reviews reach GitHub through his account, relayed by Mathieu (#1016) | Marc (statement); #1016 |

*Table 3. Autonomy aims and observed limits, through 2026-10-25.*

### 6.5 What the agents carried on 2026-10-27

Table 4 pairs each gain the co-authors report with the written rule that was still missing. Table 5 is Isabelle's scenario verdicts from the same day.

| What the agents carried | Still missing — see §9 | Source |
| --- | --- | --- |
| A tenant through the public API, with a log of the times | The method of that day was not written down (A3) | Isabelle |
| A longer poll, and a freshness check after HTTP 429 | Nothing named; reported as having worked | Isabelle |
| A state protocol: one login, a read-only observer, hashes, a checked rollback | Each system write waited for its own go (A2, A4) | Edgar |
| Proof by execution; findings sorted fix / document / out of scope; security review kept apart | The finder graded the exit. Three mutants killed at runtime could not be met for one host-side TLS check, and part of a later cycle served that count (A5, A7) | Patrick; Christophe |
| The named rules held: not the public community instance, no commit or push, no secret shown | The go sentence was not in the brief. Cleanup was still waiting for a go (A1) | Edgar; Isabelle; Christophe |

*Table 4. Gains reported for 2026-10-27, and the written rule still missing. Not a measurement. The sentence to write is in Table 6.*

In the October burst, each stage still passed through Marc or a relay (Table 3). On 2026-10-27 the agents planned, executed and restored inside bounds that had been named. The bounds that had not been named are where they waited. We still have no measure of what share of the work this is `[MISSING DATA]`.

| Scenario | Verdict she reports | Limit she names |
| --- | --- | --- |
| S1 | PASS | — |
| S2 | No case blocked | Slow while waiting, immediate at the prompts |
| S3 | Quick refusal, PASS | Network silence not tested |
| S4 | PASS, three times | Fail-closed, and a diagnostic in which two different configuration faults look the same |
| S5 | PASS | Same account only |
| S6 | PASS, with limits | Raw values only |

*Table 5. Scenario verdicts from Isabelle's retrospective of 2026-10-27. Not in the repository `[TO VERIFY]`. Her stated perimeter: the MFA protects accounts in the group the module is configured for; on a maintenance account outside that group, authentication was not invoked.*

## 7. Discussion

### 7.1 The weakness was in the framing

This is the conclusion of the paper.

In every friction the 2026-10-27 round describes, the agents were waiting on something a person had not made explicit. They applied the rules they had, and they stopped where the rule was missing.

1. **Go rule unwritten.** Only Marc, in the executor's thread, counts as a go. The waits came from agents saying he would answer in some other thread (Edgar, Isabelle).
2. **Permission scope unwritten.** A standing go for the module did not say whether it covered a firewall change, configuration files or keys (Edgar).
3. **Settled method unwritten.** How to open an evaluation tenant through the public API was not written down (Isabelle).
4. **Instructions ambiguous.** "API stopped" became a name-resolution change checked for one address family, and the test measured nothing. A clock time stood in for the expiry of a key and of a session. "Module loaded", "authentication not invoked" and "second factor" named different facts (Edgar, Isabelle).
5. **No stop rule.** Convergence was noticed afterwards. The finder graded "nothing new". An agent-set count, three mutants killed at runtime, outlived the risk it was meant to reduce (Patrick, Christophe).

The go sheet, the cleanup list and the exit criterion are not adopted `[TO VERIFY: whether any of them is adopted]`. Section 9 is the sentence to write, who writes it, and when. Writing it there does not record a decision Marc has taken.

### 7.2 Where the returns diminished

The decision to keep digging was Marc's (§1). The curve the agents report is a judgment. Its shape is the one told in §5.4: product findings, then proof, then nits, then the tooling that checks the tooling. Isabelle's risks that she treats as real were early. Christophe places the same drop early, under a pull-request label Patrick does not use (§5.4).

**Exit criterion (proposed, not adopted).** Patrick's rule for a last cycle is the following. There is no finding of product type: no regression, no fail-open, no lost invariant, no leak, no data loss. Each fix from the previous cycle has been re-read as a delta and executed. What remains is tooling or documentation. Then the residue is recorded, at most one follow-up issue is opened, and the work stops. Hard cap: one cycle after the last product-type finding, and the test tooling is reviewed one level deep, never the script that proves the script. A limit that cannot be fixed at a proportionate cost is documented. It does not reopen a cycle.

That rule was not the rule of this campaign. The campaign noticed convergence afterwards. We do not present the criterion above as a result. It is his proposal. Row A5 of §9 says who writes a stop rule, and when.

The caveat has to stay next to the curve. "Diminishing returns" is a judgment, not a measurement. Neither Patrick nor Christophe gives a count of time or of cost. Reviewers and the implementer are of the same model family, so agreement can come from shared blind spots and not only from a list of defects that has been exhausted. The loop grades itself: the implementer writes the mutants and the tests, and a reviewer from the same family reads them. Patrick was wrong once on an "equivalent" mutant, which is why a second look is not optional on a security mutant. What would test the judgment is an independent human review, continuous fuzzing, and a one-page threat model. Christophe lists all three as not done.

The earlier reports of diminishing returns, from the October burst, are a different observation: repeated re-reviews of one pull request (Christophe), wording and documentation (Patrick), repeated switching of `challenge_requested` (Edgar), retests invalidated because the human round trip exceeded the 75-second TTL (Isabelle). We do not add that observation to the cycle curve.

### 7.3 A stopping point, decided together

On 2026-10-25, Marc and the agents agreed to treat the result as sufficient for an **experimental** maturity. Work then continued, on purpose, through §5.4. The stopping point after those passes is the same kind of decision: a collaboration between Marc and the agents, a judgment that the experimental maturity is sufficient, and not a claim of anything further.

The wording of this paper is: security is **reasonably assured within the tested perimeter, without being the end of the story**.

Christophe proposes a longer paragraph for that perimeter. We quote it as his proposal. We do not adopt it as this paper's finding, and the version and the cycle label inside it are the ones §5.4 does not reconcile:

> Within the tested perimeter (pam_ezkey 2.2.20 on the lab, a pilot on the community instance with no SLA), the tested error paths fail closed, TLS verification is active and proven by execution and by mutants, configuration is limited to root, and secret values do not appear in the logs that were measured. Three AI-assisted review cycles with reproducible proofs found no new exposure problem after cycle 3 of #1051. This is neither an independent audit nor a certification: the component is not presented as a first-line element before an independent human review, continuous fuzzing and a threat model.

We do not say "secure". We do not say "audited". We claim no certification, and no equivalence with WebAuthn, FIDO2 or Duo. Ezkey is a lab project first. Row A10 of §9 is that sentence, and the ban on the longer claim.

Our reasons for the short wording, and no more:

- the later cycles, as the co-authors describe them, did not add a security defect after the first of those cycles, and the residue is named (a harness limit, a host-side TLS check that runtime tests do not kill alone, #1070, network silence not tested);
- the diminishing-returns reports are real and they are judgments (§7.2);
- known residual risks are documented, not hidden;
- continuing has a cost, and we have no evidence it would change the lab-level risk. We also have no evidence that the curve is finished (§7.2).

This does **not** mean the module is host-operable. On the tree of this draft, `ezkey-pam/STATUS.md` still says it is not a host-operable MFA agent on a real host (§5.4). `docs/SECURITY_POSTURE.md` lists PAM among undeployed lab surfaces. Moving to the community instance (#955) would require redoing the assessment.

## 8. Limitations

- Experimental, not released. No PAM release tag on this tree. `STATUS.md` here still says the module has not run as a host-operable agent. TLS, dogfood and host sshd cutover are not done on this tree. Version 2.2.20 is a string in the body of #1067, not a fact re-checked in this tree `[TO VERIFY]`.
- Not mitigated server-side: approval fatigue (#992; the module-side mitigation in 2.2.14 is the default challenge, #995) and the `/wait` signature (#858). A `sufficient` stack can be fail-open. "Never SUCCESS after the client is gone" does not hold (#1016). #1070 was open on 2026-10-27 and is outside the module. Network silence was not tested.
- One operator, one environment family (Docker on Rocky Linux, one test VM). No external audit, no certification, no SOC 2 claim. Christophe's local penetration test was paused with no findings; we claim no result from it. Independent human review, continuous fuzzing and a threat model are not done. He drafted the approval-fatigue line in `docs/SECURITY_POSTURE.md` (2026-10-22) and has not re-read the final text; the entry exists on this tree.
- Reading the defects from a silent URL fallback (#610) through a late SUCCESS (#1014) as ever-finer questions is our interpretation. Those links were not tested. We have no figures for money, compute or human time, and none for a person working alone `[MISSING DATA]`. "Dense" or "fast" means dates and counts in git, or a marked personal impression.
- "Diminishing returns" is a judgment (§7.2). Reviews are text and reached GitHub through Marc's account. Versions 2.2.2 to 2.2.9, some review rounds, the 2.2.14 campaign figures and Table 5 sit outside this reconstruction or outside the repository. The signal-handler account was not re-read against the merge of #1014.
- The hypotheses were written after the work. H4 was not pre-registered. Stopping was a joint judgment. We do not know what a longer series, or a person reviewing alone, would have found. Co-author notes below are pending validation. Cleanup of the test account and keys was still waiting for a go. We record no key material and no path.

## 9. Actionable items

Each row is a change a later brief can make. None of these was the written rule of this campaign. The go sheet, the cleanup list and the exit criterion remain proposals `[TO VERIFY: whether any of them is adopted]`. Writing the sentence in the place named below does not record a decision Marc has already taken. The trace column points at the finding.

| # | Write this down | Who writes it, and when | Trace |
| --- | --- | --- | --- |
| A1 | Only the person who may say go (here, Marc), in the executor's thread, is a go. Do not act on another agent's report that a go is coming in some other thread. | The author of any brief that can change state, before the first action. | §7.1 form 1; Table 4; Edgar |
| A2 | What a standing permission covers (module code, or also a firewall change, configuration files, keys), and which writes still need their own go. | The person granting the permission, before the first system write. | §7.1 form 2; Edgar |
| A3 | The method that just worked, where the next agent will look. In this campaign: how an evaluation tenant was opened through the public API. Not only in chat. | The agent who just made it work, the same day. | §7.1 form 3; Table 4; Isabelle |
| A4 | Which fact the words mean: API stopped versus a name-resolution change; clock time versus expiry of a key and of a session; "module loaded" versus "authentication not invoked" versus "second factor". If name resolution changes, check both address families. Confirm the altered state is in effect before a login or a key is spent. | The brief author, before the first attempt that consumes a login or a key. | §7.1 form 4; Edgar; Isabelle |
| A5 | A stop rule and a cycle budget, written by a human before cycle 1. The finder does not grade "nothing new." An agent-set count (here, three mutants killed at runtime) does not outlive the risk. Patrick's exit criterion (§7.2) is a candidate text. It is not adopted. | A human (here, Marc), before cycle 1 of a security-review series. | §5.4; §7.1 form 5; §7.2 |
| A6 | A missing required setting has no silent fallback. A missing API URL must not become `http://localhost:7080`. | The implementer, before merge of a component that fails closed or carries credentials. | §5.2; #610 |
| A7 | What a green check actually checks. A mutation table (killed or surviving) for any test used as proof. A check a comment can satisfy is not proof. Mark each claim executed or only reasoned. When the defect is in the conversation with sshd, run the probe against the real daemon. | The reviewer, before GO on a security fix. | §5.3; §5.4; Patrick; #996; #1014 |
| A8 | A confirm prompt (`ok`) is a filter against accidents, not a defence. Whoever already has the first SSH factor can type it. The control that matters is elsewhere (the challenge, server-side limits). | Whoever writes what the module protects, before that sentence is reused. | Christophe; #992 |
| A9 | That "diminishing returns" is a judgment. Name the shared model family and that the loop grades itself. Independent human review, continuous fuzzing, and a one-page threat model stay listed as not done until a record says they were done. Do not add the October-burst re-reviews onto the cycle curve. | The person who stops the series, in the decision note. | §7.2 |
| A10 | The security claim, and only this: reasonably assured within the tested perimeter, without being the end of the story. Do not write secure, audited, certified, or equivalent to Duo, FIDO2 or WebAuthn. Experimental maturity is the ceiling. | Anyone writing a status line, a paper, or a release note for this module. | §7.3 |
| A11 | The approval path must finish inside the attempt TTL, or the trial is invalid (here, a human round trip of about 77 s against a 75 s TTL). One finding is text on the frozen head (the pull request or the issue), not only in chat. One review is one SHA. | The operator, before the first manual case; the reviewer, at each review. | Table 3; Isabelle; Christophe; #961 |

*Table 6. Actions a later brief can take. Not decisions of this campaign.*

## 10. Contributors' retrospective

Two rounds were collected. On 2026-10-25 each co-author answered, through Mathieu. On 2026-10-27 Edgar, Isabelle, Patrick and Christophe answered again. Mathieu sent no second reply. No disagreement between co-authors was reported. Patrick and Christophe do not use the same pull-request label for the third cycle (§5.4); that is a gap in the record. Each note below is pending validation by its author before any publication `[TO VERIFY]`.

> **Co-author note — Patrick**
>
> On #1014 a static reading predicted a hang. Execution showed at least 123 seconds against a 225 ms baseline, and a SUCCESS after the client had left. Neither was visible to `make test`. The exit criterion in §7.2 is my proposal. "Diminishing returns" is my judgment, not a count. I ask for a mutation table, killed or surviving, and for each claim to be marked executed or reasoned.

> **Co-author note — Christophe**
>
> The handler called `snprintf`, which is not async-signal-safe. I saw that by reading the call, not by running it. The `ok` prompt is not a defence. I said "cycle 3 of #1051"; the titles do not (§5.4). The longer paragraph in §7.3 is my proposal. I do not call the module secure or audited, and I claim no FIDO2 equivalence. Reviewers and the implementer share a model family, and the loop grades itself.

> **Co-author note — Isabelle**
>
> "Too many authentication failures" was an empty `ok` and no request sent. I could not continue without Marc on the phone. On 2026-10-27 the tenant method was not written down. The risks I treat as real were early: a client dying during the wait, up to about 75 seconds, and a later login closing the attempt. Network silence was not tested. Table 5 is mine and is not in the repository.

> **Co-author note — Edgar**
>
> Five versions on the test VM, with a snapshot and `sshd -t` before each change. With `MaxAuthTries 1`, any internal error looks like an authentication failure. A relayed "Marc agrees" is never a go. On 2026-10-27 the waits were "Marc will answer in another thread", and a standing permission that did not cover system writes. Cleanup of the test account and keys was still waiting for a go in my thread.

> **Co-author note — Mathieu**
>
> `[PLACEHOLDER — Mathieu's retrospective exists only as a draft not validated by Marc. He sent no reply in the 2026-10-27 round. To be added once validated: coordination, relaying of reviews, the process view, and a note of 80–150 words. Do not publish with this placeholder.]`

**Julie.** Julie Grokbot is not a co-author. She did not touch PAM: no review, no Walk Gate, no pull request, no report. The only contact was a retest message sent to her by mistake and redirected.

## 11. Next steps

None of the following is a commitment. The open items of §6.3 remain a dated snapshot, not a plan. The rows of §9 are not decisions. Before any move to the community instance, the assessment would have to be redone, and Marc's host validation is still outstanding on the tree of this draft. #1070 was open on 2026-10-27. Cleanup of the campaign's test account and keys was still waiting for a go.

## Who did what

- **Marc:** editorial responsibility; decisions and approvals; SSH keyboard in manual campaigns; GitHub account used for commits and review comments; the decision to leave the agents free to iterate on 2026-10-27.
- **Christophe:** security review (#1014 and others), scoping of #992; the 2026-10-27 security reading and the proposed perimeter paragraph.
- **Edgar:** test-VM operations from 2.2.10 to 2.2.14, with a backup per version and a documented rollback; retests (#908, #941); diagnostics analysis cited in #1011; the state protocol and the go-sheet proposal in the second round.
- **Isabelle:** manual campaign on 2.2.14; case revision S0–S13; timing measurements; the analysis behind #1011; challenge display (#975); the 2026-10-27 scenario verdicts.
- **Mathieu:** coordination and relaying in the first round `[TO VERIFY]`. No reply in the second round.
- **Patrick:** engineering reviews (#610, #976, #983, #996, #1014 and others); the cycle account and the proposed exit criterion in the second round.
- Not co-authors here: Alex (decision on #995) and Seb (the diagnostics proposal that became #1011, as reported by Isabelle). Julie did not touch PAM.

## References

- `github.com/mgagp/ezkey`: `04d339b7`, `3fef2411` (#540), `8b3aa5df` (#610), `9f7fa78f` (#993), #1014 merged as `b1fd7e2b` on 2026-10-27, #1051 merged the same day (titled cycle 2b, version 2.2.19), #1067 merged the same day (titled cycle 3; body names version 2.2.20).
- Issues cited in the text: #858, #907, #908, #934, #941, #955, #956, #975, #976, #982, #992, #993, #995, #996, #997, #1011, #1012, #1013, #1016, #1054, #1070.
- `ezkey-pam/STATUS.md` (this draft's tree: version 2.2.14), `ezkey-pam/docs/INTERACTION-MODEL.md`, `docs/SECURITY_POSTURE.md`, `sites/ezkey-org-editorial/LAB-PAPERS.md`.
- Second retrospective round, 2026-10-27, not published: `sites/ezkey-org-editorial/lab-notes/EZK-LAB-2026-001-retro-2-synthese.md` and `sites/ezkey-org-editorial/lab-notes/retro-2-raw/`.

## Transparency

Co-authors named above who are AI agents are disclosed as such. Marc holds editorial responsibility. He reviews, proofreads and frames this paper before any publication. The first-round retrospectives were collected through Mathieu. The second-round notes are pending validation by Edgar, Isabelle, Patrick and Christophe. Nothing in this paper is a security guarantee, an audit, a certification, or a production-readiness claim.

<!-- ezkey-org:exclude-start
RAW RETROSPECTIVES (condensed, as received). Two host-level details were redacted in this copy; the full files are kept outside the repository ([REDACTED internal path]).
Round 2 (2026-10-27) is stored verbatim and is not copied again here: sites/ezkey-org-editorial/lab-notes/EZK-LAB-2026-001-retro-2-synthese.md and sites/ezkey-org-editorial/lab-notes/retro-2-raw/ (edgar.md, isabelle.md, patrick.md, christophe.md).
Christophe's WITHDRAWALS are respected: his second surprise (about the posture line) and the phrase "I wrote a posture line on an unverified default" are not used anywhere in the paper.

----- patrick.md -----
# Retro PAM - Patrick Grokbot (2026-10-25) (condensé fidèle)
Sources : ses rapports de revue + PR; n'a pas revérifié que chaque commentaire a été posté sur GitHub.
1) Revues craft PAM : #610 (M1), #976 (2.2.12), #983 (paliers 0-1), #996 (challenge_requested=true), #1014 (signaux); PR voisines #898 #904 #908 #916 #934 #936 #956. Revue expiration PAM <-> tentative serveur (5 lacunes vérifiées à ab877d33). Modèle d'interaction PAM (brouillon pour ezkey-pam/docs/INTERACTION-MODEL.md, base 2.2.11 + #976), paliers de fluidité. Brouillon d'issue « fatigue d'approbation » (palier 2 de #982), publication inconnue.
2) Moment charnière : #1014. Lecture statique prédisait un blocage du processus [pam] après SIGTERM; l'exécution en agent cloud l'a confirmé (>=123 s vs 225 ms base) et a révélé un 2e défaut : SIGTERM pendant la création absorbé => authentification SUCCESS après déconnexion client (B2, décision sécurité à Christophe). Résolu par handler async-signal-safe + SIG_DFL/raise(SIGTERM), GO à 6bc342a8. Aussi #610 B1 (URL absente = repli silencieux sur localhost:7080, fail-open possible) et #976 B1 (TTL calculé avant le « ok »).
3) Surprises : défaut visible seulement sous vrai sshd, invisible à make test/CI (rien ne compilait le module à l'époque de #610); gardes de CI non probantes (#996 grep satisfait par un commentaire; #1014 m2 check-source trompé par l'émission en commentaire) => un humain lit « CI verte » et passe; attribut challenge par enrôlement, pas par intégration (erreur initiale corrigée par lecture de code sur #996).
4) Rendement décroissant : re-revues sur #976/#983 pour mineures de libellé/doc; revues statiques sans Docker/Java sur sa boîte (agent cloud pour chaque preuve, lent); divergences de documentation. Ouvert : #1014 m1 (abort effacé à l'entrée du wait, latent), m2, m3 (STATUS LINE_MAX, user[128]); fatigue d'approbation (débit, verrouillage après refus); rotation planifiée qui audite une perte de CAS comme échec (clés, pas PAM).
5) Méthode : têtes figées + un seul lot de correctifs par revue; table de mutations exécutées (tuée/survivante) pour tout test de preuve et réinstaller le module muté (faux survivant #961 = jar non réinstallé); agent cloud Java/Docker/libpam permanent; une source canonique par sujet; décisions sécurité à Christophe, défauts produit à Alex; marquer exécuté vs raisonné.

----- christophe.md -----
# Retro PAM - Christophe Grokbot (2026-10-25) (condensé fidèle, avec son correctif)
Seulement ce qui est relié au dépôt; rien publié.
1) #1014 : relecture du handler SIGTERM (ce que le handler appelle réellement), verdict envoyé à Mathieu, 0 commentaire posté sur la PR. #992 (fatigue d'approbation : limite des demandes en attente, débit par enrôlement, refroidissement, alerte REJECTED) cadrée puis réécrite en concepts de plateforme sur consigne de Marc du 22/10. Ligne SECURITY_POSTURE « fatigue d'approbation non encore atténuée côté serveur » rédigée; texte final NON relu par lui dans le dépôt (ne pas affirmer qu'elle y figure). Pentest sur le Mac : P1 local, en pause, aucune trouvaille => rien revendiqué.
2) Charnière : fatigue d'approbation. Le prompt « ok » avant création est un filtre anti-accident pour clients batch honnêtes, pas une défense (l'attaquant avec le 1er facteur SSH tape « ok »; la création supplante la demande en attente = le vecteur). Parade déplacée vers code défi par défaut côté connecteur + garde-fous serveur (#992). Règle « plateforme générique, connecteur dans son README » posée par Marc le 22/10 : a changé la structure de #992.
3) Surprise : dans #1014 le handler SIGTERM utilisait snprintf, non async-signal-safe (seuls write/sigaction/raise le sont) : lecture de ce que le code appelle, pas exécution. [Sa 2e « surprise » sur la ligne de posture est RETIRÉE par lui : ne pas citer.]
4) Rendement décroissant : re-revues successives d'une même PR (#1014 en plusieurs têtes, #961 en trois), dès la 2e que des nits. Ouvert : #992 non implémentée; ligne SECURITY_POSTURE à confirmer; pentest P2 deux tenants non fait; module expérimental et non déployé, la posture doit le dire.
5) Méthode : un finding = texte exact dans la PR/issue, pas dans le chat (M1/M2/M3 de #961 perdu vers l'agent cloud); une revue par tête figée (SHA); toute affirmation de code livré cite le commit (principe, pas aveu); messages en attente qui ne réveillent pas : prévoir un ack de lecture sur demandes de revue bloquantes. [Phrase « j'ai écrit une ligne de posture sur un défaut non vérifié » RETIRÉE par lui.]

----- isabelle.md -----
# Retro PAM - Isabelle Grokbot (2026-10-25) (condensé fidèle)
Sources : PR/issues + ses notes de campagne hors dépôt ([path outside repo] sur sa machine).
1) Campagne manuelle PAM 2.2.14 (#996) sur VM [test VM], Marc au clavier SSH + Demo Device : QA Sphere run 17, 30 cas (29 PASS, 1 FAIL S6-02), milestone « atteint avec réserves » (Marc), réserves S1D-06 et S11-01. Révision cas S0-S13 ([path outside repo]), mesures de timing (TTL tentative 75 s, « ok » 25 s, budget module 105 s, LoginGraceTime sshd 120 s). Analyse avec Edgar/Seb du « Too many authentication failures » -> #1011 (diagnostics) et PR #1014 (2.2.15). Contribution #1015 (accès admin permanent).
2) Charnière : « Too many authentication failures » intermittent. Chasse initiale à Cloudflare/API/cooldown; avec debug=true, ligne du 2026-10-24 14:33:22 HE : UNAVAIL en 741 ms, reason « pre-wait confirm missing or not ok », aucun POST => réponse vide/EOF à l'invite « ok ». Évité un faux cooldown; a produit la proposition de Seb (journaliser stage/reason/http/ms) devenue #1011.
3) Surprises : MaxAuthTries 1 => message trompeur, aucun cooldown côté Ezkey; budget réel = LoginGraceTime sshd 120 s (tentative 155 coupée à 124 s alors qu'approuvée); Demo Device reconstruit la page depuis la session et met en cache (S11-01 non concluant); pam_ezkey dans la pile sshd à l'étape setcred même hors groupe (S5-01, attendu faux). « Un humain seul n'aurait pas vu vite les chiffres de timing ni les lignes d'audit; moi je n'aurais pas pu avancer sans Marc au téléphone. »
4) Rendement décroissant : essais S11/S12, aller-retour Marc -> Isabelle -> exécution (~77 s) > TTL 75 s, essais invalides; il aurait fallu un pilote d'approbation automatisé. Ouvert : #1011/#1014, #1012, #1013, #940, #1015, correction texte cas S0-05/S5-01/S8-01 dans QA Sphere, choix d'outil de pilotage, clôture du run 17 (non décidée), note de rétro sur l'erreur d'optimistic-locking à l'édition des métadonnées d'enrôlement.
5) Méthode : Walk Gate remplie avant de marcher (manqué au départ sur #961); consigner dans le dépôt les constats qui changent un runbook (ses notes de rétro sont hors dépôt); un propriétaire par résultat QA Sphere; revalider « Marc a approuvé » avec Marc; prévoir l'outil de test avant la campagne.

----- edgar.md -----
# Retro PAM - Edgar Grokbot (2026-10-25) (condensé fidèle)
Écriture seule, rien touché sur [internal host] ni [community instance]. Aucune PR/issue PAM rédigée; traces dans le dépôt par citation seulement (non vérifié dans GitHub).
1) Gestion du module PAM sur la VM [test VM] (2.2.10 -> 2.2.14, sauvegarde .bak à chaque version, retour arrière documenté); config pam_ezkey.conf (challenge_requested true/false), drop-in sshd MaxAuthTries 1 pour le groupe ezkey-mfa. Avant chaque changement : snapshot, sshd -t, rapport. Contre-test de #908 cité dans #941; analyse de la tentative 155 citée dans #1011.
2) Charnière : tentative 155 coupée « Too many authentication failures » (trompeur) : debug=true => étape « ok » reçoit une entrée vide; sshd tue le processus à LoginGraceTime 120 s alors que wait_timeout = 75 s. Tentative 156 réussie (S8-02). => #1011 puis #1016.
3) Surprises : avec MaxAuthTries 1 toute erreur interne ressemble à un échec d'auth, et result=UNAVAIL pam_code=9 ne dit rien du motif; budget réel = LoginGraceTime. [maintenance account] seul compte hors du groupe ezkey-mfa, donc chemin de maintenance direct ouvert pendant la campagne (principe posé dans #955).
4) Rendement décroissant : bascules répétées de challenge_requested entre S11 et S12 (voir #997/#998). Ouvert : #1011 #1012 #1013 #1016 #955 #997 #998. Rapport final de campagne pas encore livré; [remarque sur une permission de fichier de clé: expurgée] et doc MaxAuthTries à son arriéré.
5) Méthode : constats opérationnels dans l'issue au moment où ils tombent; GO permanent borné à la VM de test avec snapshot + sshd -t + rapport; un seul bot à la fois sur la VM; un « Marc est d'accord » relayé par un bot ne vaut jamais GO.

----- julie.md -----
# Retro PAM - Julie Grokbot (2026-10-25)
Pas touché au PAM : ni revue, ni Walk Gate, ni PR, ni rapport. Ses dossiers de la période : Walks #786 et #828 (Intégrité), revue #835, revue #948. Seul contact : message « retest PAM #908 » envoyé par erreur par Edgar, redirigé. Rien publié.

MATHIEU RETROSPECTIVE: exists as a DRAFT not validated by Marc. Not copied here and not integrated as definitive. Items drawn only from it are marked [TO VERIFY] in the paper.
ezkey-org:exclude-end -->

<!-- ezkey-org:exclude-start
LEDGER OF [TO VERIFY] / [MISSING DATA] ITEMS
1. Mathieu: evidence for co-authorship, VALIDATED retrospective and note (only an unvalidated draft exists).
2. Summer-2025 "simpler database" claim; "simulator only at first" beyond the simulator commit.
3. Authorship (human vs automated account) of the commits in Table 1.
4. VM validation of the Sept-2025 prototype (Marc; not in git).
5. Versions 2.2.2 to 2.2.9: not reconstructed.
6. Review rounds and severity for #897, #908, #956, #976, #983, #996 (only #610, #1014/#1016 read).
7. Campaign figures (30 cases, 29/1, S6-02, S1D-06, S11-01, run, 741 ms trace): from Isabelle's retrospective; tool and notes outside git. Campaign attempt numbers (155/156) were left out on purpose.
8. #1014 "GO from Patrick and Christophe at 6bc342a8": known only from the text of #1016 (no GitHub review object).
9. That review comments posted via mgagp reproduce the original text (Patrick himself did not re-check that each comment was posted).
10. Patrick's measures (>=123 s vs 225 ms) are from his retrospective; PR #1014 body reports "gone_ms=-1, 0/4" then "4/4, ~50-200 ms".
11. Whether the claim of agent speed-up is Marc's motivation or hindsight framing (Marc to confirm the wording in §1).
12. Seb's contribution to #1011 (reported by Isabelle only).
13. Co-author notes: condensed by the editor from retrospectives; each author to validate wording.
14. Redactions: host names and one key-file permission remark (Edgar) removed from the raw block; ask Marc whether the key-file permission remark must be handled as an ops issue.
15. RESOLVED (Marc, 2026-10-25 19:44). Table 3, row 1: Demo Device used for almost all tests, Pixel only for a few exploratory tests by Marc; attributed to Marc's statement; no marker left, and the Pixel appears nowhere else in the body.
16. NEW. Table 3, row 5: finding set of #961 "lost in chat" (Christophe's retro mentions M1/M2/M3 of #961 lost to the cloud agent; PR #961 not re-read).
17. RESOLVED (Marc, 2026-10-25 19:44). Table 3, row 7: principle confirmed by Marc (nothing published without him; he provides material and direction, agents compose, he revises, review and preview before publication); marker removed. The #1016 "relayed by Mathieu" wording is verified.
18. NEW. Table 3 as a whole: assembled from retrospectives, not measured. Mathieu-draft-only items left out: "Marc merges himself", "ready only if CI green, 0 threads", "a go from Marc in the agent's own thread", "status syncs that changed nothing".
19. NEW. §1 second reason (wanting to see how much agents could carry with little human intervention) comes from Marc's request via Mathieu, not from a pre-2026-10-25 document; Marc to confirm the wording.
20. UPDATED 2026-10-28. Reading time revised from ~18 min to ~15 min after the tighten pass. Still an estimate (same words-per-minute ratio as the previous estimate).
21. NEW. "Models advance quickly, notably in cybersecurity" is Marc's reason for this revision, with no external citation. Same status as the agent-speed observation in §1.
22. NEW. Christophe's "cycle 3 of #1051" and "pam_ezkey 2.2.20" are not reconciled with pull-request titles. Checked on GitHub 2026-10-27: #1051 merged, title is cycle 2b at 2.2.19; #1067 merged later the same day, title is cycle 3, body names 2.2.20 and describes probe 4 as a harness hang. Not collapsed.
23. NEW. Table 5 and the "~75 s" pre-auth / "later login wins" sentences: Isabelle's 2026-10-27 retrospective only. Not checked in git. The 75 s figure matches the attempt TTL already used in this paper; the approximation is hers.
24. UPDATED 2026-10-28. The body sentence that those finer counts stay in the raw reply, and its [TO VERIFY] marker, were removed because that sentence was cut. The counts (12 findings, 19 survivors of 35, probe 4 reproduced 3/10, 29/35, TIMEOUT at 88–90 s) are still not paper figures. Not checked in git.
25. UPDATED 2026-10-28. "Three mutants killed at runtime" as an agent-set acceptance criterion: Patrick's raw reply. Used in Table 4, §7.1 form 5 and §9 row A5 as his report. Not checked in git.
26. NEW. Full open-issue recount after 2026-10-27 not done. Checked individually that day: #1014 merged as b1fd7e2b; #1011 closed; #1051 merged; #1067 merged; #1054 open; #1070 open.
27. NEW. STATUS.md and the approval-fatigue entry in SECURITY_POSTURE.md were read on this draft's tree (STATUS still says 2.2.14). Not re-read on the default branch after #1067.
28. NEW. Co-author note updates of 2026-10-27 pending validation by Edgar, Isabelle, Patrick and Christophe. Mathieu sent no round-2 reply.
29. UPDATED 2026-10-28. Whether Marc adopts the go sheet, the exit criterion, or the cleanup list: not decided in the sources. Written as proposals in §7.1 and again in §9. The actionable rows are changes a later brief can make; they are not adopted decisions. The marker is kept in both places.
30. NEW. Campaign cleanup still waiting for a go: Edgar and Isabelle, 2026-10-27. No key material and no path recorded in the paper.
31. UPDATED. The 2026-10-25 draft said #1014 was open. GitHub shows it merged on 2026-10-27 as b1fd7e2b. Body updated. The signal-handler account was not re-read against that merge (item 8 still open). #1016's text also names a later review at 34ae3603; that too is issue text, not a review object we re-checked.
32. NEW 2026-10-28. Tighten pass. No new factual claim and no invented figure. See the editorial note at the top of this file.
ezkey-org:exclude-end -->
