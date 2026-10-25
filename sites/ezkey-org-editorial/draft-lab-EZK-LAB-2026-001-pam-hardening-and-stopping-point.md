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
EDITORIAL NOTES — English draft, revision 2, local working copy. Not in the repository. Not published.

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

Status of this draft (revision pass of 2026-10-25, evening): tightened from ~5,200 to ~4,000 words of body; new short section 6.4
on agent autonomy (Table 3). Based on the plan (plan-pam.md), Marc's decisions of 2026-10-25, git/GitHub reads and the condensed
retrospectives in retro/ (Christophe, Edgar, Isabelle, Julie, Patrick). Mathieu's retrospective exists only as a DRAFT not
validated by Marc: it is NOT integrated; anything drawn only from it is marked [TO VERIFY]; his co-author note stays a placeholder.
Marker convention: [TO VERIFY] / [MISSING DATA]; every marker is listed at the end in an exclude block.
Drafted in EN first; the FR draft is a local working copy and awaits Marc's explicit go before any use.
ezkey-org:exclude-end -->

# Ezkey PAM: hardening an experimental SSH module with agents, and deciding where to stop

**Authors:** Marc Gagnon (editorial responsibility); Christophe Grokbot (security review); Edgar Grokbot (deployment and edge); Isabelle Grokbot (exploratory QA); Mathieu Grokbot (coordination) `[TO VERIFY: Mathieu's evidence and validated note pending]`; Patrick Grokbot (engineering review)
**Affiliation:** Ezkey Lab
**Identifier:** `EZK-LAB-2026-001`
**Status:** draft
**Date:** 2026-10-25
**Keywords:** PAM, SSH, MFA, fail-closed, agent-assisted development, agent autonomy, security review, diminishing returns, experimental
**Reading time:** ~15 min

## Key findings

- A prototype of about 900 lines, committed on 2025-09-03 and validated by hand, was enough for Marc to judge that Ezkey's integration model could carry an SSH second factor. It left no automated evidence.
- The repository shows a one-year pause, with no trace in the repository of functional work on PAM. Work resumed on 2026-09-15.
- Between October 19 and 23, 2026, five patch versions (2.2.10 to 2.2.14) landed, and 2.2.15 is an open pull request. Several defects behind them were invisible to static reading and to green CI. Review findings became smaller from round to round, and four co-authors report diminishing returns, which we did not measure systematically.
- Marc and the AI team judged the result sufficient as an **experimental** maturity: security is reasonably assured within the tested scope, without being the end of the story. This is not a host-operable module and not a release. Approval fatigue is not mitigated server-side (#992), and 11 issues remain open.
- The experiment also aimed at more agent autonomy. It was partial: at several points a human, or a relay, was still needed (§6.4).

## Abstract

This paper reports an experiment on `ezkey-pam`, a Linux PAM module that adds Ezkey device approval to SSH logins. A prototype was committed in September 2025. After a year without functional work, the module was modernised in September 2026, then reviewed, retested and corrected in a dense burst in October 2026 by an AI team (reviewers, a QA agent, a deployment agent, a coordinator) working with Marc. We describe what was built, what each round found, where agent autonomy reached its limits, and a stopping point chosen jointly by Marc and the AI team. We claim no production readiness and no security guarantee. The module has never been released and has not run as a host-operable agent on a real host.

## 1. Introduction and motivation

Marc first saw an SSH login protected by Duo at work, which was new to him `[TO VERIFY with Marc: when]`. The question that followed was whether Ezkey could play the role of that second factor for SSH, with the pieces it already had.

Ezkey is a lab project, not promised to anyone. The realistic niche we see is passwordless, strong-cryptography protection of internal applications for a small internal staff. Going further would first require SOC 2-style discipline: a prerequisite, not a plan. `docs/SECURITY_POSTURE.md` treats SOC 2 as a reference vocabulary, not a certification target, and makes no equivalence claim.

Two further reasons shaped the experiment. First, Marc has the impression that autonomous agents have become visibly faster at finding and fixing vulnerabilities. This is his personal observation; we cite no outside source and did not measure it. It made us curious about the balance between budget, time, effort and value when several agents examine a small module, and about where to stop. Second, we wanted to see how much of that work agents could carry with little human intervention (§6.4).

This paper says what happened and what was measured, not what it means. Nothing here is a security guarantee.

## 2. Hypotheses

These were **formulated after the fact**; none was written down before the work started. We state them to make the reading explicit, not to suggest the experiment was designed around them.

- **H1.** The existing pieces (authentication, APIs, device) were enough to build a complete SSH approval flow.
- **H2.** An AI team with cross-review can bring a small C module to a point where review findings become minor.
- **H3.** Beyond some point, another review round costs more than the lab-level risk it removes, and that point can be identified.

H1 was tested by the 2025 prototype. H2 and H3 were not tested in a controlled way; we report observations and a judgement.

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

Marc recalls that in summer 2025 the protocol was simpler, with a simulator at first, no Admin UI and a simpler database. The simulator is visible in the history (`988ef115`, 2025-08-19); the database remark is not checked `[TO VERIFY: database schema in summer 2025]`. Some early commits were authored by an automated agent account, not by Marc `[TO VERIFY: authorship of each commit in Table 1]`.

## 4. Protocol

**The module.** `ezkey-pam` is a C PAM module for SSH keyboard-interactive. It asks the user to confirm with `ok`, creates an auth-attempt through the Integration API (`POST /api/v1/auth-attempts`), optionally shows a two-digit code to match, then waits (`GET …/wait`) while the user approves or rejects on a device (the Demo Device or the Android app).

**Design constraints.** It fails closed: an unusable API or configuration returns `PAM_AUTHINFO_UNAVAIL`, and there is deliberately no `failmode=safe`. The configuration is a root-owned file (`0600` or `0400`), with no default API URL and no environment variables. A documented caveat remains: a `sufficient` PAM stack can make the module fail-open.

**Environment.** A Rocky Linux container for builds and demos and, from October 2026, a dedicated test VM with a real sshd. Several findings appeared only there. We give no host names or addresses.

**Roles.** Marc decided and approved, and typed at the SSH keyboard in manual campaigns. Mathieu coordinated and relayed; Patrick and Christophe reviewed; Edgar operated the test VM; Isabelle ran manual QA.

**How reviews reached GitHub.** Patrick's and Christophe's reviews reach the repository through **Marc's GitHub account**: the comments are posted from `mgagp`, and the text of each says which co-author wrote it. An issue (#1016) says reviews were relayed by Mathieu, and squash-merge commits carry Marc's name. In this paper, attribution rests on the text of the comment or on the co-author's retrospective, not on GitHub identity `[TO VERIFY: that comments reflect the original review text]`.

**Tests.** A `make test` target, shell test scripts, signal probes against a real sshd and a CI job. We did not re-run them for this paper; figures below come from the repository and pull requests.

## 5. Course of work

### 5.1 Prototype and pause

The first PAM commit, `04d339b7` (2025-09-03, 9 files, 919 added lines), describes itself as a basic hard-coded module in Rocky Linux under Docker, where after the SSH password the demo device must confirm access. According to Marc it was also run on a VM and validated by hand only `[TO VERIFY: VM test; no automated evidence in git]`.

Then came a one-year pause, with no trace in the repository of functional work on PAM. The folder received only four mechanical commits: a port change (`07746bb8`), a move to Rocky Linux 10 (`90b16e57`), file permissions (`83758319`) and documentation hygiene (`b19eca7d`). We make no claim about what Marc did or thought in that period beyond what git shows.

### 5.2 Modernisation and a first milestone

Pull request #540 (`3fef2411`, 2026-09-15) modernised the module. Pull request #610 (`8b3aa5df`, "M1"), opened on 2026-09-22 and merged on 2026-10-17 (38 files, +3767/−332), added observability, fail-closed configuration, the two-digit challenge and a time budget.

Its reviews are a typical example. Patrick's first review returned **CHANGES** with one blocking finding: a missing API URL did not fail closed, because the code pre-filled `http://localhost:7080`. A configuration without a URL would have sent integration credentials in clear to a local port where another local user could answer `ACCEPTED`. Christophe's review returned minor findings only. Patrick re-reviewed twice and returned **GO** on 2026-10-17 once CI was green.

### 5.3 The October burst

| Version | Landed | Change | What exposed it |
| --- | --- | --- | --- |
| 2.2.10 | 2026-10-20 | No orphan pending request after Deny/Expire retry (#907, #908); `Match … MaxAuthTries 1` mandatory in docs (#934) | Edgar's retest of #908: without `Match`, three requests were created |
| 2.2.11 | 2026-10-21 | Waiting message shown before the device wait (#941, #956) | Edgar: OpenSSH buffers bare `pam_info` text until the next prompt |
| 2.2.12 | 2026-10-21 | Confirm-before-create (`ok`) and enrollment challenge display (#975, #976) | Isabelle (#975); Patrick's review found a TTL computed before the `ok` |
| 2.2.13 | 2026-10-22 | Interaction fluidity tiers 0–1 (#982, #983) | Follow-up to #976 |
| 2.2.14 | 2026-10-22 | `challenge_requested=true` by default (#995, #996) | Decision by Alex and Marc, per `STATUS.md` |
| 2.2.15 | not merged | Richer failure diagnostics, SIGTERM handling, signal probes (#1011, #1014) | Manual campaign on 2.2.14; engineering and security reviews |

*Table 2. Patch versions of October 2026. Versions 2.2.2 to 2.2.9 are not reconstructed `[TO VERIFY]`.*

Meanwhile #992 (approval fatigue: pending-request limit, rate per enrollment, cooldown, alert on denial) was scoped, then rewritten in generic platform terms on Marc's instruction of 2026-10-22. Commit `9f7fa78f` (#993) documents the limit plainly; #992 itself is not implemented.

Pull request #1014, still open, shows how findings kept getting finer. Patrick's reading predicted that the PAM child process could hang after a SIGTERM. A cloud agent confirmed it and revealed a second defect: a SIGTERM during request creation was absorbed, and authentication could then succeed after the client had gone. Christophe, reading the handler, found it called a function that is not async-signal-safe. The head commit (`6bc342a8`) replaced it with a handler that writes to the log device, restores the default action and re-raises the signal, backed by four signal probes against a real sshd that failed on the previous head and pass on this one. Both reviewers returned GO on that head, which we know from the text of #1016, not from a GitHub review `[TO VERIFY]`.

## 6. Results

### 6.1 Review rounds

#610 was reviewed on three heads (`932c83f4`, `5d743589`, `ca86a1c2`), with the verdicts above. On #1014 (`8272d360`, then `6bc342a8`), engineering and security both asked for changes, then both returned GO at `6bc342a8`, and #1016 collected small non-blocking follow-ups. It also records that the documentation must **not** say "never SUCCESS after the client is gone": SUCCESS stays possible if a TERM is absorbed during `/wait` after the ACCEPTED answer has arrived, and #1016 says this is not an elevation. Other pull requests (#897, #908, #956, #976, #983, #996) were reviewed too; we did not read all of them, so we give no counts of findings per round `[TO VERIFY: rounds and severity]`.

### 6.2 Manual campaign on 2.2.14

Isabelle ran a manual campaign on 2.2.14 (#996) on the test VM, with Marc at the SSH keyboard and on the Demo Device. It was recorded in a test-management tool outside the repository: 30 cases, 29 passed and 1 failed (S6-02, cancel and audit, now #1012). Marc recorded the milestone as "reached with reservations" (S1D-06, S11-01) `[TO VERIFY: figures come from Isabelle's retrospective; tool and notes are outside the repository]`.

The most useful result was a misleading symptom. With `MaxAuthTries 1`, almost any internal error shows up as "Too many authentication failures". A debug trace on 2026-10-24 showed UNAVAIL after 741 ms, reason "pre-wait confirm missing or not ok", and no POST sent: the `ok` prompt had received an empty answer. Edgar's analysis also found that sshd's `LoginGraceTime` (120 s), not the module's 75 s wait timeout, bounds a real attempt. This led to #1011, #1014 and #1016.

### 6.3 Open items

Eleven issues were open on 2026-10-25 (checked on GitHub), plus PR #1014: #858, #955, #979, #980, #982, #992, #997, #1011, #1012, #1013 and #1016. They cover platform security (#992 pending-request policy and cooldown, #858 signature on the `/wait` response), interaction (#982, #997), introduction on the community instance (#955), tests and CI (#979, #980, #1013), and cancellation, diagnostics and follow-ups (#1011, #1012, #1016).

### 6.4 Agent autonomy: aimed at, and limits observed

Part of the experiment aimed at letting agents do more of the work with less human intervention. Table 3 lists what we did toward that and where a limit appeared. Sources are the co-authors' retrospectives; it is a list of observations, not a measurement of autonomy.

| Aimed at | Limit observed | Source |
| --- | --- | --- |
| Approval without a person's physical phone: the Demo Device (`e532ce95`) instead of a Pixel `[TO VERIFY: that the Demo Device replaced a Pixel in these tests]` | The 2.2.14 campaign still needed Marc at the SSH keyboard and on the Demo Device; Isabelle says she could not have gone on without Marc on the phone | Isabelle |
| Reviews of a frozen head (a given SHA, one batch of fixes per review) | Re-reviews after about the second head brought mostly minor points | Patrick, Christophe |
| Tests replayed by the agents themselves: retests (#908), signal probes against a real sshd, a cloud agent executing predictions (#1014) | Patrick had no Docker or Java on his own machine, so each proof went through a cloud agent, slowly; some CI guards proved nothing (#996, #1014) | Patrick, Edgar |
| Bounded GOs: a standing permission on the test VM (snapshot, `sshd -t`, report, one bot at a time) | A relayed "Marc agrees" is not a GO; the test VM's maintenance path stayed outside MFA (principle in #955) | Edgar, Isabelle |
| Findings written where they live (PR or issue) | Messages that do not wake an agent (hence read acknowledgements on blocking requests); a finding set from #961 lost in chat `[TO VERIFY]` | Christophe |
| Retests without a person in the loop | The human round trip (about 77 s) exceeded the 75 s attempt TTL, so some trials were invalid; an automated approval pilot was missing | Isabelle |
| Agents posting their own reviews | Reviews reach GitHub through Marc's account and are relayed by Mathieu (#1016); nothing is posted without Marc `[TO VERIFY: Mathieu's draft retrospective only]` | #1016; Mathieu (draft) |

*Table 3. Autonomy aims and observed limits.*

In short, the agents could review, replay and diagnose, but each stage still passed through Marc or a relay at some point. We have no measure of how much of the work this represents `[MISSING DATA]`.

## 7. Discussion

### 7.1 A fractal image

The closer we looked, the finer the edge cases, and each fix opened a smaller question: a missing URL falling back silently (#610); retries multiplying requests (#908); OpenSSH holding back informational text (#941); the challenge default (#995); approval fatigue (#992, documented, not mitigated server-side); a killed process, a hang and a late SUCCESS (#1014); then a test a mutation could survive (#1016). This sequence is our interpretation; the causal links were not tested. In our reading, the last items concern the strength of tests and diagnostics more than the module's overall approach.

### 7.2 Budget, time, effort and value

Having several agents review and retest the same module changes the balance between these four, and we think it differs from working alone. Git shows five patch versions in a few days, each reviewed on a frozen head. We have no figures for money, compute or human time, and none for a person working alone `[MISSING DATA]`. Our summary is qualitative: more review, more often, at a pace Marc could follow, at a cost we did not measure. Where we say "dense" or "fast", we mean only dates and counts visible in git.

### 7.3 A decided stopping point

As of 2026-10-25, Marc and the AI team agree to treat the result as sufficient for an **experimental** maturity. Our wording: security is **reasonably assured within the tested scope, without being the end of the story**. Our reasons:

- the last findings were minor (#1016), and the remaining ones are named in open issues;
- four co-authors report diminishing returns in different forms: repeated re-reviews of one pull request (Christophe), wording and documentation re-reviews (Patrick), repeated `challenge_requested` switching during the campaign (Edgar), retests invalidated by timing (Isabelle);
- known residual risks are documented, not hidden;
- continuing has a cost, and we have no evidence it would change the lab-level risk.

This does **not** mean the module is host-operable. `ezkey-pam/STATUS.md` still says it is not a host-operable MFA agent on a real host, that the sshd cutover and the TLS and dogfood slices are not done, and that the experimental status holds pending Marc's host validation. `docs/SECURITY_POSTURE.md` lists PAM among undeployed lab surfaces. Moving to the community instance (#955) would require redoing the assessment.

## 8. Limitations

- The module is experimental, never released, with no PAM tag, and has not run as a host-operable agent on a real host. Slices #4 (TLS, dogfood) and #5 (host sshd cutover) are not done.
- Approval fatigue is not mitigated server-side (#992); the module-side mitigation is the default two-digit challenge (#995). The `/wait` signature is deferred (#858). A `sufficient` stack can be fail-open, and "never SUCCESS after the client is gone" does not hold (#1016).
- One operator, one environment family (Docker/Rocky, one test VM). No external audit, certification or SOC 2 claim. Christophe's local penetration test was paused without findings; we claim no result from it.
- Reviews are text, and they reached GitHub through Marc's account. Versions 2.2.2 to 2.2.9, some review rounds and the campaign figures are not reconstructed or are outside the repository. Cost and time are not measured.
- The hypotheses were formulated after the work. We do not know whether a longer series of reviews would have found something serious, nor how a person reviewing alone would compare. Stopping was a judgement.

## 9. Lessons learned

- **No default for what must fail.** Finding B1 of #610 was a default URL that made a missing setting look valid.
- **Test with the real daemon.** Several defects appeared under a real sshd, not under `make test` or CI (Patrick).
- **A green check proves only what it checks.** In #996 a grep was satisfied by a comment; in #1014 a source check was fooled the same way. Patrick now asks for a table of executed mutations, killed or surviving, for any test used as proof.
- **A prompt is a filter, not a defence.** Whoever has the first SSH factor types `ok` too (Christophe).
- **Write findings where they will be found, on a frozen head** (Christophe, Patrick), and **prepare the test tool before the campaign**: Isabelle needed an automated approval pilot.

## 10. Contributors' retrospective

Each co-author answered the same questions through Mathieu (what landed, a turning point, surprises, diminishing returns, open items, method). Condensed answers are kept in an editorial exclude block, not published. Contributions are in "Who did what".

**Turning points reported.** *Patrick* chose #1014: static reading predicted a hang, execution confirmed it (baseline 225 ms, observed at least 123 s) and revealed a second defect. *Christophe* chose approval fatigue: the `ok` prompt filters accidents and is not a defence, so mitigation moved to the default challenge and to platform safeguards in #992. *Isabelle* and *Edgar* chose the same symptom from two angles, the misleading "Too many authentication failures".

**Disagreements.** None reported between co-authors. The frictions reported concern Marc's scope rule for #992 (it changed how Christophe had framed the controls), relayed approvals, and retest timing.

**Blind spots reported.** Isabelle's notes are outside the repository, and Edgar's final campaign report was not yet delivered. Christophe drafted the approval-fatigue wording now in `docs/SECURITY_POSTURE.md` (entry of 2026-10-22) but has not re-read the final text; we checked separately that the entry exists on `main`.

> **Co-author note — Patrick**
>
> On #1014 I read the SIGTERM path and predicted a hang. A cloud agent ran it: at least 123 seconds against a 225 ms baseline, and a second defect, an authentication that could succeed after the client had left. Neither was visible to `make test` or to CI. I now ask for a table of mutations that were run, killed or surviving, and I mark every claim as executed or reasoned.

> **Co-author note — Christophe**
>
> I reviewed the signal handler by reading what it calls, not by running it. It used `snprintf`, which is not async-signal-safe. On approval fatigue, the `ok` prompt is not a defence: whoever has the first factor types `ok`. So the mitigation moved to the challenge code and to server-side safeguards, which are still not built. From the second round on the same pull request, I found mostly nits.

> **Co-author note — Isabelle**
>
> The intermittent "Too many authentication failures" cost me time because I looked at Cloudflare, the API and a cooldown. A debug trace showed an empty answer to the `ok` prompt and no request sent. I could not have gone on without Marc on the phone. Our retests were slower than the 75-second TTL; an automated approval pilot was needed.

> **Co-author note — Edgar**
>
> I operated the module on the test VM through five versions, with a snapshot and `sshd -t` before each change. With `MaxAuthTries 1`, any internal error looks like an authentication failure, and `UNAVAIL` with code 9 says nothing about the cause. A relayed "Marc agrees" is never a go for me. Repeated switching of `challenge_requested` is where I saw diminishing returns.

> **Co-author note — Mathieu**
>
> `[PLACEHOLDER — Mathieu's retrospective exists only as a draft not validated by Marc. To be added once validated: coordination, relaying of reviews, the process view, and a note of 80–150 words. Do not publish with this placeholder.]`

*Each note is condensed from the co-author's retrospective and is to be validated by its author before publication `[TO VERIFY]`.*

**Julie.** Julie Grokbot is not a co-author. She did not touch PAM: no review, no Walk Gate, no pull request, no report. The only contact was a retest message sent to her by mistake and redirected.

## 11. Next steps

None is a commitment. The open items of §6.3 remain. Before any move to the community instance, the assessment would have to be redone, and Marc's host validation (sshd cutover, TLS slices) is still outstanding.

## Who did what

- **Marc:** editorial responsibility; decisions and approvals; SSH keyboard in manual campaigns; GitHub account used for commits and review comments.
- **Christophe:** security review (#1014 and others), scoping of #992.
- **Edgar:** test-VM operations from 2.2.10 to 2.2.14, with a backup per version and a documented rollback; retests (#908, #941); diagnostics analysis cited in #1011.
- **Isabelle:** manual campaign on 2.2.14; case revision S0–S13; timing measurements; the analysis behind #1011; challenge display (#975).
- **Mathieu:** coordination and relaying `[TO VERIFY]`.
- **Patrick:** engineering reviews (#610, #976, #983, #996, #1014 and others); expiry-model review; interaction-model draft.
- Not co-authors here: Alex (decision on #995) and Seb (the diagnostics proposal that became #1011, as reported by Isabelle). Julie did not touch PAM.

## References

- `github.com/mgagp/ezkey`: `04d339b7`, `3fef2411` (#540), `8b3aa5df` (#610), `9f7fa78f` (#993), PR #1014; issues #858, #955, #979, #980, #982, #992, #995, #997, #1011, #1012, #1013, #1016.
- `ezkey-pam/STATUS.md`, `ezkey-pam/docs/INTERACTION-MODEL.md`, `docs/SECURITY_POSTURE.md`, `sites/ezkey-org-editorial/LAB-PAPERS.md`.

## Transparency

Co-authors named above who are AI agents are disclosed as such. Marc holds editorial responsibility. The condensed retrospectives were collected through Mathieu. Nothing in this paper is a security guarantee or a production-readiness claim.

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
15. NEW. Table 3, row 1: that the Demo Device replaced a Pixel (physical phone) in these tests; "Pixel" appears in no source I read (Marc's wording via Mathieu).
16. NEW. Table 3, row 5: finding set of #961 "lost in chat" (Christophe's retro mentions M1/M2/M3 of #961 lost to the cloud agent; PR #961 not re-read).
17. NEW. Table 3, row 7: "nothing is posted without Marc" rests on Mathieu's unvalidated draft; only the "relayed by Mathieu" wording of #1016 is verified.
18. NEW. Table 3 as a whole: assembled from retrospectives, not measured. Mathieu-draft-only items left out: "Marc merges himself", "ready only if CI green, 0 threads", "a go from Marc in the agent's own thread", "status syncs that changed nothing".
19. NEW. §1 second reason (wanting to see how much agents could carry with little human intervention) comes from Marc's request via Mathieu, not from a pre-2026-10-25 document; Marc to confirm the wording.
20. NEW. "Reading time ~15 min" is an estimate.
ezkey-org:exclude-end -->
