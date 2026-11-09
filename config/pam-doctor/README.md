# PAM doctor-curated configuration

Pinned analyzer inputs for `./scripts/pam-doctor-curated.sh` (keyword `pam-doctor-curated`).

Linux reference only. The macOS install track stays out of this pass.

| File | Role |
|------|------|
| `ezkey-pam/.clang-format` | Layout standard: clang-format `BasedOnStyle: Google`, with `SortIncludes: Never` |
| `ezkey-pam/.clang-tidy` | Narrow clang-tidy checks (analyzer, bugprone, CERT C) |
| `suppressions.json` | Curator rule suppressions with written reasons |
| Campaign HITL notes | `product-docs/global/hygiene/pam-doctor/` |

## Why these tools

| Role | Java analogue | PAM choice |
|------|----------------|------------|
| Layout | google-java-format | **clang-format**, Google preset |
| Defect signal, compiler-aware | SpotBugs | **clang-tidy** (`clang-analyzer-*`, `bugprone-*`, `cert-*`) |
| Defect signal, build-light | narrow PMD | **cppcheck** `warning,performance,portability` (not `style`, not `--enable=all`) |

Semgrep's C pack and commercial analyzers are out. Two maintained open-source analyzers plus one formatter cover this module without a third overlapping tool.

## Checks left off on purpose

- clang-tidy `readability-*`, `modernize-*`, `cppcoreguidelines-*`: style or C++ guidance. Layout is clang-format's job.
- `bugprone-easily-swappable-parameters`, `bugprone-reserved-identifier`, `cert-dcl37-c`, `cert-dcl51-cpp`: noisy on a C99 PAM module that already uses POSIX feature-test macros.
- `cert-err33-c`: forty hits on bounded `snprintf`, `fprintf`, and `fclose`. The buffers are sized for the format. Casting those calls to `void` would be noise copied into later edits. Pass 2026-11-08.
- `clang-analyzer-security.insecureAPI.DeprecatedOrUnsafeBufferHandling` and `.strcpy`: flag ordinary C buffer calls. Real bounds bugs still come from the other analyzer and cppcheck checks.
- cppcheck `style` and `--enable=all`: style belongs to clang-format; `all` also pulls information-level noise.

## Scope

`ezkey-pam/src` only (the module that sshd loads). Harnesses under `ezkey-pam/test` are the regression net, not a second scan surface.
