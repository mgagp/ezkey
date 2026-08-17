# Ezkey Mobile — Play Store listing copy

Paste-ready listing text aligned with in-app Release Notes after
`TB-2026-08-14-mobile-exit-experimental-messaging`. English is the source language.

## Identity

| Field | Value |
| --- | --- |
| Package | `org.ezkey.mobile` |
| Launcher name | Ezkey |
| Listing name | Ezkey |
| Short description (max 80) | Approve sign-ins from a trusted Android device. |
| Category | Productivity (or Tools) — confirm in Console |
| Privacy policy | https://ezkey.org/privacy.html |
| Support email | support@ezkey.org |
| OS | Android 12 or later (`minSdk` 31) |

Do not use "Ezkey Authenticator" as the launcher name. It may appear in the **short description**
if search clarity needs it.

## Full description (English)

Ezkey Mobile is the Android companion for Ezkey, a self-hosted cryptographic MFA platform.

Scan an enrollment QR from your organization's Ezkey installation, then approve or deny
authentication requests on this phone. Signing keys stay in Android Keystore (StrongBox when
the device provides it). The app does not collect analytics or advertising identifiers.

Requires Android 12 or later. Camera access is used only to scan enrollment QR codes.

Learn more: https://ezkey.org

## Full description (French)

Ezkey Mobile est l’application Android compagnon d’Ezkey, une plateforme MFA cryptographique
auto-hébergée.

Scannez un QR d’enrôlement depuis l’installation Ezkey de votre organisation, puis approuvez ou
refusez les demandes d’authentification sur ce téléphone. Les clés de signature restent dans
Android Keystore (StrongBox lorsque l’appareil le fournit). L’application ne collecte pas
d’analytique ni d’identifiants publicitaires.

Android 12 ou plus récent. L’accès à la caméra sert uniquement à scanner les QR d’enrôlement.

En savoir plus : https://ezkey.org/fr/

## What's new (Play Console)

Keep this in lockstep with in-app Release Notes (`releaseNotes.*` in
`ezkey_mobile/app/i18n/resources.ts`).

English:

```
Ezkey Mobile 1.0 for Android.

• Enroll a trusted device by scanning a QR code
• Approve or deny authentication requests on this phone
• Keys stay in Android Keystore (StrongBox when available)
• Android 12 or later

This app does not collect analytics.
```

French:

```
Ezkey Mobile 1.0 pour Android.

• Enrôlez un appareil de confiance en scannant un code QR
• Approuvez ou refusez les demandes d’authentification sur ce téléphone
• Les clés restent dans Android Keystore (StrongBox si disponible)
• Android 12 ou plus récent

Cette application ne collecte pas d’analytique.
```

## Feature graphic

Play requires **1024 × 500** PNG or JPEG. Produce from the existing Ezkey logo and sober dark
surface used in the app (`colors.background`). No "experimental" or "invite only" wording.

Suggested layout: Ezkey wordmark left, short line "Approve sign-ins from a trusted device" right,
Android 12+ in small type.

Store this asset in Play Console only (do not commit enrollment screenshots that show real QR
payloads or proof material). Working files may live under a gitignored maintainer folder.

## Phone screenshot shot list (minimum two)

Capture on a physical device with **release** or production-clean debug, English first, no real
customer enrollments (use a clean-start Demo Device / lab installation).

1. **Home empty** — welcome copy and add-enrollment FAB. No experimental banner.
2. **Home with one enrollment** — grouped installation list.
3. **Enrollment wizard QR** — scanner or pre-scan explanation (no live secret QR in the asset if
   it would leak a real enrollment).
4. **Pending authentication** — approve/deny (lab attempt).
5. **Settings** — What's new, Coming soon, version footer `Ezkey v1.0.0`.
6. **Release notes** — Version 1.0 / requirements Android 12+.

Tablet screenshots: optional unless Play asks for them.

## In-app update note (not listing copy)

Flexible Play in-app updates ship in this binary. The store listing itself does not need to
describe that mechanism. The prompt cannot appear until a **later** `versionCode` is published.
