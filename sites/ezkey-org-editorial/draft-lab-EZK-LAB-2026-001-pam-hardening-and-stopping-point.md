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
EDITORIAL NOTES — first English draft, local working copy. Not in the repository. Not published.

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

Status of this draft: written from the plan (plan-pam.md), Marc's decisions of 2026-10-25, git/GitHub reads, and the
condensed retrospectives in retro/ (Christophe, Edgar, Isabelle, Julie, Patrick). Mathieu's retrospective is NOT in yet.
Marker convention: [TO VERIFY] / [MISSING DATA]. Every marker is listed at the end in an exclude block.
Drafted in EN first; the FR draft exists only as a local working copy and awaits Marc's explicit go before any use.
ezkey-org:exclude-end -->

# Ezkey PAM: hardening an experimental SSH module with agents, and deciding where to stop

**Authors:** Marc Gagnon (editorial responsibility); Christophe Grokbot (security review); Edgar Grokbot (deployment and edge); Isabelle Grokbot (exploratory QA); Mathieu Grokbot (coordination) `[TO VERIFY: Mathieu's evidence and note pending]`; Patrick Grokbot (engineering review)
**Affiliation:** Ezkey Lab
**Identifier:** `EZK-LAB-2026-001`
**Status:** draft
**Date:** 2026-10-25
**Keywords:** PAM, SSH, MFA, passwordless, fail-closed, agent-assisted development, security review, diminishing returns, experimental
**Reading time:** ~22 min

## Key findings

- A prototype of about 900 lines, written in one commit on 2025-09-03 and validated by hand, was enough for Marc to judge that Ezkey's integration model could carry an SSH second factor. It left no automated evidence.
- The repository shows a one-year pause, with no trace in the repository of functional work on PAM. Work resumed on 2026-09-15.
- Between October 19 and 23, 2026, five patch versions (2.2.10 to 2.2.14) landed, and 2.2.15 is an open pull request. Each version fixed a finer behaviour that reviewers, retests or a manual campaign had exposed. Several of these defects were not visible to static reading or to green CI.
- Review findings became smaller from round to round. Four co-authors, in separate retrospectives, each report diminishing returns from about the second round on the same pull request. We did not measure this systematically.
- Marc and the AI team judged the result sufficient as an **experimental** maturity: security is reasonably assured within the tested scope, without being the end of the story. This is not a host-operable module and not a release. Approval fatigue is not mitigated server-side (#992), and 11 issues remain open.

## Abstract

This paper reports an experiment on `ezkey-pam`, a Linux PAM module that adds Ezkey device approval to SSH logins. A first prototype was committed in September 2025. After a year without functional work, it was modernised in September 2026 and then reviewed, retested and corrected in a dense burst in October 2026, with an AI team of reviewers, a QA agent, a deployment agent and a coordinator working with Marc. We describe what was built, what each round of review and testing found, and how the findings evolved. We then describe a stopping point chosen jointly by Marc and the AI team. We claim no production readiness and no security guarantee. The module has never been released, has not run as a host-operable agent on a real host, and has known open issues.

## 1. Introduction and motivation

Marc first saw an SSH login protected by Duo at work. That was new to him `[TO VERIFY with Marc: when]`. The question that followed was whether Ezkey could play the role of that second factor for SSH, using the pieces it already had.

Ezkey is a lab project. It is not backed by a paying sponsor, and the repository's own documents say it is not promised to anyone. The realistic niche we see for it is passwordless, strong-cryptography protection of internal applications for a small internal staff. Going beyond that would first require SOC 2-style operational discipline. That is a prerequisite, not a plan: `docs/SECURITY_POSTURE.md` describes SOC 2 as a reference vocabulary and not a certification target, and states that Ezkey makes no SOC 2 equivalence claim.

There is also a second reason for the experiment. In recent months Marc has had the impression that autonomous agents have become visibly faster at finding and fixing vulnerabilities. This is his personal observation. We cite no outside source for it and we did not measure it. It shaped the question we were curious about: what happens to the balance between budget, time, effort and value when a small experimental module is examined by several agents, and where is it reasonable to stop?

This paper says what happened and what was measured. It does not claim to say what it means. The module remains experimental, and nothing here is a security guarantee.

## 2. Hypotheses

These hypotheses were **formulated after the fact**. None was written down before the work started. We state them to make the reading explicit, not to suggest that the experiment was designed around them.

- **H1.** The existing pieces of the protocol (authentication, API, integration API, device) were enough to build a complete SSH approval flow.
- **H2.** An AI team with cross-review can bring a small C module to a point where review findings become minor.
- **H3.** Beyond some point, another review round costs more than the lab-level risk it removes, and that point can be identified.

H1 was tested by the 2025 prototype. H2 and H3 were not tested in a controlled way. We report observations and a judgement.

## 3. Prior state

By September 2025 the platform already had the parts of a full approval flow (Table 1). The dates below come from the git history.

| Element | Evidence | Date |
| --- | --- | --- |
| First repository commit | `51822a89` | 2025-06-12 |
| First protocol version: enrollment and auth-attempts | `407326a9`, `87a3ab73`, `7ce660cb` | 2025-06-24/25 |
| Admin API module | `47db2527` | 2025-07-06 |
| Demo Device | `e532ce95` | 2025-08-10 |
| Simulator application | `988ef115` | 2025-08-19 |
| `/wait` endpoint | `796dd455` | 2025-08-26 |
| SDKs | `15245111`, `bb71c828` | 2025-08-31 |
| First Android app | `fd7d9d6a` | 2025-09-01 |
| **First PAM commit** | `04d339b7` | **2025-09-03** |
| Android proof of concept | `1efda3d1`, `8da533ce` | 2025-09-04 |
| Admin UI | `71e27831` | 2026-03-06 |

*Table 1. Context before and around the first PAM commit.*

So the Demo Device and a first Android app existed before PAM was coded, and an Android proof of concept followed the next day. Only the Admin UI came much later. Marc's recollection is that in summer 2025 the protocol was simpler, with a simulator at first, no Admin UI, and a simpler database. The simulator is visible in the history; the database remark is not checked `[TO VERIFY: database schema in summer 2025]`. Some of these early commits were authored by an automated agent account in git, not by Marc `[TO VERIFY: authorship of each commit in Table 1]`.

## 4. Protocol

**What the module does.** `ezkey-pam` is a C PAM module for SSH keyboard-interactive. It asks the user to confirm with `ok`, creates an auth-attempt through the Integration API (`POST /api/v1/auth-attempts`), optionally shows a two-digit code to match, then waits (`GET …/wait`) while the user approves or rejects on a device. The device is the Demo Device or the Android app.

**Design constraints.** The module fails closed. If the API or the configuration is unusable it returns `PAM_AUTHINFO_UNAVAIL`. There is no `failmode=safe` switch, because that is how MFA modules quietly become optional. The configuration is a root-owned file with mode `0600` or `0400`, with no defaults for the API URL and no environment variables. A documented caveat remains: a `sufficient` PAM stack can turn the module into a fail-open one.

**Environment.** A Rocky Linux container image served for builds and demos. From October 2026 a dedicated test VM, running a real sshd with the module installed, was also used. The test VM is where several findings appeared. We deliberately give no host names or addresses.

**Who did what in the process.**
- Marc decided, approved, and typed at the SSH keyboard during manual campaigns.
- Mathieu coordinated and relayed messages and review results.
- Patrick wrote engineering reviews. Christophe wrote security reviews.
- Edgar operated the test VM under a bounded standing permission (snapshot, `sshd -t` check, written report, one bot on the VM at a time).
- Isabelle ran manual QA campaigns.

**How reviews reached GitHub.** The reviews attributed to Patrick and Christophe reach the repository through **Marc's GitHub account**: the comments are posted from `mgagp`, and the text of each comment says which co-author wrote it. One issue says in so many words that the reviews were relayed by Mathieu. Squash-merge commits also carry Marc's name. In this paper, an attribution to a co-author rests on the text of the comment or on the co-author's retrospective, not on the GitHub identity `[TO VERIFY: whether all review comments reflect the original review text]`.

**Tests.** The module has a `make test` target, shell test scripts for configuration security, challenge, timeouts, retry behaviour and diagnostics, SSH signal probes that run against a real sshd, and a CI job. We did not re-run them for this paper. Counts quoted below come from the repository documents and pull requests.

## 5. Course of work and iterations

### 5.1 The prototype, September 2025

The first PAM commit, `04d339b7` (2025-09-03, 9 files, 919 added lines), is described in its own message: a basic hard-coded PAM module running in Rocky Linux in Docker, where, after the SSH password, the demo mobile device must confirm access with Ezkey. According to Marc, it was also run on a VM, and validated by hand only. It confirmed to him that the product and its integration model could work for this case `[TO VERIFY: VM test; no automated evidence is in git]`.

### 5.2 A one-year pause

After that, there is a one-year pause, with no trace in the repository of functional work on PAM. The `ezkey-pam` folder received only four mechanical commits between 2025-09-03 and 2026-09-15: a port change (`07746bb8`, 2026-02-15), a move to Rocky Linux 10 (`90b16e57`, 2026-04-11), file permissions (`83758319`, 2026-05-25), and documentation hygiene (`b19eca7d`, 2026-08-15). The Admin UI was built in the meantime (2026-03-06). We make no claim about what Marc did or thought during this period beyond what git shows.

### 5.3 Modernisation and a first milestone, September to October 2026

Pull request #540 (`3fef2411`, 2026-09-15, 20 files, +1658/−606) modernised the module. Pull request #610 (`8b3aa5df`, "M1") followed: the branch was opened on 2026-09-22 and merged on 2026-10-17 (38 files, +3767/−332). M1 brought observability, a fail-closed configuration, a two-digit challenge and an authentication time budget.

The reviews of #610 are a typical example of the process. Patrick's first review returned **CHANGES** with a single blocking finding: a missing API URL did not fail closed, because the configuration code pre-filled `http://localhost:7080`, so a configuration without a URL would have sent the integration credentials in clear to a local port where another local user could answer `ACCEPTED`. Christophe's review returned minor findings only. After corrections, Patrick re-reviewed twice, with minor findings and then a **GO** on 2026-10-17 once CI, including the module job, was green. This is a review comment sequence quoted from the pull request, relayed through Marc's account.

### 5.4 The October burst, 2026-10-19 to 2026-10-24

Table 2 lists the patch versions of this period. Each row has the same shape: something was observed that the previous version did not handle, and a fix followed.

| Version | Landed | Change | What exposed it |
| --- | --- | --- | --- |
| 2.2.10 | 2026-10-20 | No orphan pending request after Deny/Expire retry (#907, #908); `Match … MaxAuthTries 1` made mandatory in the docs (#934) | Edgar's retest of #908: without the `Match` rule, three requests were created |
| 2.2.11 | 2026-10-21 | Waiting message shown before the device wait (#941, #956) | Edgar: OpenSSH buffers bare `pam_info` text until the next prompt |
| 2.2.12 | 2026-10-21 | Confirm-before-create (`ok`) and display of the enrollment challenge (#975, #976) | Isabelle (#975); Patrick's review found a TTL computed before the `ok` |
| 2.2.13 | 2026-10-22 | Interaction fluidity tiers 0–1 (#982, #983) | Follow-up to #976; tiers 2 and later remain open in #982 |
| 2.2.14 | 2026-10-22 | `challenge_requested=true` by default (#995, #996) | Decision by Alex and Marc, confirmed 2026-10-22 per `STATUS.md` |
| 2.2.15 | not merged | Richer failure diagnostics, SIGTERM handling, signal probes (#1011, #1014) | Manual campaign on 2.2.14 (Isabelle, Edgar); engineering and security reviews |

*Table 2. Patch versions in October 2026. Versions 2.2.2 to 2.2.9 are not reconstructed `[TO VERIFY]`.*

Between these, #992 (approval fatigue: a limit on pending requests, rate per enrollment, cooldown, alert on denial) was scoped and then rewritten in generic platform terms on Marc's instruction of 2026-10-22. The accompanying documentation commit `9f7fa78f` (#993) states the limit plainly. #992 itself is not implemented.

Pull request #1014, the one currently open, is worth describing because it shows how findings kept getting finer. Patrick's reading of the code predicted that the PAM child process could hang after a SIGTERM. Running it in a cloud agent confirmed this, and revealed a second defect: a SIGTERM during request creation was absorbed, and authentication could then succeed after the client had gone. Christophe, reading the handler, found it called a function that is not async-signal-safe. The head commit (`6bc342a8`) replaced the handler with one that writes to the log device, restores the default signal action and re-raises the signal, with four signal probes against a real sshd that failed on the earlier head and pass on this one. Patrick and Christophe both returned GO on that head, which we know from the text of #1016, not from a GitHub review. The PR is still open.

### 5.5 Misalignments and frictions

We list what the co-authors reported. We did not find any disagreement between two co-authors in the retrospectives collected so far. Mathieu's retrospective is still to come `[MISSING DATA]`.

- **Scope of #992.** Marc's rule of 2026-10-22, "generic platform, connector described in its own README", changed how Christophe had framed approval-fatigue controls (Christophe).
- **"Marc agreed" relayed by a bot.** Both Edgar and Isabelle say it is not a go. They now confirm it with Marc directly.
- **Timing of manual retests.** Isabelle describes a loop of Marc, Isabelle and execution that took about 77 seconds, longer than the 75-second attempt TTL, so some trials were invalid. She says an automated approval pilot should have been in place before the campaign.
- **Messages that do not wake.** Christophe says a pending message does not wake a bot, so a blocking review request needs a read acknowledgement.
- **A stray message.** A message about the retest of #908 was sent to Julie by mistake and redirected. Julie did not touch PAM (see §10).

## 6. Results

### 6.1 Review rounds

| Id | Pull request | Head | Verdict sequence | Source |
| --- | --- | --- | --- | --- |
| R1 | #610 (M1) | `932c83f4` → `5d743589` → `ca86a1c2` | Patrick CHANGES (1 blocker) · Christophe MINOR · Patrick MINOR · Patrick GO | PR comments of 2026-10-16/17 |
| R2 | #1014 (2.2.15) | `8272d360` → `6bc342a8` | CHANGES from engineering and security · then GO from both at `6bc342a8` · then #1016 with non-blocking follow-ups | PR body; #1016 text |

*Table 3. Review rounds we could read.* Other pull requests (#897, #908, #956, #976, #983, #996) also had reviews; we did not read them all `[TO VERIFY: rounds and severity]`. Because of this, we cannot give counts of findings per round.

The follow-ups in #1016 are small by design: an abort flag whose clearing no probe detects, a check that could be fooled by a comment, two missing table rows, a volatile-versus-double-buffer question, and a default artifact directory. #1016 also records that the documentation must **not** say "never SUCCESS after the client is gone". SUCCESS stays possible if a TERM is absorbed during `/wait` after the ACCEPTED answer has already arrived; the result then goes to an sshd whose client has left, which #1016 says is not an elevation. The issue asks to document it that way.

### 6.2 Manual campaign on 2.2.14

Isabelle ran a manual campaign on version 2.2.14 (#996) on the test VM, with Marc at the SSH keyboard and on the Demo Device. It was recorded in a test-management tool outside the repository: 30 cases, 29 passed and 1 failed (S6-02, the cancel-and-audit case now tracked in #1012). Marc recorded the milestone as "reached with reservations", the reservations being S1D-06 and S11-01. `[TO VERIFY: figures come from Isabelle's retrospective; the tool and her notes are not in the repository.]`

The most useful result of the campaign was a misleading symptom. With `MaxAuthTries 1`, nearly any internal error appears to the user as "Too many authentication failures". With `debug=true`, a trace on 2026-10-24 showed UNAVAIL after 741 ms, with reason "pre-wait confirm missing or not ok" and no POST sent to the API. The `ok` prompt had received an empty answer. The hunt had started with Cloudflare, the API and a presumed cooldown, so the trace avoided a wrong diagnosis. Edgar's analysis also found that the real time budget is sshd's `LoginGraceTime` (120 s), not the module's wait timeout (75 s). These led to #1011, #1014 and #1016.

### 6.3 Open items

| Id | Issue | Subject |
| --- | --- | --- |
| R3 | #858 | Signature (receipt) on the Integration API `/wait` response, for host hardening |
| R4 | #955 | Phased introduction on the community instance (`status:needs-challenge`) |
| R5 | #979 | Make the B2b `cancel=ok` check a real assertion, or rename it as an observation |
| R6 | #980 | Stabilise EPEL mirror access in CI and document the pin |
| R7 | #982 | Interaction fluidity tiers (tiers 0–1 done in #983) |
| R8 | #992 | Pending-request policy, rate per enrollment, cooldown, alert on denial (platform) |
| R9 | #997 | Challenge code on user request when `challenge_requested=false` |
| R10 | #1011 | Diagnostics on failure (addressed by open PR #1014) |
| R11 | #1012 | Cancel and audit when the SSH client is cut (S6-02) |
| R12 | #1013 | Remaining test coverage (macOS/Linux, SELinux Enforcing, restart, debug-log rotation) |
| R13 | #1016 | Follow-ups from the #1014 reviews |

*Table 4. Eleven open issues on 2026-10-25, checked on GitHub, plus open PR #1014.*

### 6.4 What we did not measure

We have no data on cost (money, compute, agent runs), on human time, or on the proportion of work done by each party. Nothing in the repository records it `[MISSING DATA]`. Where we use words like "dense" or "fast" we refer only to dates and counts visible in git: 2.2.10 to 2.2.14 landed between October 19 and 23, and 2.2.15 has been open since October 24.

## 7. Discussion

### 7.1 A fractal image

When we looked closer at the module, we found more detail, and finer edge cases. Each fix opened a new, smaller question. In the order they appeared, as we read them:

1. A missing URL silently fell back to a local default (#610, Patrick).
2. Without a `Match … MaxAuthTries 1` rule, SSH retries multiplied requests (#908 retest, Edgar).
3. OpenSSH holds back informational text until the next prompt (#941, Edgar).
4. The challenge code was not shown when the connector had not asked for it (#975, Isabelle).
5. The default for the challenge had to be decided (#995, Alex and Marc).
6. Approval fatigue: someone holding the first SSH factor can loop connections (#992, documented, not mitigated server-side).
7. A killed process, a hang, a late SUCCESS (#1011, #1012, #1014).
8. A test that a mutation could survive (#1016).

This sequence is our reading. The causal links are interpretation and were not tested. In our reading, the last items on the list (#1016) concern the strength of tests and diagnostics, and no longer the overall approach of the module.

### 7.2 Budget, time, effort and value

Having several agents review and retest the same module changes the balance between these four, and we think it is a different balance from working alone. What git shows is limited: five patch versions in a few days, each reviewed on a frozen head, with a cloud agent needed whenever a claim had to be proved by running the code (Patrick says this was slow). We have no figures for money, compute or human time, and none for how a person working alone would have fared `[MISSING DATA]`. The honest summary is qualitative: more review, more often, at a pace Marc could follow, at a cost we have not measured. Agent reviews also need their own control. Several findings came from running the code and not from reading it, and a green CI check was not always proof (see §9).

### 7.3 A decided stopping point

As of 2026-10-25 Marc and the AI team agree to treat the result as sufficient for an **experimental** maturity. Our wording is that security is **reasonably assured within the tested scope, without being the end of the story**. We chose it for four reasons that we can point to:

- the last findings were minor (#1016), and the remaining ones are named in open issues;
- four co-authors report diminishing returns, each in a different form (Christophe on repeated re-reviews of one pull request, Patrick on documentation and wording re-reviews, Edgar on repeated switching of `challenge_requested` during the campaign, Isabelle on retests invalidated by timing);
- the known residual risks are documented, not hidden (approval fatigue, the `/wait` signature, the `sufficient` caveat);
- continuing has a cost, and we have no evidence that it would change the lab-level risk.

The decision does **not** mean that the module is host-operable. `ezkey-pam/STATUS.md` still says it is not a host-operable MFA agent on a real host, that the sshd cutover and the TLS and dogfood slices are not done, and that the experimental status holds pending Marc's host validation. `docs/SECURITY_POSTURE.md` lists PAM among undeployed lab surfaces. Moving to the community instance (#955) would require redoing the assessment.

### 7.4 What this does not tell us

We do not know whether a longer series of reviews would have found something serious. We do not know how a person reviewing alone would have compared. We stopped at a point we found reasonable, and that is a judgement.

## 8. Limitations

- The module is experimental, has never been released and has no PAM tag. It has not run as a host-operable agent on a real host. Slices #4 (TLS and dogfood) and #5 (host sshd cutover) are not done.
- Approval fatigue is not mitigated server-side (#992). The module-side mitigation is the two-digit challenge by default (#995). The `/wait` response signature is deferred (#858). A `sufficient` PAM stack can be fail-open. A "never SUCCESS after the client is gone" guarantee does not hold (#1016).
- One operator, one environment family (Docker and Rocky; one test VM). OpenSSH 9.9's per-source penalties were disabled in the lab only.
- No external audit, no certification, no SOC 2 claim. A local penetration test was started by Christophe and paused without findings; we claim no result from it.
- Reviews are text. We did not re-run every test for this paper. The review comments reached GitHub through Marc's account, and we did not verify that they all reproduce the original text.
- Versions 2.2.2 to 2.2.9, some review rounds, and the campaign's figures are not reconstructed or are outside the repository. Cost and time are not measured.
- The hypotheses were formulated after the work.

## 9. Lessons learned

- **No default for what must fail.** The B1 finding in #610 was a default URL that made a missing setting look valid.
- **Test with the real daemon.** Several defects (retries, buffered text, the signal hang) appeared under a real sshd and not under `make test` or CI (Patrick).
- **Do not trust a green check without knowing what it checks.** In #996 a grep was satisfied by a comment, and in #1014 a source check was fooled the same way. Patrick now asks for a table of executed mutations, killed or surviving, for any test used as proof.
- **A prompt is a filter, not a defence.** The `ok` before creation protects honest batch clients from accidents. Someone with the first SSH factor types `ok` too (Christophe).
- **Write findings where they will be found.** A finding is the exact text in the PR or issue, not a chat message. Review a frozen head SHA. Any claim that code was delivered cites the commit (Christophe, Patrick).
- **Prepare the test tool before the campaign.** Isabelle needed an automated approval pilot and did not have one.
- **A relayed approval is not an approval.** Confirm with Marc (Edgar, Isabelle).
- **Record operational findings when they happen**, in the issue, and keep a bounded standing permission for the test host (Edgar).

## 10. Contributors' retrospective

The retrospective followed the method in `LAB-PAPERS.md`. Each co-author received the same questions through Mathieu: contributions that landed, a turning point, surprises, diminishing returns, open items and method. Their condensed answers are kept in an editorial exclude block of the draft and are not published. Below is a synthesis, with attribution.

**Contributions that landed.** *Patrick*: engineering reviews of #610, #976, #983, #996 and #1014 and of neighbouring pull requests; a review of the expiry model between PAM and the server; a draft interaction model for `ezkey-pam/docs/INTERACTION-MODEL.md`. *Christophe*: the security review of #1014 and the scoping of #992. *Isabelle*: the manual campaign on 2.2.14, a revision of cases S0–S13, timing measurements (attempt TTL 75 s, `ok` prompt 25 s, module budget 105 s, `LoginGraceTime` 120 s), and the analysis that led to #1011. *Edgar*: managing the module on the test VM from 2.2.10 to 2.2.14, with a backup at every version and a documented rollback; configuring the challenge and the `MaxAuthTries 1` drop-in; the retest cited in #941. *Mathieu*: coordination and relaying `[MISSING DATA: Mathieu's retrospective pending]`.

**Turning points reported.**
- *Patrick* chose #1014: static reading predicted a hang, execution confirmed it (the baseline was 225 ms and the observed delay at least 123 s) and revealed a second defect.
- *Christophe* chose approval fatigue: the `ok` prompt is an anti-accident filter and not a defence, so the mitigation moved to the challenge code by default and to platform safeguards in #992.
- *Isabelle* and *Edgar* chose the same symptom from two angles: the misleading "Too many authentication failures".

**Disagreements.** None reported between co-authors. See §5.5 for the frictions with Marc's instructions and with process.

**Blind spots reported.** Isabelle's campaign notes are outside the repository. Edgar's final campaign report was not yet delivered. The test tool was not chosen before the campaign (Isabelle). Patrick could not run Docker or Java on his own machine, so each proof required a cloud agent, which was slow. Christophe's pentest, phase 2 (two tenants), is not done. Christophe reports that he drafted the wording about approval fatigue now found in `docs/SECURITY_POSTURE.md` (entry of 2026-10-22), but has not re-read the final text in the repository. We checked separately that this entry exists on `main`.

> **Co-author note — Patrick**
>
> On #1014 I read the SIGTERM path and predicted a hang. A cloud agent ran it: at least 123 seconds against a 225 ms baseline, and a second defect, an authentication that could succeed after the client had left. Neither was visible to `make test` or to CI. I now ask for a table of mutations that were run, killed or surviving, and I mark every claim as executed or reasoned.

> **Co-author note — Christophe**
>
> I reviewed the signal handler by reading what it calls, not by running it. It used `snprintf`, which is not async-signal-safe. On approval fatigue, the `ok` prompt is not a defence: whoever has the first factor types `ok`. So the mitigation moved to the challenge code and to server-side safeguards, which are still not built. From the second round on the same pull request, I found mostly nits.

> **Co-author note — Isabelle**
>
> The intermittent "Too many authentication failures" cost me time because I looked at Cloudflare, the API and a cooldown. A debug trace showed an empty answer to the `ok` prompt and no request sent. I could not have gone on without Marc on the phone, and a person alone would not have seen the timing figures quickly. Our retests were slower than the 75-second TTL; an automated approval pilot was needed.

> **Co-author note — Edgar**
>
> I operated the module on the test VM through five versions, with a snapshot and `sshd -t` before each change. With `MaxAuthTries 1`, any internal error looks like an authentication failure, and `UNAVAIL` with code 9 says nothing about the cause. I reported findings in the issue as they happened. A relayed "Marc agrees" is never a go for me. Repeated switching of `challenge_requested` is where I saw diminishing returns.

> **Co-author note — Mathieu**
>
> `[PLACEHOLDER — Mathieu's retrospective is not in the folder yet. To be added: coordination, relaying of reviews, the process view, and a note of 80–150 words. Do not publish with this placeholder.]`

*Each note is condensed from the co-author's retrospective and is to be validated by its author before publication `[TO VERIFY]`.*

**Julie.** Julie Grokbot is not a co-author. She did not touch PAM: no review, no Walk Gate, no pull request, no report. The only contact was a retest message sent to her by mistake and redirected.

## 11. Next steps

None is a commitment. The open items in Table 4 fall into five groups: platform security (#992, #858), interaction (#982, #997), operations and introduction (#955, #1011), tests and CI (#979, #980, #1013) and cancellation and audit (#1012, #1016). Pull request #1014 is open. Before any move to the community instance, the assessment would have to be redone, and Marc's host validation (the sshd cutover and TLS slices) is still outstanding.

## Who did what

- **Marc:** editorial responsibility; decisions and approvals; SSH keyboard in the manual campaigns; GitHub account used for the commits and review comments.
- **Christophe:** security review (#1014 and others), scoping of #992.
- **Edgar:** test-VM operations from 2.2.10 to 2.2.14; retests (#908, #941); diagnostics analysis cited in #1011.
- **Isabelle:** manual campaign on 2.2.14; timing measurements; the analysis behind #1011; challenge display (#975).
- **Mathieu:** coordination and relaying `[TO VERIFY]`.
- **Patrick:** engineering reviews (#610, #976, #983, #996, #1014 and others); interaction-model draft.
- Others named in the repository but not co-authors here: Alex (decision on #995) and Seb (the diagnostics proposal that became #1011, as reported by Isabelle). Julie did not touch PAM.

## References

- Commits and pull requests cited above, in `github.com/mgagp/ezkey`: `04d339b7`, `3fef2411` (#540), `8b3aa5df` (#610), `14933743` (#908), `88bf0d78` (#934), `5b541793` (#956), `9076cb4c` (#976), `d3b8c2bf` (#983), `9f7fa78f` (#993), `83246404` (#996), PR #1014.
- Issues #858, #955, #979, #980, #982, #992, #995, #997, #1011, #1012, #1013, #1016.
- `ezkey-pam/README.md`, `ezkey-pam/STATUS.md`, `ezkey-pam/docs/INTERACTION-MODEL.md`.
- `docs/SECURITY_POSTURE.md`.
- `sites/ezkey-org-editorial/LAB-PAPERS.md`.

## Appendix A — Tests named in the repository

<!-- Published HTML: wrap appendices in collapsed <details> -->

`make test` (source check, compile, file-log, sanitiser and challenge harnesses); `test-observability.sh`, `test-conf-security.sh`, `test-challenge.sh`, `test-timeouts.sh`, `test-f9-retry.sh`, `test-install-sshd-warn.sh`, `test-signal-probes.sh`; the CI job `ezkey-pam`. Which of them were executed, and with what result, is documented in the pull requests and in `STATUS.md`. We did not re-run them.

## Transparency

Co-authors named above who are AI agents are disclosed as such. Marc holds editorial responsibility for this Lab Paper. The condensed retrospectives were collected through Mathieu. Nothing in this paper is a security guarantee or a production-readiness claim.

<!-- ezkey-org:exclude-start
RAW RETROSPECTIVES (condensed, as received). Two host-level details were redacted in this copy; the full files are kept outside the repository ([REDACTED internal path]).
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

MATHIEU RETROSPECTIVE: NOT RECEIVED at the time of writing. Re-check the retrospectives folder ([REDACTED internal path]) for mathieu.md.
ezkey-org:exclude-end -->

<!-- ezkey-org:exclude-start
LEDGER OF [TO VERIFY] / [MISSING DATA] ITEMS
1. Mathieu: evidence for co-authorship, retrospective, note (MISSING).
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
ezkey-org:exclude-end -->
