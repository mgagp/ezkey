# Mobile Agentic Testability And E2E Autonomy Roadmap

**Created:** April 29, 2026  
**Context:** Follow-up planning note after a real local validation session covering Bash clean-start,
Admin UI browser login, Demo Device browser approval, Android emulator startup, Android debug install,
and first native automation probing for `ezkey_mobile/`.

---

## Objective

Improve the future testability and agent autonomy of the Ezkey mobile validation workflow without
over-engineering the current stack. The goal is not to replace manual mobile QA immediately; it is
to make the local path progressively more deterministic, more scriptable, and easier to reuse for
browser-driven and eventually native mobile end-to-end validation.

This roadmap is intentionally pragmatic:

1. Stabilize the already-working browser and stack path first.
2. Reduce ambiguity and hidden prerequisites in the Android local run path.
3. Introduce agent-friendly anchors only where they materially improve automation reliability.
4. Delay any heavy native mobile automation investment until the browser and environment layers are
   consistently reliable.

---

## Session Findings

### What worked in this session

1. `ezkey-tests/clean-start.sh` launched successfully through Git Bash on Windows and brought the
   Docker stack to a healthy state.
2. The local browser could access:
   - Admin UI on `http://localhost:5173/login`
   - Demo Device on `http://localhost:8083/phone/ezkey`
3. Passwordless login was successfully initiated in Admin UI for `admin.docker` and approved in the
   Demo Device browser.
4. Admin UI session state after approval was confirmed on `/dashboard`, with visible post-login
   navigation and recent activity entries proving the flow completed.
5. Android emulator startup was successful for the configured AVD `Pixel_7_Pro_virtuel`.
6. `ezkey_mobile` built and installed successfully on the emulator using Android Studio's JBR
   (`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`).
7. The installed mobile app launched successfully via `adb shell monkey -p org.ezkey.mobile ...`.

### What was fragile or incomplete

1. `clean-start.ps1` remains an unreliable entrypoint for this workflow on Windows; Bash is the
   trusted path.
2. The Admin UI browser snapshot tooling can lag behind SPA transitions. In this session, the page
   snapshot still looked stale after login, while direct DOM inspection showed the dashboard was
   actually loaded and usable.
3. The Demo Device browser flow worked functionally, but the page emitted `404` errors on Bootstrap
   webjar assets and a `bootstrap is not defined` page error. The useful content still rendered, but
   the surface is noisier and more fragile than it should be.
4. The native mobile app was installed and launched, but the session did not complete a full
   in-app `bind -> verify -> pending -> respond` flow.
5. Native Android automation is technically possible through `adb` and `uiautomator`, but current
   reliability is not good enough to treat it as a first-class agentic path.
6. During native probing, the emulator surfaced unrelated Android system ANR noise, which reduces
   confidence in blind scripted UI traversal at the device level.
7. `ezkey_mobile` initially lacked a local `.env`, which did not block the build because fallback
   behavior exists, but it is still an avoidable source of ambiguity in repeatable E2E runs.

---

## Core Conclusion

The most realistic near-term agentic E2E path is:

1. Browser-driven Admin UI
2. Browser-driven Demo Device
3. Bash-driven clean-start and local environment setup

The least realistic near-term path is fully autonomous native mobile UI driving on Android without a
dedicated automation layer.

So the correct sequencing is:

1. Harden the browser and environment path first.
2. Make the Admin UI and Demo Device more automation-friendly.
3. Keep native mobile validation as manual or semi-manual smoke for now.
4. Revisit true native automation only after environment and browser flows are stable.

---

## Proposed Phases

## Phase 1 - Local Runbook And Environment Discipline

### Goal

Remove hidden prerequisites and make the currently working local flow easy to reproduce.

### Work items

1. Document the canonical Windows local path explicitly:
   - launch Docker stack with `ezkey-tests/clean-start.sh` via Git Bash
   - use Android Studio JBR for Android builds
   - use the configured emulator profile name
   - validate stack health before browser or mobile steps
2. Add a short mobile E2E local runbook that includes:
   - stack startup
   - Admin UI URL
   - Demo Device URL
   - emulator startup
   - debug install command
   - minimum success signals
3. Document the `.env` expectations for `ezkey_mobile` so the build path is explicit and not left
   to fallback behavior.
4. Clarify which ports are canonical for browser testing during clean-start:
   - Admin UI dev server
   - Demo Device
   - direct API ports
   - Caddy proxy ports

### Expected result

Any developer or agent can reproduce the same baseline environment without rediscovering the shell,
JDK, or emulator assumptions.

---

## Phase 2 - Browser Surface Hardening For Agentic Use

### Goal

Make the already-working browser flows easier to drive reliably.

### Work items

1. Add stable UI anchors in Admin UI for critical actions and state transitions:
   - login submit
   - awaiting approval state
   - post-login dashboard shell
   - integrations list/create
   - enrollments list/create
   - QR/proof-token reveal surfaces
2. Prefer semantic and stable selectors over brittle text-only or CSS-shape-dependent selectors.
   `data-testid` is acceptable where semantic roles are insufficient.
3. Ensure important screens expose stable headings and landmark structure so `read_page` snapshots
   reflect the real navigation state more reliably.
4. Review the Admin UI SPA transition behavior that caused stale-looking browser snapshots after
   successful login, and determine whether route rendering or loading-state surfacing can be made
   more explicit.
5. Evaluate whether the integration and enrollment creation flows already have sufficient stable
   labels for browser agents, or whether a small testability pass is required.

### Expected result

Browser automation can reach “login -> integrations -> create integration -> create enrollment ->
reveal QR/proof token” with high confidence and little custom workaround logic.

---

## Phase 3 - Demo Device Browser Reliability Cleanup

### Goal

Remove avoidable noise from the Demo Device browser path so it can serve as the default approval
surface in agentic E2E tests.

### Work items

1. Fix the missing Bootstrap/webjar asset loading on Demo Device pages.
2. Remove the `bootstrap is not defined` browser error from the standard phone pages.
3. Verify that the critical enrollment and auth pages remain fully functional without console/page
   errors.
4. Add a small browser smoke check for the Demo Device pages used in local E2E:
   - home
   - new enrollment
   - pending auth approval
   - result/confirmation

### Expected result

The Demo Device becomes a clean, low-friction browser approval surface for automated local flows,
instead of a functionally-working but noisy fallback.

---

## Phase 4 - First-Class Browser E2E Golden Path

### Goal

Codify one reliable full-path test that proves the local stack and browser surfaces are ready.

### Candidate scope

1. Login to Admin UI as `admin.docker`
2. Approve login on Demo Device
3. Create a test integration in Admin UI
4. Create a test enrollment in Admin UI
5. Reveal QR or proof-token material
6. Complete enrollment in Demo Device browser
7. Trigger an auth attempt from the operator/browser side if applicable
8. Approve the auth attempt in Demo Device browser
9. Assert success states in both Admin UI and Demo Device

### Why browser first

This already uses surfaces that are visible, accessible, and scriptable with the current browser
tooling. It is the highest-value automation target before native mobile automation.

### Expected result

A representative local “golden path” test exists for the full stack without depending on fragile
Android UI driving.

---

## Phase 5 - Native Mobile Smoke Strategy, Not Full Autonomy Yet

### Goal

Define a realistic role for the Android app in the short term.

### Recommendation

Treat `ezkey_mobile` as a semi-manual smoke surface for now, not as the first target of full
agentic E2E automation.

### Work items

1. Define one short manual smoke checklist for the Android app:
   - app launches on emulator or phone
   - enrollment screen is reachable
   - QR scan path can be exercised on a real device or prepared emulator setup
   - pending auth screen loads
   - approve/deny is reachable
2. Keep Android automation limited to environment-level checks for now:
   - install succeeds
   - app launches
   - process is alive
   - optional UI dump is readable
3. Document the current native automation gap explicitly:
   - current tools allow `adb` and UI dumps
   - no robust native interaction model equivalent to browser navigation exists in this workflow
   - emulator/system ANR noise can invalidate brittle UI-driving scripts

### Expected result

The team uses Android where it adds confidence, without pretending the native automation surface is
 mature enough for unattended full E2E.

---

## Phase 6 - Revisit True Native Automation Later

### Gate condition

Do not invest in heavy native automation until all of the following are true:

1. Browser golden path is stable and valuable
2. Admin UI and Demo Device surfaces are agent-friendly
3. Local environment setup is documented and repeatable
4. The Android app launches reliably on the target emulator/device profile

### Future options

1. Detox or another dedicated React Native E2E layer
2. ADB + accessibility-driven helper scripts for limited flows
3. Device-farm or CI emulator strategy only after local determinism exists

### Expected result

Native automation, if pursued, starts from a stable base instead of compensating for unrelated
environment and UI fragility.

---

## Concrete Gaps Identified In This Session

1. No dedicated mobile E2E runbook ties together Bash clean-start, Admin UI, Demo Device, emulator,
   and Android build/install.
2. Admin UI critical actions are not yet confirmed to expose stable enough selectors everywhere for
   long-lived agentic automation.
3. Demo Device has broken asset references on the main phone pages.
4. Browser snapshot/read tooling can mislead after SPA transitions unless cross-checked.
5. Native Android automation currently relies on low-level ADB inspection rather than a supported
   end-to-end automation layer.
6. `.env` setup in `ezkey_mobile` is easy to forget, even when fallback behavior prevents outright
   build failure.

---

## Suggested Deliverables For A Follow-Up Session

1. A short operational doc for local mobile/browser E2E validation
2. A small Admin UI testability pass on critical flows
3. A Demo Device asset-loading fix
4. One browser E2E golden-path test for Admin UI + Demo Device
5. A documented “native mobile smoke only” policy until a dedicated automation strategy is approved

---

## Relevant Files And Surfaces

- `c:\github\ezkey-worktree3\ezkey-tests\clean-start.sh`
- `c:\github\ezkey-worktree3\ezkey_mobile\README.md`
- `c:\github\ezkey-worktree3\ezkey_mobile\.env.example`
- `c:\github\ezkey-worktree3\ezkey_mobile\android\app\build.gradle`
- `c:\github\ezkey-worktree3\ezkey-admin-ui\`
- `c:\github\ezkey-worktree3\ezkey-demo-device\`
- `http://localhost:5173/login`
- `http://localhost:8083/phone/ezkey`
- `http://localhost:18080`
- `http://localhost:19080`

---

## Verification For This Roadmap

1. Confirm future work starts from Bash clean-start, not PowerShell clean-start.
2. Confirm one browser-driven login + Demo Device approval flow remains green.
3. Confirm the Admin UI exposes stable anchors for integration and enrollment creation.
4. Confirm the Demo Device browser pages load without asset errors.
5. Confirm Android install + launch remains reproducible on the chosen emulator profile.
6. Confirm the team agrees that native mobile stays smoke/manual until a dedicated automation phase
   is explicitly approved.

---

## Decision Summary

- Near-term priority: browser and environment hardening.
- Approved practical baseline: Bash clean-start + Admin UI browser + Demo Device browser + Android
  install/launch smoke.
- Deferred investment: fully autonomous native mobile UI testing.
- Key principle: improve the simplest reliable path first before adding heavier automation layers.