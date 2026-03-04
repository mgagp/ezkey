# Phase 1 Upgrade Guide: React Native 0.76 → 0.83

> Detailed step-by-step guide for upgrading Ezkey Mobile to React Native 0.83.  
> Written for developers new to mobile development.

---

## Table of Contents

1. [Before You Start](#1-before-you-start)
2. [Android Studio Update](#2-android-studio-update)
3. [Node.js Version](#3-nodejs-version)
4. [Backup Your Work](#4-backup-your-work)
5. [Update Dependencies](#5-update-dependencies)
6. [Clear All Caches](#6-clear-all-caches)
7. [JDK and the android:jdk17 Script](#7-jdk-and-the-androidjdk17-script)
8. [Apply Changes](#8-apply-changes)
9. [Verify the Build](#9-verify-the-build)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Before You Start

### What Phase 1 Changes

- **react-native**: 0.76.0 → 0.83.0
- **react**: 18.3.1 → 19.2.x
- **@react-native/\*** packages: 0.76.0 → 0.83.x
- **@react-native-community/cli**: 15.0.0-alpha.2 → 15.x (stable)
- **react-test-renderer**: 18.3.1 → 19.2.x
- **@types/react**: ^18.2.6 → ^19.x

### What Stays the Same (for now)

- Vision Camera, Keychain, Navigation, AsyncStorage, etc. (Phase 2–4)

### Time Estimate

- **First time**: 1–2 hours (including Android Studio setup)
- **If environment is ready**: 30–45 minutes

---

## 2. Android Studio Update

### Do You Need to Update?

React Native 0.83 requires:

- **Android SDK Platform 35** (Android 15 – VanillaIceCream)
- **Android SDK Build-Tools 36.0.0**
- **JDK 17**

### How to Check Your Current Setup

1. Open **Android Studio**
2. Go to **File → Settings** (or **Android Studio → Preferences** on macOS)
3. **Languages & Frameworks → Android SDK**
4. Open the **SDK Platforms** tab → check if **Android 15 (API 35)** is installed
5. Open the **SDK Tools** tab → check if **Android SDK Build-Tools 36.0.0** is installed

### How to Install Missing Components

1. In Android Studio: **Tools → SDK Manager** (or the gear icon in the welcome screen)
2. **SDK Platforms** tab:
   - Enable **Show Package Details**
   - Expand **Android 15 (VanillaIceCream)**
   - Check **Android SDK Platform 35**
   - Check **Intel x86 Atom_64 System Image** (or **Google APIs Intel x86 Atom**) for the emulator
3. **SDK Tools** tab:
   - Enable **Show Package Details**
   - Expand **Android SDK Build-Tools**
   - Check **36.0.0**
   - Check **Android SDK Command-line Tools (latest)**
4. Click **Apply** and wait for the download

### Android Studio Version

- Use **Android Studio Ladybug (2024.2.1)** or newer
- If you are on an older version: **Help → Check for Updates**

---

## 3. Node.js Version

React Native 0.83 requires **Node.js 20.19.4 or newer**.

### Check Your Version

```bash
node -v
```

### If You Need to Update

- **Windows**: Download from [nodejs.org](https://nodejs.org/) (LTS 20.x or 22.x)
- **macOS**: `nvm install 20` then `nvm use 20` (if using nvm)
- **Alternative**: Use [fnm](https://github.com/Schniz/fnm) or [volta](https://volta.sh/)

---

## 4. Backup Your Work

### Create a Git Branch

```bash
cd ezkey_mobile
git checkout -b upgrade/rn-0.83-phase1
```

### Optional: Full Backup

```bash
# From project root
cp -r ezkey_mobile ezkey_mobile.backup
```

---

## 5. Update Dependencies

### Option A – Manual Edit

Edit `package.json` and change the following.

**Dependencies:**

| Package | From | To |
|---------|------|-----|
| react | 18.3.1 | 19.2.0 |
| react-native | 0.76.0 | 0.83.0 |

**DevDependencies:**

| Package | From | To |
|---------|------|-----|
| @react-native-community/cli | 15.0.0-alpha.2 | 15.1.3 |
| @react-native-community/cli-platform-android | 15.0.0-alpha.2 | 15.1.3 |
| @react-native-community/cli-platform-ios | 15.0.0-alpha.2 | 15.1.3 |
| @react-native/babel-preset | 0.76.0 | 0.83.0 |
| @react-native/eslint-config | 0.76.0 | 0.83.0 |
| @react-native/metro-config | 0.76.0 | 0.83.0 |
| @react-native/typescript-config | 0.76.0 | 0.83.0 |
| @types/react | ^18.2.6 | ^19.0.0 |
| @types/react-test-renderer | ^18.0.0 | ^19.0.0 |
| react-test-renderer | 18.3.1 | 19.2.0 |

**Engines:**

```json
"engines": {
  "node": ">=20.19.4"
}
```

### Option B – Yarn Commands

```bash
cd ezkey_mobile
yarn add react-native@0.83.0 react@19.2.0
yarn add -D @react-native/babel-preset@0.83.0 @react-native/metro-config@0.83.0 @react-native/typescript-config@0.83.0 @react-native/eslint-config@0.83.0
yarn add -D @react-native-community/cli@^15.1 @react-native-community/cli-platform-android@^15.1 @react-native-community/cli-platform-ios@^15.1
yarn add -D react-test-renderer@19.2.0 @types/react@^19 @types/react-test-renderer@^19
```

Then manually set `"node": ">=20.19.4"` in `engines`.

### After Editing

```bash
yarn install
```

---

## 6. Clear All Caches

**Important:** Caches from RN 0.76 can cause build failures. Clear everything before building.

### Step 6.1 – Metro Bundler Cache

```bash
cd ezkey_mobile
npx react-native start --reset-cache
```

Stop it with `Ctrl+C` after it starts. The `--reset-cache` clears the Metro cache.

### Step 6.2 – Yarn Cache (Optional but Recommended)

```bash
yarn cache clean
```

### Step 6.3 – Android Gradle Cache

```bash
cd ezkey_mobile/android
./gradlew clean
```

On Windows (PowerShell or Git Bash):

```powershell
cd ezkey_mobile\android
.\gradlew clean
```

### Step 6.4 – Delete Build Folders

```bash
# From ezkey_mobile
rm -rf android/app/build
rm -rf android/build
rm -rf android/.gradle
```

On Windows (PowerShell):

```powershell
Remove-Item -Recurse -Force android\app\build -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force android\build -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force android\.gradle -ErrorAction SilentlyContinue
```

### Step 6.5 – iOS (if you build for iOS)

```bash
cd ezkey_mobile/ios
rm -rf build
rm -rf Pods
rm Podfile.lock
bundle exec pod install
```

### Step 6.6 – Node Modules (Nuclear Option)

If you still see strange errors:

```bash
cd ezkey_mobile
rm -rf node_modules
yarn install
```

---

## 7. JDK and the android:jdk17 Script

### Why JDK 17?

React Native 0.83 requires **JDK 17**. If your system uses JDK 25 (or another version), the Android build can fail with errors like:

- `Error resolving plugin [id: 'com.facebook.react.settings']`
- `Unsupported class file major version 69`

### Do You Need the Script?

- **Yes** if `java -version` shows 21, 25, or another non-17 version
- **No** if you already use JDK 17

### How to Use the Script

From Git Bash or a terminal:

```bash
cd ezkey_mobile
yarn android:jdk17
```

This sets `JAVA_HOME` to Android Studio’s bundled JBR (JDK 17) and runs the Android build.

### Running from Android Studio

When you open the project in Android Studio and click **Run**, Android Studio uses its own JDK (usually 17). You typically **do not** need the script in that case.

### Summary

| How You Run | Use Script? |
|-------------|-------------|
| `yarn android` in terminal | Only if your default JDK is not 17 |
| `yarn android:jdk17` in terminal | Always works (forces JDK 17) |
| Run from Android Studio | Usually not needed |

---

## 8. Apply Changes

After updating `package.json` and clearing caches, you may need to adjust native files. The [React Native Upgrade Helper](https://react-native-community.github.io/upgrade-helper/?from=0.76.0&to=0.83.0) shows the diffs.

### Android – Likely Unchanged

For 0.76 → 0.83, `MainApplication.kt`, `MainActivity.kt`, `settings.gradle`, and `build.gradle` often stay compatible. If the build fails, compare with the Upgrade Helper.

### Android – Gradle (if needed)

If you see Gradle or AGP errors, the RN 0.83 Gradle plugin may expect different versions. The Upgrade Helper will show the exact `gradle-wrapper.properties` and `build.gradle` for 0.83.

### iOS – Pods

```bash
cd ezkey_mobile/ios
bundle exec pod install
```

---

## 9. Verify the Build

### 9.1 – Start Metro

```bash
cd ezkey_mobile
yarn start
```

Leave it running in one terminal.

### 9.2 – Build and Run Android

In another terminal:

```bash
cd ezkey_mobile
yarn android:jdk17
```

Or, if your default JDK is 17:

```bash
yarn android
```

### 9.3 – Quick Checks

- App launches
- Home screen shows
- Navigation works
- No red error screen

### 9.4 – Run Tests

```bash
yarn test
yarn typecheck
yarn lint
```

---

## 10. Troubleshooting

### "Error resolving plugin [id: 'com.facebook.react.settings']"

- **Cause:** Wrong JDK (e.g. 25)
- **Fix:** Use `yarn android:jdk17` or set `JAVA_HOME` to JDK 17

### "Unsupported class file major version 69"

- **Cause:** JDK 25 bytecode
- **Fix:** Same as above – use JDK 17

### "Could not find com.facebook.react:react-android:0.83.0"

- **Cause:** Stale Gradle cache or wrong repositories
- **Fix:** `cd android && ./gradlew clean`, delete `android/.gradle`, run `yarn android:jdk17` again

### Metro "Unable to resolve module"

- **Fix:** `yarn start --reset-cache`, then rebuild

### iOS: "React-rncore pod not found"

- **Fix:** `cd ios && rm -rf Pods Podfile.lock && bundle exec pod install`

### Build Succeeds but App Crashes on Launch

- Check Metro logs for JavaScript errors
- Run `yarn typecheck` and fix type errors
- React 19 may require small code changes (e.g. `ref` callbacks)

---

## Checklist

- [ ] Node.js 20.19.4+
- [ ] Android Studio with SDK 35, Build-Tools 36
- [ ] JDK 17 (or `yarn android:jdk17` script)
- [ ] Git branch created
- [ ] `package.json` updated
- [ ] `yarn install` run
- [ ] Caches cleared (Metro, Gradle, build folders)
- [ ] `yarn android:jdk17` succeeds
- [ ] App launches and basic flows work
- [ ] `yarn test` and `yarn typecheck` pass
