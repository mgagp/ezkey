---
public: false
---
# Mobile Android build — JDK resolution for agents

## Date

2026-05-31

## Context

During closeout of the mobile stack modernization program (`I-2026-05-29`, GitHub #177), agents
repeatedly failed Android Gradle builds on the maintainer Windows workstation before eventually
succeeding. Failure modes included:

- `JAVA_HOME` pointing at **JDK 25** on PATH (Ezkey backend JDK) — React Native 0.85 Android
  requires JDK **17 or 21** (`Unsupported class file major version 69`).
- Assumed Android Studio JBR at `C:\Program Files\Android\Android Studio\jbr` while the actual
  install uses `Android Studio1\jbr`.
- Long `gradlew clean installDebug` runs started without a prior **`adb devices`** check; wireless
  debugging disconnected before `installDebug`, producing a green compile but failed install.

The program already documented JDK 17 in README troubleshooting, but agents did not treat that
path as mandatory entry workflow.

## Working assumptions

- Mobile Android validation remains **Android-first** and **device-smoke gated** for native slices.
- Maintainers may keep JDK 25 as the default for backend work; mobile Android must not inherit it.
- Git Bash is the canonical shell for Gradle on Windows in this repo.
- Workstation-specific paths belong in **probe scripts**, not in agent improvisation.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Document only in README | Low churn | Agents skip README troubleshooting under time pressure |
| Hard-code one JBR path in docs | Simple | Breaks when Android Studio folder name differs |
| Canonical probe script + AGENTS + Cursor rule | First-try success; probe list extensible | Small script maintenance |

## Decision

Adopt a **canonical Android debug install script** with JDK probing and adb pre-check:

- `ezkey_mobile/scripts/resolve-android-jdk.sh` — resolve JDK 17/21 (override via `EZKEY_ANDROID_JAVA_HOME`).
- `ezkey_mobile/scripts/build-install-debug-clean.sh` — `adb` check → uninstall → clean → `installDebug` → launch.
- Document as mandatory agent entry in `ezkey_mobile/AGENTS.md` and `.cursor/rules/ezkey-mobile-android-build.mdc`.

Agents must **not** run bare `./gradlew` on Windows without sourcing the resolver.

## Consequences

- Yarn alias: `yarn android:install:debug:clean`.
- `android-with-jdk17.sh` / `install-debug-after-uninstall.sh` delegate to the resolver (no `unset JAVA_HOME` gamble).
- Future toolchain TBs for native mobile should list **`adb devices` before Gradle** in functional gates.

## Related documents

- [`ezkey_mobile/AGENTS.md`](../../../ezkey_mobile/AGENTS.md) — Android debug build section
- [`ML-2026-05-29-mobile-stack-modernization.md`](../../global/backlog/method-logs/ML-2026-05-29-mobile-stack-modernization.md)
- [`TB-2026-05-29-mobile-stack-modernization.md`](../../global/backlog/TB-2026-05-29-mobile-stack-modernization.md)
