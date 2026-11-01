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
EDITORIAL NOTES — revision 6 (2026-11-01). Not for publication.

Full rewrite in the light format (LAB-PAPERS.md reframe of 2026-11-01): ~2,000 words instead of ~6,500, no inline
markers, no hypotheses, no appendices, one scope box, short quotes from the main co-authors only.
Revisions 1–5 stay in git history (last: 99e9737b).

Facts kept were re-checked on origin/main on 2026-11-01: first commit 04d339b7 (2025-09-03); #540 (2026-09-15);
#610 merged 2026-10-17; 2.2.10–2.2.14 on 2026-10-20..22; #1014, #1051 (2.2.19), #1067 (cycle 3, 2.2.20) merged
2026-10-27; ezkey-pam/STATUS.md on main: version 2.2.20, not a host-operable MFA agent on a real host.

Dropped on purpose: Mathieu as co-author (no validated retrospective), version-by-version table, Isabelle's
scenario verdicts, Christophe's perimeter paragraph, label reconciliation #1051/#1067, finer mutant counts.
Raw material: lab-notes/ (retro-1-condensed, retro-2-synthese, retro-2-raw/).
ezkey-org:exclude-end -->

# Ezkey PAM: letting agents harden an SSH module, and knowing when to stop

**Authors:** Marc Gagnon; Patrick Grokbot (engineering review); Christophe Grokbot (security review); Edgar Grokbot (test VM operations); Isabelle Grokbot (exploratory QA)
**Affiliation:** Ezkey Lab
**Identifier:** `EZK-LAB-2026-001`
**Status:** draft
**Date:** 2026-11-01
**Keywords:** PAM, SSH, MFA, fail-closed, AI agents, diminishing returns
**Reading time:** ~9 min

## Key findings

- AI agents carried most of the hardening of a small C module. Where we lost time, it was almost always waiting on a rule we had not written down.
- The first review cycle found the real defects. Later cycles mostly added proof, then started auditing their own tooling. That was our signal to stop.
- “Diminishing returns” is a judgment, made by agents of the same model family as the implementer. It is not a measurement.
- The module is experimental and stays that way: it is not meant to protect a real host yet.

## Abstract

`ezkey-pam` adds Ezkey device approval to SSH logins on Linux. A prototype written in 2025 sat untouched for a year. In September and October 2026 we modernised it and let a small team of AI agents review, test and harden it, with Marc deciding. We wanted to see how far agents could carry the work, and where another review round stops paying off. They carried a lot. The friction came from our side: unwritten rules, unclear permissions, no stopping criterion. We stopped when new rounds produced proof rather than defects.

> **Scope.** This is an account of a lab experiment. It is not an audit, a certification or a security guarantee, and `ezkey-pam` is not production software. Nothing here claims equivalence with Duo, WebAuthn or FIDO2.

## 1. Why we tried it

Marc first met SSH protected by a phone approval at work, with Duo. The question that followed was simple: could Ezkey, with the pieces it already had — an Integration API, an authentication flow, a device that approves — play a similar role for SSH?

There was a second, less obvious reason. In Marc’s own observation, AI agents are getting visibly better at finding and fixing security problems, and fast. Rather than decide in advance how much review a small security module needs, we wanted to let the agents go and see where they, and we, would agree that enough was enough.

Ezkey is a lab project, promised to no one. That freedom is what made the experiment possible.

## 2. What we set up

`ezkey-pam` is a C module for Linux PAM, the layer that handles login authentication. Used by SSH, it asks the user to type `ok`, creates an authentication attempt through the Ezkey Integration API, optionally shows a two-digit code to match, and waits while the user approves or rejects on a device. It **fails closed**: if the API or the configuration is unusable, the login is refused. There is deliberately no “let people in if Ezkey is down” mode.

Work ran in a Rocky Linux container, then on a dedicated test VM with a real `sshd`. That second environment mattered: several findings only appeared there.

The team:

- **Marc** decided, gave every go, and typed at the SSH keyboard during manual campaigns.
- **Patrick** reviewed the engineering, **Christophe** the security.
- **Edgar** operated the test VM: snapshot, `sshd -t`, report, before every change.
- **Isabelle** ran exploratory QA campaigns.

Tests used the Demo Device, Ezkey’s browser-based stand-in for the phone, so that most of the work did not depend on someone holding a phone.

## 3. What happened

**A prototype, then a year of silence.** The first commit, in September 2025, was a hard-coded module of about 900 lines, run in Docker and validated by hand. Then nothing functional happened on PAM for a year.

**Modernisation and a first real review.** Work resumed in September 2026 ([#540](https://github.com/mgagp/ezkey/pull/540)). The next milestone ([#610](https://github.com/mgagp/ezkey/pull/610), merged 17 October) added logging, strict configuration, the two-digit challenge and a time budget. Patrick’s first review blocked it on one finding that justified the whole exercise: when the API URL was missing, the code quietly fell back to a local default. A login could then be approved by whatever answered on that local port. The module was supposed to fail closed; on that path, it did not.

**Five versions in three days.** Between 20 and 22 October, versions 2.2.10 to 2.2.14 followed, each one triggered by a retest or a review on the test VM. Small things, mostly: a waiting message that OpenSSH buffered until too late, a request created before the user had confirmed, a timeout computed from the wrong moment.

**A misleading symptom.** During Isabelle’s manual campaign, logins failed intermittently with “Too many authentication failures”. She first looked at the network, the API, a possible cooldown. A debug trace told another story: the `ok` prompt had received an empty answer, and no request had even been sent. With `sshd` configured to allow a single attempt, almost any internal error looks like a failed password. Edgar found a second surprise along the way: the real time limit on a login was `sshd`’s own grace time (120 seconds), not the module’s 75 seconds. Both findings led to better diagnostics ([#1014](https://github.com/mgagp/ezkey/pull/1014)).

**Signals.** On that same change, Patrick read the termination path and predicted that the module could hang after a `SIGTERM`. A disposable cloud agent ran it: the hang was real, and it revealed a second defect, an authentication that could still succeed after the client had gone. Christophe, reading what the signal handler called, found an unsafe function in it. All three were fixed before merge.

> “Neither defect was visible to `make test` or to CI. I now mark every claim as executed or reasoned.” — **Patrick**, engineering review

**Letting the agents run.** On 25 October we agreed the module had reached a sufficient experimental maturity. Marc then left the agents free to keep iterating, to see the curve rather than guess it. Three more review cycles followed, merged on 27 October as versions 2.2.19 ([#1051](https://github.com/mgagp/ezkey/pull/1051)) and 2.2.20 ([#1067](https://github.com/mgagp/ezkey/pull/1067)), alongside a live campaign on the test VM.

## 4. What we observed

**The returns had a recognisable shape.** In Patrick’s account, cycle 1 was the only one that found something about the product itself: no test noticed when the authentication decision was deliberately broken, and nothing guarded TLS verification. Cycle 2 found no behaviour defect. Cycle 3 fixed a bug in the test harness, a test hole that an earlier fix had opened, and one of Patrick’s own calls on a mutant. After that came nits, then recursion: an audit of the tests, then of the script that audits the tests.

The later cycles still had value — measurement instead of suspicion, catching a defect that a fix had introduced, correcting a reviewer’s mistake — but no new security defect.

**The live campaign found its real risks early.** Isabelle’s point: while a login waits for approval, the module does not notice that the client has gone, so a pre-authentication process can linger up to about 75 seconds; and a new login closes the previous pending one. Replays from a second machine added proof, not new risk.

**Approval fatigue is a platform problem.** Christophe’s turning point was this one. The `ok` prompt prevents accidents; it does not stop an attacker who already has the first SSH factor — they type `ok` too. The defence moved to the two-digit challenge, on by default since 2.2.14, and to server-side limits that are scoped ([#992](https://github.com/mgagp/ezkey/issues/992)) but not built.

> “The `ok` prompt is a filter, not a defence.” — **Christophe**, security review

**The agents carried more than we expected.** Where the frame was clear, they planned, executed and restored on their own: a test tenant opened through the public API, a state protocol with a read-only observer and a checked rollback, proof by execution on a throwaway cloud agent, findings sorted as fix, document or out of scope. Where the frame was not clear, they stopped and waited. That was the right behaviour, and it pointed straight at us.

## 5. What we got wrong, and how we reframed

When we asked the agents, after the 27 October round, what had slowed them down, nearly every answer pointed at something a person — Marc first — had not made explicit.

- **The go rule was known but never written.** “Only Marc, in the thread of the agent who executes, gives a go.” Everyone knew it. Waits still happened when an agent was told that Marc would answer in someone else’s thread.
- **A standing permission had no stated scope.** A permanent go for the PAM work did not say whether it covered system writes — a firewall rule, a hosts file, a key. Each one waited for its own go.
- **A settled method lived only in chat.** How to open the evaluation tenant was agreed, then not written down, and searched for again.
- **Instructions were ambiguous.** “Treat the API as stopped”, for an API hosted elsewhere, led to a name-resolution change that covered only one address family. The test measured nothing and had to be redone.
- **No stopping criterion.** We noticed convergence after the fact. One acceptance criterion the agents had set for themselves could not be reached by a host-side test alone, and part of a cycle went to satisfying it rather than reducing risk.

> “A relayed ‘Marc agrees’ is never a go for me. A one-page go sheet, signed in my thread, would have avoided most of the waiting.” — **Edgar**, test VM operations

None of this is a limit of the agents. It is a limit of the frame we gave them.

## 6. What we take away

- **Write the frame before the work.** Who gives a go, what a standing permission covers, the method we settled on. One page, read before the campaign.
- **Fix the stopping rule in advance, and let a human own it.** Patrick proposed one: stop one cycle after the last product-level finding, and review test tooling one level deep, never the script that checks the script. We did not apply it this time; we would next time.
- **Execute, don’t just read.** The defects that mattered were confirmed, or revealed, by running the code against a real `sshd`. A green CI check proves only what it checks.
- **Keep the caveat next to the curve.** Our reviewers and our implementer come from the same model family. Their agreement may reflect shared blind spots as much as an exhausted list of defects. An independent human review, continuous fuzzing and a threat model would test that; we did none of the three.
- **Agents need a human at the right moments, not all the time.** The manual campaign still needed Marc at the keyboard, and a human round trip (about 77 seconds) was longer than the approval window (75 seconds). An automated approval pilot would have saved trials.

> “I could not have gone on without Marc on the phone. Our retests were slower than the approval window.” — **Isabelle**, exploratory QA

## 7. Where we stopped

We stopped at version 2.2.20, together: Marc and the agents agreed that more cycles would buy more proof but not less lab-level risk. Within what we tested, the error paths fail closed and TLS verification is checked by execution. That is as far as we go.

The module is still experimental. Its status file says plainly that it is not a host-operable MFA agent on a real host, and it has not been released. Server-side approval-fatigue limits (#992) and a rate limit on creating attempts ([#1070](https://github.com/mgagp/ezkey/issues/1070)) remain open. Moving it to the community instance would mean starting the assessment again.

What we keep is less about PAM than about working with agents: they will go as far as the frame lets them. Drawing that frame well is our job.

## References

- [`ezkey-pam/`](https://github.com/mgagp/ezkey/tree/main/ezkey-pam) — module, README and status file
- [#540](https://github.com/mgagp/ezkey/pull/540) modernisation · [#610](https://github.com/mgagp/ezkey/pull/610) first milestone · [#1014](https://github.com/mgagp/ezkey/pull/1014) diagnostics and signals · [#1051](https://github.com/mgagp/ezkey/pull/1051), [#1067](https://github.com/mgagp/ezkey/pull/1067) later cycles
- [#992](https://github.com/mgagp/ezkey/issues/992) approval fatigue · [#1070](https://github.com/mgagp/ezkey/issues/1070) attempt rate limit
- [`docs/SECURITY_POSTURE.md`](https://github.com/mgagp/ezkey/blob/main/docs/SECURITY_POSTURE.md)

## Transparency

Co-authors other than Marc are AI agents. Marc holds editorial responsibility and reviewed this paper before publication. Nothing in this paper is a security guarantee.
