---
name: pam-doctor-curated
description: >-
  Runs the PAM C doctor-curated pass (clang-format Google, clang-tidy, cppcheck)
  and walks findings one at a time. Use when the operator says pam-doctor-curated
  or asks to set up or run C quality signals for ezkey-pam. Linux module only;
  the macOS install track is out of scope.
disable-model-invocation: true
---

# PAM doctor-curated

## Purpose

Punctual hygiene for `ezkey-pam/src`: an opinionated layout standard plus a short
deduped defect shortlist. Same weight class as `java-doctor-curated`. **Not** a CI
gate and **not** a zero-warning campaign.

## Boundary

- **Enter when:** the operator says `pam-doctor-curated`, or asks to stand up / run
  the C quality pass for the PAM module.
- **Out of scope:** macOS install, host sshd lab notes, and product behavior changes
  that were not accepted in HITL.
- **Not a program:** do not invent `I-*` / `TB-*` per finding. One GitHub issue for
  the lane is enough when the operator wants board visibility.

## Authority

- Keyword: root `AGENTS.md` § PAM doctor-curated
- Tooling: `config/pam-doctor/README.md`, `ezkey-pam/.clang-format`, `ezkey-pam/.clang-tidy`
- Campaign notes: `product-docs/global/hygiene/pam-doctor/`
- Command: `./scripts/pam-doctor-curated.sh` from the repo root

## Standards

| Role | Choice | Why |
|------|--------|-----|
| Layout | clang-format `BasedOnStyle: Google` | Same decision as google-java-format: one widely shipped, opinionated preset. Only override: `SortIncludes: Never` (include order is load-bearing). |
| Compiler-aware defects | clang-tidy `clang-analyzer-*`, `bugprone-*`, `cert-*` | Maintained with LLVM. Readability and C++ modernize checks stay off. |
| Build-light defects | cppcheck `--enable=warning,performance,portability` | Maintained C/C++ analyzer. `style` and `--enable=all` stay off so they do not fight the formatter. |

Scan `ezkey-pam/src` only, on the Linux reference (container with PAM, curl, and cJSON headers).

## Procedure

1. Run `./scripts/pam-doctor-curated.sh`. Read `logs/pam-doctor/pam-doctor.curated.md`.
2. Propose a small lot (usually 3–6), grouped by rule so duplicates are one row.
3. **Before any source change:** one finding at a time. State what the tool said, where
   it sits in the PAM flow, the hypothesis, and the options. Wait for fix / suppress /
   skip. Do not ask for a bulk matrix reply.
4. Fuzzy signal with no clear source tie: skip. Harmless local over-defense: leave it
   and suppress with a reason. Do not invent characterization tests for a leave.
5. Record the pass in `product-docs/global/hygiene/pam-doctor/` from `TEMPLATE.md`.
6. Accepted fixes go on a hygiene branch. For each fix, name the existing `make test`
   harness and extend it only when the new behavior is not already pinned.
7. Security-sensitive paths (auth decision, cancel, conf secrets, TLS verify, signal
   handler): counter-read the code before a refactor. A format-only apply is
   `clang-format -i` on `ezkey-pam/src` after an explicit go, then `make test` where
   a Linux toolchain exists.
