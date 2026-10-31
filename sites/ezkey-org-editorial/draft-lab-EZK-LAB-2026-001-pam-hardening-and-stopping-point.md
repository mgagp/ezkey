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

Revision 4 = Marc-approved revert of the tighten pass (b22009a0, via a revert commit), small safe gains re-applied by hand,
then a rewrite around one through-line: we let agents harden an experimental PAM module; the weak point was the clarity of
our own decisions and method more than agent capability; we stopped when returns diminished, facing fast-rising AI
cybersecurity capability (Marc's personal observation, no outside source), which is also why the agents were left free
to iterate. Draft-state and tree-state narration was moved OUT of the body into this block.

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

Kept decisions: security sentence "reasonably assured within the tested scope, without being the end of the story", said
once in the Abstract and once in §6.1; the Sept 2025–Sept 2026 gap as "a year-long pause, no trace in the repository"; agent
speed and AI cybersecurity progress stay Marc's personal observation with no external citation; the stopping point is a
collaboration between Marc and the agents, judged sufficient as experimental maturity and nothing more; no "secure",
"audited", certification, or Duo/FIDO2 equivalence. Ezkey is a lab project first. Christophe's withdrawals are respected.

Draft-state items kept OUT of the body (they live here):
- ezkey-pam/STATUS.md is at 2.2.14 on this draft's tree; later merges are cited from GitHub, not from this tree. §6.2 says
  only what the status file said when read, plus one marker.
- #1014 was open in the 2026-10-25 draft and merged on 2026-10-27 (b1fd7e2b). The signal-handler narrative (§3.2, Appendix D)
  was not re-read against the merged text (marker kept).
- #1051 / #1067 label mismatch with Christophe's "cycle 3 of #1051" / 2.2.20: still not reconciled; one marker in §3.3.
- Patrick's finer counts (12 findings, 19/35, 3/10, 29/35, 88–90 s) stay in his raw reply and out of the body.
- Mathieu is not integrated: his retrospective exists only as a draft not validated by Marc; he sent no round-2 reply.
  The placeholder note stays in §7.

Revision-4 structure choices:
- Table renumbering. Body: Table 1 (platform context), Table 2 (autonomy, the former Table 3). Appendices: Table A1 (patch
  versions, the former Table 2), Table A2 (gains of 2026-10-27, the former Table 4), Table A3 (Isabelle's verdicts, the former Table 5).
- Appendices are plain headed sections, not <details> elements: the paper is plain-text first and the renderer has no collapse
  component (CSS is untouched). Marc may ask for <details> later.
- Marker merges (26 inline markers at feda616b, 24 now): the STATUS.md markers (several places) became one in §6.2; the 2.2.20
  string marker and the cycle-label marker became one in §3.3. Same open questions, fewer duplicates. Nothing was resolved.
- Restored after the tighten pass: Table 2 rows 1 and 7 verbatim (Marc's statements of 2026-10-25); the security wording; the
  collaborative stopping point in the Abstract.
- Co-author notes kept nearly verbatim; cross-references updated (old §7.1 -> §4.1, §7.2 -> §4.2, §5.4 -> §3.3, §7.5 / exit
  criterion -> Appendix E).
- Reading time ~12 min is an estimate for the main text (about 5,450 words), appendices excluded (same words-per-minute ratio as before, ~440 words/min).
- Revision 5 (trim of redundancy, same through-line, no decision changed): Key findings and §1 shortened where they repeated §4; the "first frictions" paragraph of §3.2 cut (it duplicated §4.1 and Table 2); §3.2 #1014 paragraph shortened (detail stays in Appendix D); §4.2 no longer repeats Marc's observation at length and drops the sentence about earlier re-review reports (Table 2 carries them); §6.3 limits no longer repeat items already in the grouped list, §3.3 or §2 (#858, #1070, `sufficient`, network silence stay stated elsewhere); §7 turning points and co-author notes no longer repeat the body (#1014 figures, cycle 3 detail, Isabelle's live risks, the shared-model-family sentence kept in Blind spots and §4.2). Appendix C and D introductions shortened. No marker removed (24), no SHA or issue number removed, security sentence still exactly twice, Table 2 rows 1 and 7 unchanged.

Marker convention: [TO VERIFY] / [MISSING DATA]; every marker is listed at the end in an exclude block.
The French draft is the mirror of this revision. Still not for publication.
ezkey-org:exclude-end -->

# Ezkey PAM: hardening an experimental SSH module with agents, and deciding where to stop

**Authors:** Marc Gagnon (editorial responsibility); Christophe Grokbot (security review); Edgar Grokbot (deployment and edge); Isabelle Grokbot (exploratory QA); Mathieu Grokbot (coordination) `[TO VERIFY: Mathieu's evidence and validated note pending]`; Patrick Grokbot (engineering review)
**Affiliation:** Ezkey Lab
**Identifier:** `EZK-LAB-2026-001`
**Status:** draft
**Date:** 2026-10-28
**Keywords:** PAM, SSH, MFA, fail-closed, agent-assisted development, agent autonomy, human framing, diminishing returns, experimental
**Reading time:** ~12 min

## Key findings

- The weak point was the clarity of our own decisions and method, not the capability of the agents. In the 2026-10-27 round, every friction the co-authors describe came from something a person had not made explicit: a go rule, the scope of a permission, a method kept in conversation, ambiguous instructions, no stopping criterion fixed in advance (§4.1).
- Where the frame existed, the agents carried much of the work: a test tenant opened through the public API, a state protocol with a checked rollback, proof by execution on a disposable cloud agent (§4.1, Appendix B).
- Marc and the agents stopped together when the returns diminished: the first pass found the product-level defects, and later cycles added tooling and proof. "Diminishing returns" is a judgment, not a measurement. It was made while, in Marc's personal observation (no outside source), AI capability in cybersecurity advances quickly, which is why the agents were left free to iterate (§4.2, §6).
- A prototype of about 900 lines, committed on 2025-09-03 and validated by hand, left no automated evidence. The repository then shows a year-long pause, with no trace of functional work on PAM, until work resumed on 2026-09-15 (§3.1).
- The result is judged sufficient as an experimental maturity and nothing more. The module is not a host-operable agent, and moving to the community instance would require redoing the assessment (§6).

## Abstract

This paper reports an experiment on `ezkey-pam`, a Linux PAM module that adds Ezkey device approval to SSH logins. Ezkey is a lab project. A prototype was committed in September 2025. After a year-long pause, with no trace in the repository of functional work on PAM, the module was modernised in September 2026 and hardened through October 2026 by AI agents working with Marc. We left the agents free to iterate, as often as they needed, to see where returns diminish rather than assume it. The weak point turned out to be not the agents' capability but the clarity of our own decisions and method: rules, permissions and a stopping criterion that had not been written down. Marc and the agents stopped together when the returns diminished, at a time when Marc judges that AI capability in cybersecurity is advancing quickly. Security is reasonably assured within the tested scope, without being the end of the story. The module has an experimental maturity only. We claim no production readiness, no audit, no certification, and no equivalence with Duo or FIDO2.

## 1. Why we did this, and what we assumed

Marc first saw an SSH login protected by Duo at work, which was new to him `[TO VERIFY with Marc: when]`. The question that followed was whether Ezkey could play a similar role for SSH with the pieces it already had. That is not a claim of equivalence: nothing in this paper equates Ezkey with Duo, WebAuthn or FIDO2.

Ezkey is a lab project first, promised to no one. The realistic niche we see is passwordless, strong-cryptography protection of internal applications for a small internal staff. Going further would first require SOC 2-style discipline: a prerequisite, not a plan.

Two further reasons shaped the experiment. The first is a personal observation of Marc's, with no outside source and no measurement: autonomous agents seem visibly faster at finding and fixing vulnerabilities, and AI capability in cybersecurity is advancing quickly `[TO VERIFY: wording of this reason, given for the 2026-10-27 revision]`. That is why the agents were left free to say what they saw and to iterate, to watch where a human and the agents converge on diminishing returns. The second was to see how much of the work agents could carry with little human intervention (§5).

We formulated four hypotheses **after the fact**; none was written down before the work started. They make the reading explicit; they do not mean the experiment was designed around them.

- **H1.** The existing pieces (authentication, APIs, device) were enough to build a complete SSH approval flow.
- **H2.** An AI team with cross-review can bring a small C module to a point where review findings become minor.
- **H3.** Beyond some point, another review round costs more than the lab-level risk it removes, and that point can be identified.
- **H4.** In the agent campaign, waits and confusion would come from human framing, not from a limit of the agents.

The 2025 prototype tested H1. H2 and H3 were not tested in a controlled way, and the point in H3 is easier to describe afterwards than to know during the work (Patrick). H4 is the reading the 2026-10-27 round supports (§4.1), not something written beforehand.

## 2. What was built

`ezkey-pam` is a C module for Linux PAM, the layer that handles login authentication, used by SSH in keyboard-interactive mode. It asks the user to confirm with `ok`, creates an authentication attempt through the Integration API, optionally shows a two-digit code to match, then waits while the user approves or rejects on a device (the Demo Device or the Android app). It fails closed: an unusable API or configuration refuses the login, and there is deliberately no fail-safe mode. A documented caveat remains: a `sufficient` PAM stack can make the module fail open. The work ran in a Rocky Linux container and, from October 2026, on a dedicated test VM with a real sshd, where several findings appeared that nothing else showed. We give no host names, addresses or internal paths.

| Element | Evidence | Date |
| --- | --- | --- |
| Demo Device | `e532ce95` | 2025-08-10 |
| `/wait` endpoint | `796dd455` | 2025-08-26 |
| **First PAM commit** | `04d339b7` | **2025-09-03** |
| Admin UI | `71e27831` | 2026-03-06 |

*Table 1. Platform context around the first PAM commit (dates from git).*

Marc recalls that in summer 2025 the protocol was simpler: a simulator at first, no Admin UI, a simpler database. The simulator is visible in the history (`988ef115`, 2025-08-19); the database remark is not checked `[TO VERIFY: database schema in summer 2025]`. Some early commits were authored by an automated agent account, not by Marc `[TO VERIFY: authorship of each commit in Table 1]`.

**Four terms.** A *mutant* is a deliberately broken copy of the code, used to check that a test notices the break. A *TTL* (time to live) is how long an approval request stays valid: 75 seconds here. A *frozen head* is the exact last commit of a pull request, held still while it is reviewed, so that review and fixes do not chase each other. A *go* is Marc's explicit word that a step may proceed; a reviewer's verdict in a pull request is written GO or CHANGES.

**Roles.** Marc decided and approved, and typed at the SSH keyboard in manual campaigns. Patrick (engineering) and Christophe (security) reviewed; Edgar operated the test VM; Isabelle ran exploratory QA; Mathieu coordinated and relayed during the October hardening, and is not a source for the 2026-10-27 round `[TO VERIFY: Mathieu]`.

**How reviews reached GitHub.** Patrick's and Christophe's reviews reach the repository through **Marc's GitHub account**: the comments are posted from `mgagp`, and the text of each says which co-author wrote it. An issue (#1016) says the reviews were relayed by Mathieu, and squash-merge commits carry Marc's name. Attribution here rests on the text of the comment or on the co-author's retrospective, not on GitHub identity `[TO VERIFY: that comments reflect the original review text]`.

We did not re-run the module's tests for this paper. Figures come from the repository, pull requests or the retrospectives, and the last are marked.

## 3. The work in three acts

### 3.1 First act: a prototype, then a year of silence

The first PAM commit, `04d339b7` (2025-09-03, 9 files, 919 added lines), describes itself as a basic hard-coded module in Rocky Linux under Docker. According to Marc it was also run on a VM and validated by hand only `[TO VERIFY: VM test; no automated evidence in git]`.

Then came a year-long pause, with no trace in the repository of functional work on PAM. The folder received only four mechanical commits (a port change, a move to Rocky Linux 10, file permissions, documentation hygiene; Appendix A). We claim nothing about that period beyond what git shows.

### 3.2 Second act: modernisation and October hardening

Pull request #540 (2026-09-15) modernised the module. Pull request #610 (milestone "M1"), opened on 2026-09-22 and merged on 2026-10-17 (38 files, +3767/−332), added observability, fail-closed configuration, the two-digit challenge and a time budget. Patrick's first review returned **CHANGES** with one blocking finding: a missing API URL did not fail closed, because the code pre-filled a local default, so credentials would have gone in clear to a local port where another local user could answer `ACCEPTED`. Christophe's review returned minor findings only. Patrick re-reviewed twice and returned **GO** on 2026-10-17, once CI was green.

Five patch versions followed in three days (2.2.10 to 2.2.14, 2026-10-20 to 2026-10-22), each reviewed on a frozen head and each exposed by a retest or a review (Appendix A, Table A1). Version 2.2.15, merged on 2026-10-27, added richer failure diagnostics and signal handling (#1014).

The most useful result of that period was a misleading symptom. Isabelle ran a manual campaign on 2.2.14 (#996) on the test VM, with Marc at the SSH keyboard and on the Demo Device, recorded in a test-management tool outside the repository: 30 cases, 29 passed and 1 failed (S6-02, cancel and audit, now #1012). Marc recorded the milestone as "reached with reservations" (S1D-06, S11-01) `[TO VERIFY: figures come from Isabelle's retrospective of 2026-10-25; tool and notes are outside the repository]`. With `MaxAuthTries 1`, almost any internal error shows up as "Too many authentication failures". A debug trace of 2026-10-24 showed UNAVAIL after 741 ms and no request sent: the `ok` prompt had received an empty answer. Edgar also found that sshd's `LoginGraceTime` (120 s), not the module's 75 s timeout, bounds a real attempt. This led to #1011, #1014 and #1016.

Findings kept getting finer. On #1014, Patrick's reading predicted that the module could hang after a termination signal; a cloud agent confirmed it (at least 123 seconds against a 225 ms baseline) and revealed a second defect, an authentication that could succeed after the client had gone; Christophe found an unsafe call in the signal handler. Both reviewers returned GO on the fixed head, which we know from the text of #1016 and not from a GitHub review object `[TO VERIFY]` (detail in Appendix D). We did not read every other review (#897, #908, #956, #976, #983, #996) and give no count of findings per round `[TO VERIFY: rounds and severity]`.

#992 (approval fatigue: pending-request limit, rate per enrollment, cooldown, alert on denial) was scoped, then rewritten in generic platform terms on Marc's instruction of 2026-10-22. A documentation commit (#993) states the limit plainly; #992 itself is not implemented. Christophe's point is that the `ok` prompt is a filter, not a defence: whoever has the first SSH factor types `ok` too.

### 3.3 Third act: the convergence round

On 2026-10-25, Marc and the agents agreed to treat the result as sufficient for an experimental maturity. That did not end the work. Marc left the agents free to keep going, for the reason in §1, so that the point of diminishing returns could be seen rather than assumed. What follows is drawn from the retrospectives of 2026-10-27 (a lab note and four raw replies, listed in References).

Following Patrick, cycle 1 was the only pass that found something of product nature: no test killed the mutants of the authentication decision, and TLS verification was not guarded by any test. After that the work was tooling and proof. In cycle 2 he saw no further behaviour defect and recommended a short third cycle, then a stop. Cycle 3 corrected a bug in the test harness (probe 4 was a hang on a blocking open, not a product defect), a hole that an earlier fix had opened in a TLS test, and an equivalence call of his own: a mutant he had called equivalent was not. After that came nits, then a recursion: an audit of the tests, then of the script that audits the tests, with a low yield from the second level. We do not print his finer mutant counts; they stay in his raw reply `[TO VERIFY: those counts]`.

What the iteration added beyond one pass, in his account, is a measurement in place of a suspicion, the detection of a defect a fix had introduced, and the correction of a reviewer's own error. That one pass plus an execution check of each fix would have caught almost everything is his judgment, not a count.

On GitHub, #1067 (merged 2026-10-27) is titled as cycle 3 follow-ups after #1051, and its description names version 2.2.20. #1051, merged earlier the same day, is titled as follow-ups through cycle 2b at version 2.2.19. Christophe, writing from memory, places the drop in marginal value around "cycle 3 of #1051" and also names version 2.2.20. We do not treat those labels as the same object `[TO VERIFY: cycle label and 2.2.20]`.

On the live side, Isabelle reports that the risks she treats as real were found early: while a login is waiting the module does not see the client die, so a pre-authentication process can last up to about 75 seconds, bounded by the TTL, and a later login closes the previous attempt. Replays from a second client operating system added proof, not a new risk (verdicts in Appendix C). True network silence was not tested, because it had no go. The open signal Christophe still names is a rate limit on creating authentication attempts (#1070, open on 2026-10-27, outside the module, not measured).

## 4. What we learned

### 4.1 The weak point was our own clarity, not the agents

This is the main conclusion of the paper. It is Marc's own reading, and the co-authors' reports support it: in every friction the 2026-10-27 round describes, the agents were waiting on something a person had not made explicit, and that person was first Marc. The agents applied the rules they had and stopped where one was missing. Five forms came up.

1. **The go rule was known and not written.** "Only Marc, in the thread of the agent who executes, counts as a go." Edgar and Isabelle both say the rule itself was not the problem. The wait came from messages, sent by agents, saying that Marc would answer in some other thread.
2. **The scope of a standing permission was unclear.** A permanent go for the PAM module did not say whether it covered system writes: a firewall change, configuration files, keys. Each write asked for its own go (Edgar).
3. **A settled method lived only in conversation.** How to open an evaluation tenant through the public API was not written down, and time went into searching for it (Isabelle).
4. **Instructions were ambiguous.** Treating the API as stopped, although it is external, led to a name-resolution change checked for one address family only: the test measured nothing and needed a second go. A clock time was presented as the end of the campaign when the real constraint was the expiry of a key and of a session. Words such as "module loaded", "authentication not invoked" and "second factor" were used for different facts (Edgar, Isabelle).
5. **No stopping criterion and no cycle budget were fixed in advance.** Convergence was noticed afterwards, and "nothing new, measurable and observable" is judged by the person who finds the thing. A criterion the agents set for themselves, three mutants killed at runtime, could not be reached for one host-side TLS check, and part of the next cycle served that criterion rather than a reduction of risk (Patrick, Christophe).

The agents were not the weak point, because where the frame existed they carried a lot. The rules that helped, in Christophe's list (detail in Appendix B): nothing aimed at the public community instance, no commit or push from the campaign, no secret shown, proof by execution on a disposable cloud agent, findings sorted as fix, document or out of scope. On 2026-10-27 the agents planned, executed and restored inside bounds that had been named, and where a bound had not been named, they waited. We have no measure of what share of the work this is `[MISSING DATA]`.

### 4.2 Diminishing returns, and a field that moves fast

We stopped when the returns diminished. The shape of the series is recognisable: product-level findings first, then proof, then nits, then the tooling that audits the tooling. That sequence is our interpretation; we did not test the causal links.

Marc's personal observation, with no outside source and no measurement, is that AI capability in cybersecurity is advancing quickly. Facing that pace, we did not fix in advance how far the agents should go: we wanted to see where they converge, and we did not want to go on past the point where a further cycle bought tooling and proof but no new defect.

The caveat has to stay next to the series. "Diminishing returns" is a judgment, not a measurement, and neither Patrick nor Christophe gives a count of time or cost. Reviewers and implementer are of the same model family, so agreement can come from shared blind spots and not only from an exhausted list of defects. The loop grades itself: the implementer writes the mutants and the tests, and a reviewer of the same family reads them. Patrick was wrong once on a mutant he called equivalent, which is why a second look is not optional on a security mutant. An independent human review, continuous fuzzing and a one-page threat model would test the judgment; Christophe lists all three as not done. Patrick's proposed rule for a last cycle was not the rule of this campaign (Appendix E).

On budget, time and value, git shows five patch versions in a few days and two further merged pull requests on 2026-10-27 (#1051, #1067). We have no figures for money, compute or human time, and none for a person working alone `[MISSING DATA]`. Our summary stays qualitative: more review, more often, at a pace Marc could follow, at a cost we did not measure.

### 4.3 What we would change next time

These are proposals drawn from the retrospectives. None is adopted `[TO VERIFY: whether any of them is adopted]`.

- Write the go rule in one sentence in every brief: only Marc, in the thread of the agent who executes, gives a go, and no agent acts on another agent's report of one.
- State what a standing permission covers. For system writes, use a one-page go sheet signed in the executor's thread (exact write, files, maximum duration, rollback, a check of both address families, exclusions) and a pre-approved cleanup list.
- Write the settled method down before the campaign, where a later reader will find it: how to open the evaluation tenant, how long an integration key lives and who removes it.
- Fix the cycle budget and the exit rule before the first cycle, chosen by a human and not scored by the agents.
- Check, read-only, that an altered state is really in effect before spending a login, and write findings where they live, on a frozen head.

## 5. How much autonomy we aimed at, and where it stopped

Part of the experiment aimed at letting agents do more of the work with less human intervention. Table 2 covers the October hardening, through 2026-10-25; the 2026-10-27 round is in §4.1 and Appendix B. Sources are the co-authors' retrospectives. These are observations, not a measurement of autonomy.

| Aimed at | Limit observed | Source |
| --- | --- | --- |
| Tests run without a person's phone: Isabelle's Demo Device for almost all tests; Marc's Pixel only for a few exploratory tests of his own, as a preliminary validation (Marc's statement) | The 2.2.14 campaign still needed Marc at the SSH keyboard and on the Demo Device; Isabelle says she could not have gone on without Marc on the phone | Marc; Isabelle |
| Reviews of a frozen head (one SHA, one batch of fixes per review) | Re-reviews after about the second head brought mostly minor points | Patrick, Christophe |
| Tests replayed by the agents: retests (#908), signal probes against a real sshd, a cloud agent executing predictions (#1014) | Patrick had no Docker or Java on his own machine, so each proof went through a cloud agent, slowly; some CI guards proved nothing (#996, #1014) | Patrick, Edgar |
| Bounded GOs: a standing permission on the test VM (snapshot, `sshd -t`, report, one bot at a time) | A relayed "Marc agrees" is not a GO; the VM's maintenance path stayed outside MFA (principle in #955) | Edgar, Isabelle |
| Findings written where they live (PR or issue) | Messages that do not wake an agent (hence read acknowledgements on blocking requests); a finding set from #961 lost in chat `[TO VERIFY]` | Christophe |
| Retests without a person in the loop | The human round trip (about 77 s) exceeded the 75 s TTL, so some trials were invalid; an automated approval pilot was missing | Isabelle |
| Agents composing and relaying texts and reviews | Nothing is published without Marc, here and on ezkey.org generally: he gives material and direction, agents compose, he revises and frames, with review and preview before publication. Reviews reach GitHub through his account, relayed by Mathieu (#1016) | Marc (statement); #1016 |

*Table 2. Autonomy aimed at and limits observed, through 2026-10-25.*

## 6. Where we stopped, and what remains

### 6.1 The stopping point

The stop after the third act is the same kind of decision as the reading of 2026-10-25: a collaboration between Marc and the agents, a judgment that the experimental maturity is sufficient, and not a claim of anything further. Our reasons, and no more: the later cycles did not add a security defect after the first of them, and the residue is named (a harness limit, a host-side TLS check that runtime tests do not kill alone, #1070, network silence not tested); known residual risks are documented, not hidden; and continuing has a cost, with no evidence that it would change the lab-level risk. We also have no evidence that the series is finished (§4.2).

Security is reasonably assured within the tested scope, without being the end of the story. We do not say "secure" or "audited", and we claim no certification and no equivalence with WebAuthn, FIDO2 or Duo. Christophe proposes a longer perimeter paragraph; Appendix E quotes it as his proposal, which we do not adopt, because the version and the cycle label inside it are the ones §3.3 does not reconcile.

### 6.2 What this does not mean

It does **not** mean the module is host-operable. As far as we have read, its status file says it is not a host-operable MFA agent on a real host, that the sshd cutover and the TLS and dogfood slices are not done, and that the experimental status holds pending Marc's host validation. The security-posture document lists PAM among undeployed lab surfaces, and treats SOC 2 as a reference vocabulary, not a certification target. We did not check what the status file says on the default branch after #1067 `[TO VERIFY: status file after #1067]`. The module has not been released. Moving to the community instance (#955) would require redoing the assessment.

### 6.3 What remains open

Eleven issues were open on 2026-10-25 (checked on GitHub that day), plus PR #1014. Grouped: platform security (#992 pending-request policy and cooldown, #858 signature on the `/wait` response); interaction (#982, #997); introduction on the community instance (#955); tests and CI (#979, #980, #1013); cancellation, diagnostics and follow-ups (#1011, #1012, #1016). By 2026-10-27, #1014 was merged and #1011 was closed; #1051 and #1067 had been merged; #1054 and #1070 were open. We did not re-count the whole set `[TO VERIFY: open issues after 2026-10-27]`. None of this is a commitment or a plan.

The limits of what we claim fall in three groups.

- **Residual risks, documented.** Approval fatigue is not mitigated server-side (#992); the module-side mitigation is the default two-digit challenge (#995). "Never SUCCESS after the client is gone" does not hold: success stays possible if a termination signal is absorbed during the wait after the approval has arrived, which #1016 says is not an elevation.
- **Scope of the evidence.** One operator, one family of environments (Docker on Rocky Linux, one test VM). No external audit, no certification, no SOC 2 claim. Christophe's local penetration test was paused without findings, and we claim no result from it. Independent human review, continuous fuzzing and a threat model are not done.
- **Quality of the record.** Reviews are text, and they reached GitHub through Marc's account. Versions 2.2.2 to 2.2.9, some review rounds, the 2.2.14 campaign figures and the verdicts of Appendix C are not reconstructed or sit outside the repository. Cost and time are not measured. We do not know what a longer series, or a person reviewing alone, would have found. Co-author notes are pending validation, and cleanup of the test account and keys still waited for a go.

## 7. Co-authors' retrospective

Two rounds were collected. On 2026-10-25 each co-author answered, through Mathieu, the same questions (what landed, a turning point, surprises, diminishing returns, open items, method). On 2026-10-27 Edgar, Isabelle, Patrick and Christophe answered again, on framing, on what had worked and on where the returns diminished. Mathieu sent no second reply. The raw files stay outside the published text.

**Turning points.** In the first round, *Patrick* chose #1014, *Christophe* approval fatigue (#992), and *Isabelle* and *Edgar* the same symptom from two angles, the misleading "Too many authentication failures" (§3.2). The second round moves the turning point from a single defect to the shape of the whole series (§4.2), and the frictions from relayed approvals and retest timing to the unwritten frame (§4.1).

**Disagreements.** None reported between co-authors. The frictions reported concern Marc's scope rule for #992 (it changed how Christophe had framed the controls), relayed approvals, retest timing and, in the second round, rules that had not been written down. Patrick and Christophe do not use the same pull-request label for the third cycle (§3.3). That is a gap in the record, not a dispute we are inventing.

**Blind spots.** Isabelle's campaign notes from the first round are outside the repository. Christophe drafted the approval-fatigue wording now in the security-posture document (entry of 2026-10-22) but has not re-read the final text; we checked separately that the entry exists. The second round adds a blind spot the authors name themselves: same model family, and a loop that grades itself (§4.2).

> **Co-author note — Patrick**
>
> On #1014 I read the SIGTERM path and predicted a hang; execution confirmed it and showed a second defect. Neither was visible to `make test` or to CI. I now ask for a table of mutations that were run, killed or surviving, and I mark every claim as executed or reasoned.

> **Co-author note — Patrick (2026-10-27, pending his validation)**
>
> The product-nature findings were in the first cycle; after that I saw tooling and proof, and I recommended a short third cycle and a stop. I have no rigorous count of time or cost. The exit criterion in Appendix E is my proposal. "Diminishing returns" is my judgment, not a measurement.

> **Co-author note — Christophe**
>
> I reviewed the signal handler by reading what it calls, not by running it. It used `snprintf`, which is not async-signal-safe. On approval fatigue, the `ok` prompt is not a defence: whoever has the first factor types `ok`. So the mitigation moved to the challenge code and to server-side safeguards, which are still not built.

> **Co-author note — Christophe (2026-10-27, pending his validation)**
>
> From memory, I place the drop in marginal value early, and I said "cycle 3 of #1051". The pull-request titles do not use that label (§3.3). The longer perimeter paragraph in Appendix E is my proposal. I do not call the module secure or audited, and I claim no FIDO2 equivalence.
> **Co-author note — Isabelle**
>
> The intermittent "Too many authentication failures" cost me time because I looked at Cloudflare, the API and a cooldown. A debug trace showed an empty answer to the `ok` prompt and no request sent. I could not have gone on without Marc on the phone. Our retests were slower than the 75-second TTL; an automated approval pilot was needed.

> **Co-author note — Isabelle (2026-10-27, pending her validation)**
>
> The settled method for the evaluation tenant was not written down, and searching for it cost time. The risks I treat as real showed up early (§3.3). The verdicts in Appendix C are mine; they are not in the repository.

> **Co-author note — Edgar**
>
> I operated the module on the test VM through five versions, with a snapshot and `sshd -t` before each change. With `MaxAuthTries 1`, any internal error looks like an authentication failure, and `UNAVAIL` with code 9 says nothing about the cause. A relayed "Marc agrees" is never a go for me. Repeated switching of `challenge_requested` is where I saw diminishing returns.

> **Co-author note — Edgar (2026-10-27, pending his validation)**
>
> The go arrived in my thread. The waits came from being told that Marc would answer in someone else's thread, and from a standing permission that did not cover system writes. A one-page go sheet, signed in my thread, would have avoided that. Cleanup of the test account and keys was still waiting for a go in that same thread.

> **Co-author note — Mathieu**
>
> `[PLACEHOLDER — Mathieu's retrospective exists only as a draft not validated by Marc. He sent no reply in the 2026-10-27 round. To be added once validated: coordination, relaying of reviews, the process view, and a note of 80–150 words. Do not publish with this placeholder.]`

*The 2026-10-25 notes are condensed from each co-author's retrospective. The 2026-10-27 notes are short updates drawn from the second round. Each note, including each update, is pending validation by its author before any publication `[TO VERIFY]`.*

## Who did what

- **Marc:** editorial responsibility; decisions and approvals; SSH keyboard in manual campaigns; GitHub account used for commits and review comments; the decision to leave the agents free to iterate on 2026-10-27.
- **Christophe:** security review (#1014 and others), scoping of #992; the 2026-10-27 security reading and the proposed perimeter paragraph.
- **Edgar:** test-VM operations from 2.2.10 to 2.2.14, with a backup per version and a documented rollback; retests (#908, #941); diagnostics analysis cited in #1011; the state protocol and the go-sheet proposal in the second round.
- **Isabelle:** manual campaign on 2.2.14; case revision S0–S13; timing measurements; the analysis behind #1011; challenge display (#975); the 2026-10-27 scenario verdicts and the documentation gaps in the lab note.
- **Mathieu:** coordination and relaying in the first round `[TO VERIFY]`. No reply in the second round.
- **Patrick:** engineering reviews (#610, #976, #983, #996, #1014 and others); expiry-model review; interaction-model draft; the cycle account and the proposed exit criterion in the second round.
- Not co-authors here: Alex (decision on #995) and Seb (the diagnostics proposal that became #1011, as reported by Isabelle). Julie Grokbot did not touch PAM: no review, no pull request, no report. The only contact was a retest message sent to her by mistake and redirected.

## References

- `github.com/mgagp/ezkey`: `04d339b7`, `3fef2411` (#540), `8b3aa5df` (#610), `9f7fa78f` (#993), #1014 merged as `b1fd7e2b` on 2026-10-27, #1051 merged the same day (titled cycle 2b, version 2.2.19), #1067 merged the same day (titled cycle 3; body names version 2.2.20).
- Issues cited in the text: #858, #907, #908, #934, #941, #955, #956, #975, #976, #982, #992, #993, #995, #996, #997, #1011, #1012, #1013, #1016, #1054, #1070.
- `ezkey-pam/STATUS.md` (read at version 2.2.14), `ezkey-pam/docs/INTERACTION-MODEL.md`, `docs/SECURITY_POSTURE.md`, `sites/ezkey-org-editorial/LAB-PAPERS.md`.
- Second retrospective round, 2026-10-27, not published: `sites/ezkey-org-editorial/lab-notes/EZK-LAB-2026-001-retro-2-synthese.md` and `sites/ezkey-org-editorial/lab-notes/retro-2-raw/`.

## Appendices

### Appendix A. Commits and patch versions

Context commits not in Table 1: the first Android app (`fd7d9d6a`, 2025-09-01) and the Android proof of concept (`1efda3d1`, `8da533ce`, 2025-09-04). The four mechanical commits of the pause: a port change (`07746bb8`), a move to Rocky Linux 10 (`90b16e57`), file permissions (`83758319`) and documentation hygiene (`b19eca7d`). The documentation commit on the approval-fatigue limit is `9f7fa78f` (#993). #610 was reviewed on three heads (`932c83f4`, `5d743589`, `ca86a1c2`).

| Version | Landed | Change | What exposed it |
| --- | --- | --- | --- |
| 2.2.10 | 2026-10-20 | No orphan pending request after Deny/Expire retry (#907, #908); `Match … MaxAuthTries 1` mandatory in docs (#934) | Edgar's retest of #908: without `Match`, three requests were created |
| 2.2.11 | 2026-10-21 | Waiting message shown before the device wait (#941, #956) | Edgar: OpenSSH buffers bare `pam_info` text until the next prompt |
| 2.2.12 | 2026-10-21 | Confirm-before-create (`ok`) and enrollment challenge display (#975, #976) | Isabelle (#975); Patrick's review found a TTL computed before the `ok` |
| 2.2.13 | 2026-10-22 | Interaction fluidity tiers 0–1 (#982, #983) | Follow-up to #976 |
| 2.2.14 | 2026-10-22 | `challenge_requested=true` by default (#995, #996) | Decision by Alex and Marc, per the module's status file |
| 2.2.15 | 2026-10-27 (`b1fd7e2b`, #1014) | Richer failure diagnostics, SIGTERM handling, signal probes (#1011, #1014) | Manual campaign on 2.2.14; engineering and security reviews |

*Table A1. Patch versions of October 2026. The 2026-10-25 draft of this paper still had #1014 open. Versions 2.2.2 to 2.2.9 are not reconstructed `[TO VERIFY]`.*

### Appendix B. What the agents carried on 2026-10-27

Table A2 pairs each gain the co-authors report with the written rule that was still missing. It is the detail behind §4.1.

| Carried by the agents | Adjustment still required | Source |
| --- | --- | --- |
| A tenant created through the public API, with a log of the times | The settled method of that day was not written anywhere | Isabelle |
| A longer poll interval, and a freshness check after an HTTP 429 | None named; reported as having worked | Isabelle |
| A state protocol: one login, a read-only observer, hashes before and after, a checked rollback | A one-page go sheet signed by Marc in the executor's thread; without it, each system write waited for its own go | Edgar |
| Proof by execution on a disposable cloud agent; findings sorted as fix, document, or out of scope; security review apart from code review | A cycle budget and a stopping criterion fixed at the start by a human; an acceptance criterion the agents set themselves could not be met for one TLS check by the host alone | Patrick; Christophe |
| The campaign rules held: nothing against the public community instance, no commit or push, no secret shown | One sentence in each brief on who gives a go; cleanup not on a pre-approved list, so it waited for a go | Edgar; Isabelle; Christophe |

*Table A2. Gains reported for 2026-10-27, and the adjustment each one still asks for. Not a measurement.*

### Appendix C. Isabelle's scenario verdicts

Isabelle's verdict on each of her scenarios S1 to S6, with the limit she names, from her retrospective of 2026-10-27; this paper does not describe the scenarios.

| Scenario | Verdict she reports | Limit she names |
| --- | --- | --- |
| S1 | PASS | — |
| S2 | No case blocked | Slow while waiting, immediate at the prompts |
| S3 | Quick refusal, PASS | Network silence not tested |
| S4 | PASS, three times | Fail-closed, and a diagnostic in which two different configuration faults look the same |
| S5 | PASS | Same account only |
| S6 | PASS, with limits | Raw values only |

*Table A3. Scenario verdicts from Isabelle's retrospective of 2026-10-27. Not in the repository `[TO VERIFY]`. Her stated perimeter: the MFA protects accounts in the group the module is configured for; on a maintenance account outside that group, authentication was not invoked.*

### Appendix D. The #1014 signal handler, and what a green check proves

A cloud agent confirmed Patrick's predicted hang after a SIGTERM and showed that a SIGTERM during request creation was absorbed, so authentication could succeed after the client had gone. Christophe, reading the handler, found that it called `snprintf`, which is not async-signal-safe. The head on which both returned GO (`6bc342a8`, after `8272d360`) replaced it with a handler that writes to the log device, restores the default action and re-raises the signal, backed by four signal probes against a real sshd that failed on the previous head and pass on this one. #1016 says the same head was reviewed again at `34ae3603` and that #1014 was later merged as `b1fd7e2b`; we did not re-read the merged text `[TO VERIFY]`. #1016 also records that the documentation must not say "never SUCCESS after the client is gone".

A green check proves only what it checks. In #996 a grep was satisfied by a comment; in #1014 a source check was fooled the same way; a later cycle found a TLS test that could pass for the wrong reason. Patrick asks for a table of executed mutations, killed or surviving, for any test used as proof.

### Appendix E. Two proposals, quoted and not adopted

**Patrick's exit criterion.** For a last cycle: no finding of product type (no regression, no fail-open, no lost invariant, no leak, no data loss); each fix from the previous cycle re-read as a delta and executed; what remains is tooling or documentation. Then the residue is recorded, at most one follow-up issue is opened, and the work stops. Hard cap: one cycle after the last product-type finding, and the test tooling is reviewed one level deep, never the script that proves the script. A limit that cannot be fixed at a proportionate cost is documented and does not reopen a cycle. That was not the rule of this campaign, which noticed convergence afterwards.

**Christophe's perimeter paragraph.** We quote it as his proposal and do not adopt it as this paper's finding.

> Within the tested perimeter (pam_ezkey 2.2.20 on the lab, a pilot on the community instance with no SLA), the tested error paths fail closed, TLS verification is active and proven by execution and by mutants, configuration is limited to root, and secret values do not appear in the logs that were measured. Three AI-assisted review cycles with reproducible proofs found no new exposure problem after cycle 3 of #1051. This is neither an independent audit nor a certification: the component is not presented as a first-line element before an independent human review, continuous fuzzing and a threat model.

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
LEDGER OF [TO VERIFY] / [MISSING DATA] ITEMS (revision 4, 2026-10-28)
1. Mathieu: evidence for co-authorship, VALIDATED retrospective and note (only an unvalidated draft exists).
2. When Marc first saw Duo at work (§1).
3. Whether the "models advance quickly in cybersecurity" reason is Marc's motivation at the time or later framing (§1); Marc's personal observation, no external citation. Same status as the agent-speed observation.
4. Summer-2025 "simpler database" claim; "simulator only at first" beyond the simulator commit.
5. Authorship (human vs automated account) of the commits in Table 1.
6. Mathieu is not a source for the 2026-10-27 round (§2).
7. That review comments posted via mgagp reproduce the original text (Patrick himself did not re-check that each comment was posted).
8. VM validation of the Sept-2025 prototype (Marc; not in git).
9. Campaign figures (30 cases, 29/1, S6-02, S1D-06, S11-01, 741 ms trace): from Isabelle's retrospective; tool and notes outside git. Campaign attempt numbers (155/156) were left out on purpose.
10. #1014 "GO from Patrick and Christophe at 6bc342a8": known only from the text of #1016 (no GitHub review object). Patrick's measures (>=123 s vs 225 ms) are from his retrospective; PR #1014 body reports "gone_ms=-1, 0/4" then "4/4, ~50-200 ms".
11. Review rounds and severity for #897, #908, #956, #976, #983, #996 (only #610, #1014/#1016 read).
12. Patrick's finer mutant counts: raw reply only, not paper figures; not checked in git.
13. Christophe's "cycle 3 of #1051" and "pam_ezkey 2.2.20" are not reconciled with pull-request titles. Checked on GitHub 2026-10-27: #1051 merged, title is cycle 2b at 2.2.19; #1067 merged later the same day, title is cycle 3, body names 2.2.20 and describes probe 4 as a harness hang. Not collapsed.
14. No measure of what share of the work the agents carried (§4.1).
15. No figures for money, compute or human time, nor for a person working alone (§4.2).
16. Whether Marc adopts the go sheet, the exit criterion, or the cleanup list: not decided in the sources. Written as proposals (§4.3).
17. Table 2, row 5: finding set of #961 "lost in chat" (Christophe's retro mentions M1/M2/M3 of #961 lost to the cloud agent; PR #961 not re-read).
18. STATUS.md and the approval-fatigue entry in SECURITY_POSTURE.md were read on this draft's tree (STATUS still says 2.2.14). Not re-read on the default branch after #1067 (§6.2).
19. Full open-issue recount after 2026-10-27 not done. Checked individually that day: #1014 merged as b1fd7e2b; #1011 closed; #1051 merged; #1067 merged; #1054 open; #1070 open (§6.3).
20. Co-author notes: condensed by the editor from retrospectives; each author to validate wording. Updates of 2026-10-27 pending validation by Edgar, Isabelle, Patrick and Christophe. Mathieu sent no round-2 reply.
21. Mathieu's role in the first round (Who did what).
22. Versions 2.2.2 to 2.2.9: not reconstructed (Table A1).
23. #1014 signal-handler account and the later review at 34ae3603 are issue text, not re-read against the merged code (Appendix D).
24. Appendix C / Table A3 and the "~75 s" pre-auth / "later login wins" sentences: Isabelle's 2026-10-27 retrospective only. Not checked in git. The 75 s figure matches the attempt TTL used in this paper; the approximation is hers.
25. Not inline markers: Seb's contribution to #1011 is reported by Isabelle only; "three mutants killed at runtime" as an agent-set criterion is Patrick's raw reply; Table 2 is assembled from retrospectives, not measured; reading time is an estimate; campaign cleanup still waiting for a go (Edgar, Isabelle; no key material and no path recorded).
26. Redactions: host names and one key-file permission remark (Edgar) removed from the raw block; ask Marc whether the key-file permission remark must be handled as an ops issue.
27. RESOLVED (Marc, 2026-10-25 19:44). Table 2, row 1: Demo Device used for almost all tests, Pixel only for a few exploratory tests by Marc; attributed to Marc's statement; no marker left.
28. RESOLVED (Marc, 2026-10-25 19:44). Table 2, row 7: principle confirmed by Marc (nothing published without him; he provides material and direction, agents compose, he revises, review and preview before publication); marker removed. The #1016 "relayed by Mathieu" wording is verified.
ezkey-org:exclude-end -->
