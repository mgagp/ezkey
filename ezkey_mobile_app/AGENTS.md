# ezkey_mobile_app — Agent Guide

## Tech Stack

- **React Native 0.76.0** (Hermes engine, Old Architecture / Paper)
- **Navigation**: `@react-navigation/stack` (JS-based, NOT native-stack)
- **Camera/QR**: `react-native-vision-camera` + `react-native-worklets-core` + custom `scanEzkey` frame processor
- **Crypto**: Native Kotlin `EzkeyCryptoModule` (EC P-256, Android Keystore)
- **State**: Zustand + React Query v5
- **Config**: `react-native-config` (reads `.env`)

## Build: JDK 17 on Windows

The Ezkey backend uses JDK 25, but React Native 0.76 requires **JDK 17**. Android Studio bundles a compatible JBR (JetBrains Runtime).

**Build commands** (from `ezkey_mobile_app/`, Git Bash):

```bash
npm run android:clean    # Stop daemons + delete .gradle cache
npm run android:jdk17    # Build with JDK 17 auto-detection
```

The `scripts/android-with-jdk17.sh` script:
- Detects the JBR from Android Studio (`C:/PROGRA~1/Android/ANDROI~1/jbr`)
- Uses Windows **short paths** to avoid spaces breaking `gradlew.bat`
- Exports `JAVA_HOME` and `GRADLE_OPTS` before delegating to `npx react-native run-android`

`gradle.properties` also sets `org.gradle.java.home` as a fallback for IDE builds.

## Known Version Constraints

When version issues arise, **compare against `ezkey_mobile/`** — it is a proven working configuration.

| Package | Pinned version | Why |
|---------|---------------|-----|
| `react-native-config` | `1.5.9` exact | v1.6.x dropped Android autolinking |
| `@react-navigation/native` | `7.1.19` exact | Later versions pull incompatible transitive deps |
| `@react-navigation/native-stack` | `7.6.2` exact | Requires `react-native-screens >= 4.0` at runtime — kept for types only |
| `@react-navigation/stack` | `7.6.2` exact | JS-based stack, compatible with `screens@3.30.1` |
| `react-native-screens` | `3.30.1` exact | Patched via `patch-package` (nullability fix in `ScreenViewManager.kt`) |
| Gradle wrapper | `8.13` | 8.10.2 has `Files.move()` bug on Windows |
| Android Gradle Plugin | `8.6.0` | Must be pinned in `android/build.gradle` |

## Critical Config Files

- **`babel.config.js`** must include `'react-native-worklets-core/plugin'` — without it, frame processors crash at runtime.
- **`index.js`** must have `import 'react-native-gesture-handler'` as the **first import** — required by `@react-navigation/stack`.
- **`patches/react-native-screens+3.30.1.patch`** — applied automatically via `postinstall` script. Do not remove.

## Metro Cache

After changing `babel.config.js` or `metro.config.js`, always restart Metro with cache reset:

```bash
npx react-native start --reset-cache
```
