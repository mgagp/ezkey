# Plan: Danger Zone Enrollment Card UX

> **Status : COMPLETE — 2026-05-01**
> This archived plan records the completed mobile Danger Zone UX improvement for enrollment mini-cards, deletion confirmation, and validation/stabilization notes.

## Outcome

- The mobile Danger Zone now surfaces higher-value local enrollment context without turning the screen into a detail view.
- Each enrollment card keeps a compact presentation while showing a human-friendly identifier, integration context, tenant plus installation context, and a recency signal.
- The deletion confirmation now includes an explicit recap of the target enrollment and lightweight warnings for favorite and recently active enrollments.
- The workstream is considered complete after focused automated validation, signed Android release validation, and user-confirmed manual validation.

## What Was Implemented

- Enriched Danger Zone cards in `ezkey_mobile/app/screens/DangerZone/DangerZoneScreen.tsx`.
- Display-name fallback order: `enrollmentName`, then `deviceLabel`, then `integrationName`.
- Context line: integration name plus tenant and installation context, with installation host hint when the installation name is generic.
- Recency line: relative label from `lastActivityAt`, with fallback to `createdAt` when needed.
- Favorite indicator: lightweight badge only, not a heavier secondary action model.
- Safer ordering: favorites first, then newest by `createdAt`, matching the broader mobile ordering posture.
- Stronger delete confirmation message with enrollment, integration, tenant, installation, last activity, and high-signal warnings.

## Validation Performed

### Focused automated validation

- Added focused Jest coverage in `ezkey_mobile/__tests__/DangerZoneScreen.test.tsx`.
- Verified enriched card rendering.
- Verified safer ordering behavior.
- Verified detailed deletion confirmation content.

### Test-environment stabilization

- The mobile Jest runner initially failed because the workspace still referenced the legacy `react-native` preset.
- Updated `ezkey_mobile/jest.config.js` to use `@react-native/jest-preset`.
- Added `@react-native/jest-preset` to `ezkey_mobile/package.json` so the focused test suite can execute in this workspace.
- Re-ran the focused test successfully.

### Release build and deployment validation

- Confirmed release signing properties were configured locally through `EZKEY_UPLOAD_*` Gradle properties.
- Confirmed the connected device was available over ADB.
- Produced a signed release APK and a signed release AAB under JDK 17.
- Verified APK signing with the configured upload key certificate.
- Installed the signed release APK successfully on the physical device.
- Generated installable APKs from the signed AAB with bundletool and installed them successfully on the same device.
- Launched the deployed app successfully after installation.

### Manual product validation

- Automatic UI smoke navigation was intentionally abandoned in favor of manual validation because the user completed the product check directly on device.
- The user confirmed the Danger Zone card presentation and alert dialog behavior work correctly in practice.

## Stabilization Notes

- The Android release flow in this workstation requires JDK 17, not the system JDK 25.
- The practical PowerShell pattern that worked reliably was:

```powershell
$env:JAVA_HOME = "C:\tools\jdk17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Set-Location C:\github\ezkey-worktree1\ezkey_mobile
corepack yarn android:assemble:release
corepack yarn android:bundle:release
```

- The release signing configuration resolved to the user-scoped upload key, not the debug keystore.
- The release build emitted non-blocking Kotlin and Gradle deprecation warnings, but the signed outputs were produced successfully.
- The app emitted a non-blocking missing `.env` warning during Android build configuration in this workspace.

## Historical UX Note: Post-Delete Navigation

During final validation, the user asked why confirming an individual deletion returns to the previous screen instead of keeping the user inside Danger Zone.

Current implementation behavior:

- `DangerZoneScreen` calls `navigation.goBack()` after a successful individual deletion.
- This means the app exits the destructive screen rather than leaving the user in a context optimized for repeated deletion.

Rationale recorded for historical traceability:

- The action is modeled as an exceptional cleanup step, not as a batch-maintenance workflow.
- In realistic usage, operators usually remove a single enrollment because a specific exceptional event made that enrollment no longer needed.
- Exiting the destructive context after success reduces the chance of accidental follow-up deletions through inertia.
- This follows a pragmatic UX rule for dangerous actions: close the destructive mini-flow after successful completion unless the workflow is explicitly designed for repeated destructive operations.

The user explicitly agreed that this rationale is coherent with the real expected usage pattern and requested that this explanation be preserved as a historical design note.

## Plan: Danger Zone Enrollment Card UX

Improve the mobile Danger Zone enrollment cards so operators can identify the correct enrollment to delete using information already available in local memory, while preserving a compact destructive-flow posture and avoiding a full detail-screen density.

**Steps**
1. Define the target compact card shape with title, context, and recency lines.
2. Add a clear display-name fallback so the correct enrollment is easier to identify.
3. Surface tenant and installation context already available locally.
4. Surface recency information to reduce mistaken deletion of recent enrollments.
5. Add lightweight high-signal indicators such as favorite status.
6. Strengthen the delete confirmation dialog with explicit contextual recap.
7. Validate the screen with focused tests and device validation.
8. Archive implementation, stabilization, and validation notes when complete.

**Relevant files**
- `c:\github\ezkey-worktree1\ezkey_mobile\app\screens\DangerZone\DangerZoneScreen.tsx`
- `c:\github\ezkey-worktree1\ezkey_mobile\__tests__\DangerZoneScreen.test.tsx`
- `c:\github\ezkey-worktree1\ezkey_mobile\jest.config.js`
- `c:\github\ezkey-worktree1\ezkey_mobile\package.json`

**Verification**
1. Focused Jest validation passes for the Danger Zone screen.
2. Signed Android release artifacts build successfully under JDK 17.
3. Signed artifacts install successfully on the physical device.
4. Manual validation confirms the card UX and deletion alert behavior are correct.

**Decisions**
- Included scope: compact contextual enrichment, confirmation strengthening, safer ordering, focused validation, and historical archiving.
- Excluded scope: batch delete behavior, search/filter features, and turning Danger Zone into a detailed enrollment management screen.
- Historical design note retained: successful individual deletion exits the destructive flow instead of keeping the user in Danger Zone.