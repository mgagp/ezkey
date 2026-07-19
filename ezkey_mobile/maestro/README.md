# Maestro — Android real-device pilot (`TB-2026-0002`)

Maestro drives **critical-path UI** on a **physical Android device** (`adb`). Backend setup and queueing of pending auth attempts stay with the **clean-start Docker stack**, **Admin UI / Postman**, or **`ezkey-tests`** (see touchpoints below).

## Prerequisites

- **JDK / Android**: debug or release APK installed on the device (`org.ezkey.mobile`).
- **`adb`**: one device in `device` state (`adb devices`).
- **Maestro CLI**: [Maestro installation](https://docs.maestro.dev/getting-started/installing-maestro).
- **Shell**: Git Bash on Windows (repo convention); use `scripts/run-real-device-pilot-maestro.sh`.
- **Enrollment**: complete enrollment once (QR/bootstrap). Note the numeric **enrollment id** from Admin UI or API — it must match the row `testID` on Home (`ezkey.e2e.home.enrollment.<id>`). That row exists in the accessibility tree **only when its installation section is expanded** (see **Home list** below).

## Home list (installations accordion)

Enrollments are grouped under **installation** headers (`+` / `−`). Rows use `testID` **`ezkey.e2e.home.enrollment.<id>`** only while the parent section is **expanded**. On cold start, the app expands **all** installation sections when there are **at most six** groups (so Maestro can find rows without manual taps); with **more than six** groups, only the first stays open by default — use **`ezkey.e2e.home.installation.<installationId>`** to tap the header and expand before `scrollUntilVisible` on an enrollment. **`ENROLLMENT_ID`** must still match an enrollment that **exists on the device** (wrong id → permanent “element not found”).

The flows **do not** perform bind/verify. Complete enrollment manually (or via your own automation), then run the flows from **Home** with the chosen enrollment visible.

Each run **stops and relaunches** the app (`stopApp: true`) so Maestro always starts from the **Home** stack root. If you used `stopApp: false`, leaving the app on Enrollment Detail or Pending would make the first assertion on the Home shell fail even though the UI is “fine”. Flows wait on **`ezkey.e2e.home.root`** (not only the FAB): on some devices Maestro’s visibility check is flaky for a small floating action button near the bottom safe area.

## Pending auth attempt

Before running a flow, queue an auth attempt for that enrollment **without** a 2-digit challenge when using `flows/pilot_pending_respond.yaml`:

- Admin API `POST /auth-attempts` with `challengeRequested: false`, or equivalent operator path.

If the enrollment requires a challenge (`challengeRequested: true`), use `flows/pilot_pending_respond_with_challenge.yaml` and pass **`CHALLENGE_CODE`** (two digits, e.g. `export CHALLENGE_CODE=42`) to the runner script. The code must match what the Admin UI shows for that auth attempt. The flow uses **`tapOn` the `challengeDigits` control with `point: "50%,50%"`** so the gesture hits the full-area hidden `TextInput` overlay (React Native often does **not** expose a separate `EditText` `resource-id` in the hierarchy). After `inputText`, it waits, then **taps `attemptScroll` near the top** to blur the field before **Approve** (`point` + `retryTapIfNoChange`). The hook also calls **`Keyboard.dismiss()`** when respond starts. Reinstall the debug APK after changing `PinCodeInput` / Pending auth / `usePendingAuth`.

After a failed run, search the Maestro log or a UI dump for these ids: if **`ezkey.e2e.pendingAuth.globalErrorState`** appears, the app stayed on Pending auth with **`respond` or verification** failure; if **`ezkey.e2e.pendingAuth.respondInFlight`** stays visible for the whole timeout, the **`respond` HTTP** call may be hanging; if **`ezkey.e2e.enrollmentDetail.screen`** appears without **`checkPending`**, something else is wrong with layout.

## Investigation method (Android hierarchy)

Use this loop when **Maestro says a command ran** but **Admin or logcat show no business effect**, or when **`Element not found`** for a `testID` you believe you set.

1. **Treat the accessibility / UiAutomator tree as ground truth** for what Maestro can select. It is **not** the same as the React tree: React Native may merge children, omit `resource-id` on hidden `TextInput`s, or flatten views (`collapsable`).
2. **Pull evidence** from the same moment in the run:
   - Maestro verbose log (often includes a `TreeNode` snippet with `resource-id=…`).
   - Optional full dump: `adb shell uiautomator dump /sdcard/window_dump.xml` then `adb pull /sdcard/window_dump.xml` and search for your `testID` string (or Android Studio **Layout Inspector** on the foreground activity).
3. **Confirm instrumentation**: If your `testID` never appears as `resource-id` on any node, **`tapOn` by id cannot work** — fix the native hierarchy (layout, `collapsable`, parent `accessible` behavior) or **change the automation strategy** (see below).
4. **Remember Maestro `id` is matched as regex** unless you escape dots; a pattern like `foo.bar` matches more than the literal string. Prefer a single known-good id from the dump, or escape: `foo\\.bar`.
5. **Prefer resilient gestures**: When only a **parent** exposes an id (e.g. `challengeDigits`) but the real focus target is an overlay `TextInput`, use Maestro **`tapOn` that id with `point`** (percentages relative to the element bounds) so the coordinates land on the overlay — see [Maestro `tapOn`](https://docs.maestro.dev/reference/commands-available/tapon). **`retryTapIfNoChange`** helps flaky first taps.
6. **Correlate time and layers**: Align timestamps between Maestro steps, **`[PendingAuthRespond]`** logcat lines (when `EZKEY_PENDING_AUTH_FLOW_TRACE` is on), and Admin / Auth API behaviour to see whether the gap is **focus/input**, **JS early return**, **network**, or **crypto**. Use the **UI vs hook** split: `pendingAuth_ui_approve_press` fires in the button `onPress` **before** `handleRespond`; if it is **missing** after a Maestro Approve tap, the gesture never reached React (`onPress` not run). If **`pendingAuth_ui_approve_press`** is followed by **`handleRespond_skipped_*`** instead of **`handleRespond_try_begin`**, the hook returned early (e.g. challenge length, already processing). If **`try_begin`** appears but not **`handleRespond_after_device_sign`**, suspect **biometric / device-credential** blocking `signForRespond`. If **`after_device_sign`** but not **`handleRespond_http_verified`**, suspect **HTTP / Auth URL**. If **`handleRespond_catch`** or **`abort_*`**, read the logged message / outcome.

### Maestro daemon lines (tag `Maestro`)

During **`extendedWaitUntil`**, the Maestro process often logs **`QueryController: Could not detect idle state`** while polling — the UI framework is not reporting “idle”; that alone is not proof of an app defect.

**`Skipping invisible child`** on an `org.ezkey.mobile` **`EditText`** that still shows **`text: 98`** (or your digits) with **`visible: false`** is typical for RN **opacity‑0** inputs: they exist in the accessibility tree but are filtered as not visible. If the **same** subtree (including that `EditText`) repeats for the whole wait while **`checkPending` never matches**, the app likely **stayed on Pending auth** (navigation did not occur). Pair this with **`ReactNativeJS`** lines **`[PendingAuthRespond]`**: no `pendingAuth_ui_approve_press` → the Approve tap did not run JS; press without `try_begin` → unlikely; `try_begin` without later steps → see step 6 above.

## Respond-path logging (device)

<!-- Hypothesis-validation (Maestro pilot): remove this whole section when diagnosis is complete. -->

1. Set **`EZKEY_PENDING_AUTH_FLOW_TRACE=true`** in `.env` (see `.env.example`) and **rebuild** the native app (`react-native-config` bakes values at compile time).
2. Capture logs in one of two ways:
   - **Manual:** in a second terminal, `adb logcat | grep PendingAuthRespond` while Maestro runs.
   - **Bundled:** from `ezkey_mobile/`, run with **`MAESTRO_LOGCAT=1`** so `scripts/run-real-device-pilot-maestro.sh` clears logcat, tees **ReactNativeJS** / **ReactNative** lines to `maestro/reports/maestro-pilot-<UTC>-logcat.txt`, and stops the pipe when the script exits.

Example (after a **debug rebuild** with `EZKEY_PENDING_AUTH_FLOW_TRACE=true` in `.env`):

```bash
export ENROLLMENT_ID=9
export CHALLENGE_CODE=94
MAESTRO_LOGCAT=1 ./scripts/run-real-device-pilot-maestro.sh
```

Lines use prefix **`[PendingAuthRespond]`** with an ISO timestamp and a step id, for example:

| Step id | Meaning |
| --- | --- |
| `pendingAuth_ui_approve_press` | Approve / Deny `TouchableOpacity` `onPress` ran (UI layer). |
| `handleRespond_try_begin` | Hook entered the respond `try` (after challenge length gate). |
| `handleRespond_after_prefs` | Includes **`protectedSigning`** (app biometric/device-credential prompt when true). |
| `handleRespond_after_device_sign` | Device signing finished; about to call HTTP `respond`. |
| `handleRespond_http_verified` | HTTP response verified; about to navigate back. |
| `handleRespond_skipped_*` / `handleRespond_abort_*` / `handleRespond_catch` | Early exit or error (see JSON detail / truncated message). |

No challenge digits or tokens are logged — correlate **`authAttemptId`** with Admin.

**Note:** `EZKEY_PENDING_AUTH_FLOW_TRACE` must be **true in the built app**; exporting it only in the shell before `maestro` does **not** enable JS logs unless your build already read it from `.env`.

## Local auth / biometrics

If **Settings → Security** uses a protected mode in which the app requests biometric or device-credential confirmation before **respond**, Maestro may block on the system sheet. For the first pilot iterations, use **standard** signing posture on the device, or complete the prompt manually when experimenting.

## Selector inventory (`testID`)

| id | Screen / component |
| --- | --- |
| `ezkey.e2e.home.root` | Home — full-screen container (prefer this over the FAB for Maestro “visible” waits; small absolute FABs can fail visibility heuristics near the gesture bar) |
| `ezkey.e2e.home.fabAddEnrollment` | Home — FAB add enrollment |
| `ezkey.e2e.home.installation.<installationId>` | Home — installation section header (expand/collapse) |
| `ezkey.e2e.home.enrollment.<enrollmentId>` | Home — enrollment row |
| `ezkey.e2e.enrollmentDetail.screen` | Enrollment detail — root screen container (after content loaded) |
| `ezkey.e2e.enrollmentDetail.loading` | Enrollment detail — loading branch (no `checkPending` in hierarchy yet) |
| `ezkey.e2e.enrollmentDetail.checkPending` | Enrollment detail — Check pending |
| `ezkey.e2e.pendingAuth.attemptScroll` | Pending auth — main scroll area when a pending attempt is shown |
| `ezkey.e2e.pendingAuth.globalErrorState` | Pending auth — full-screen error branch (`respond` / network failure) |
| `ezkey.e2e.pendingAuth.respondInFlight` | Pending auth — thin marker row while `respond` request is in flight (`isProcessing`) |
| `ezkey.e2e.pendingAuth.challengeDigits` | Pending auth — `Pressable` over the two PIN boxes + hidden `TextInput` overlay; **Maestro** uses `tapOn` this id with **`point: "50%,50%"`** before `inputText` (child `EditText` often has no separate `resource-id`). |
| `ezkey.e2e.pendingAuth.approve` | Pending auth — Approve (`tapOn` with **`point: "50%,50%"`** and **`retryTapIfNoChange: true`** in pilot flows). |
| `ezkey.e2e.pendingAuth.deny` | Pending auth — Deny |
| `ezkey.e2e.pendingAuth.checkAgain` | Pending auth — empty state check again |
| `ezkey.e2e.pendingAuth.tryAgain` | Pending auth — error try again |

## Flows

| File | Purpose |
| --- | --- |
| `flows/pilot_pending_respond.yaml` | Home → detail → check pending → approve (no challenge input). |
| `flows/pilot_pending_respond_with_challenge.yaml` | Same, plus 2-digit challenge entry. |

## Runner

From `ezkey_mobile/` (Git Bash):

```bash
export ENROLLMENT_ID=123   # required
./scripts/run-real-device-pilot-maestro.sh
```

Optional:

```bash
export CHALLENGE_CODE=42   # selects challenge flow when set
export FLOW=/path/to/custom.yaml
export REPORT_DIR=./maestro/reports
export MAESTRO_VERBOSE=0   # disable maestro --verbose (quieter)
export MAESTRO_DEBUG_OUTPUT=1   # maestro.log + artifacts under maestro/reports/debug-<UTC>/
export MAESTRO_LOGCAT=1   # tee adb logcat (ReactNative*) to maestro-pilot-<UTC>-logcat.txt during the run
```

Each run writes a **full console transcript** to `maestro/reports/maestro-pilot-<UTC>.log` (same folder as the JUnit XML).

## Short repeat loop

Maestro does not create auth attempts. To exercise a **short loop** today: queue a new attempt on the backend between runs (Admin UI, Postman, or a future thin script), then re-run the same Maestro flow. Example:

```bash
for i in 1 2 3; do
  echo "Queue pending attempt $i (backend), then Enter"; read -r _
  ENROLLMENT_ID=123 ./scripts/run-real-device-pilot-maestro.sh || break
done
```

**Planned:** JUnit-driven loops, per-iteration artifact folders, and seeded scenario variance are specified in `ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md` (`TB-2026-0002` next phase).

## `ezkey-tests` touchpoints

- **`TestDataFactory#createAuthAttempt`** — programmatic queue via Admin API (`challengeRequested` flag).
- **`OperationalChurnTest`** — full bind/verify/pending/respond churn pattern against the Docker stack (reference for steady-state semantics, not a substitute for Maestro UI driving the phone).

Bootstrap admin tokens are typically cached under **`ezkey-tests/.ezkey-test/bootstrap-credentials.json`** after stack bootstrap (see `ezkey-tests/README.md`).
