# Ezkey Mobile V2 - Deployment Guide

This guide provides detailed instructions for building and deploying the Ezkey Mobile V2 application.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Development Setup](#development-setup)
3. [Building for Android Studio](#building-for-android-studio)
4. [Running on Android Emulator](#running-on-android-emulator)
5. [Deploying with Expo](#deploying-with-expo)
6. [Production Deployment](#production-deployment)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

1. **Node.js and npm**
   - Version: Node.js 18+ 
   - Download: https://nodejs.org/
   - Verify: `node --version && npm --version`

2. **Java Development Kit (JDK)**
   - Version: JDK 17 or higher
   - Download: https://adoptium.net/
   - Verify: `java -version`

3. **Android Studio**
   - Latest stable version
   - Download: https://developer.android.com/studio
   - Required components:
     - Android SDK
     - Android SDK Platform (API 33)
     - Android SDK Build-Tools
     - Android Emulator
     - Android SDK Platform-Tools

4. **Android SDK Environment Variables**
   ```bash
   # Add to ~/.bashrc or ~/.zshrc
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$PATH:$ANDROID_HOME/emulator
   export PATH=$PATH:$ANDROID_HOME/platform-tools
   export PATH=$PATH:$ANDROID_HOME/tools
   export PATH=$PATH:$ANDROID_HOME/tools/bin
   ```

### Verify Android Setup

```bash
# Check Android SDK
sdkmanager --list

# Check ADB
adb version

# Check connected devices
adb devices
```

---

## Development Setup

### 1. Install Project Dependencies

```bash
cd ezkey_mobile_v2
npm install
```

This will install:
- Expo SDK
- React Native
- AsyncStorage for local storage
- Axios for API calls
- React Native Paper for UI components
- Expo Crypto for cryptographic operations
- Custom native crypto module

### 2. Configure Backend URL

Edit `src/services/AuthApiService.ts`:

```typescript
// For Android Emulator (localhost on host machine)
const AUTH_API_BASE_URL = 'http://10.0.2.2:8080/api/v1';

// For physical device (replace with your machine's IP)
// const AUTH_API_BASE_URL = 'http://192.168.1.100:8080/api/v1';
```

**Important**: 
- `10.0.2.2` maps to `localhost` on the host machine for Android emulators
- For physical devices, use your computer's local network IP address

### 3. Start Backend Services

Ensure your Ezkey backend is running:

```bash
# Terminal 1 - Start Auth API (port 8080)
cd ezkey-auth-api
mvn spring-boot:run

# Terminal 2 - Start Admin API (port 9080)
cd ezkey-admin-api
mvn spring-boot:run
```

---

## Building for Android Studio

### Method 1: Using Build Script (Recommended)

```bash
# Make script executable (first time only)
chmod +x build-android.sh

# Run build
./build-android.sh
```

The script will:
1. Install dependencies
2. Generate Android project with `expo prebuild`
3. Build debug APK with Gradle
4. Output APK location

**Output**: `android/app/build/outputs/apk/debug/app-debug.apk`

### Method 2: Manual Build Steps

1. **Generate Android Project**:
```bash
npx expo prebuild --platform android --clean
```

This creates the `android/` directory with native Android project files.

2. **Open in Android Studio**:
```bash
# Linux/Mac
studio android/

# Or open manually: File > Open > Select 'android' folder
```

3. **Sync Gradle**:
   - Android Studio will prompt to sync Gradle files
   - Click "Sync Now" and wait for completion
   - This downloads dependencies and configures the project

4. **Build APK**:
   - **Menu**: `Build > Build Bundle(s) / APK(s) > Build APK(s)`
   - Or use shortcut: `Ctrl+Shift+F9` (Linux/Windows) or `Cmd+Shift+F9` (Mac)
   - Wait for build to complete
   - Click "locate" in the notification to find the APK

5. **Build Output**:
   - Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
   - Size: ~40-50 MB

### Method 3: Gradle Command Line

```bash
# From project root
npx expo prebuild --platform android

# Build debug APK
cd android
./gradlew assembleDebug

# Build release APK (requires signing)
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug
```

---

## Running on Android Emulator

### Setup Android Emulator (First Time)

1. **Open AVD Manager** in Android Studio:
   - `Tools > Device Manager`

2. **Create Virtual Device**:
   - Click "Create Device"
   - Select device: Pixel 5 or Pixel 6
   - Select system image: Android 13 (API 33) or higher
   - Click "Finish"

3. **Start Emulator**:
   - Click the play button next to your device in Device Manager
   - Or from command line:
   ```bash
   # List available AVDs
   emulator -list-avds
   
   # Start specific AVD
   emulator -avd Pixel_5_API_33
   ```

### Running the App

#### Option A: Using Expo CLI (Development)

```bash
# Start Expo dev server
npm start

# In the terminal, press 'a' to open on Android
# Or scan QR code with Expo Go app (limited native module support)
```

#### Option B: Using React Native CLI

```bash
# Start Metro bundler
npm start

# In another terminal
npm run android
```

This will:
1. Start Metro bundler
2. Install app on emulator
3. Launch the app
4. Enable hot reload for development

#### Option C: Install APK Manually

```bash
# Install debug APK
adb install android/app/build/outputs/apk/debug/app-debug.apk

# Or if app is already installed (update)
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

### Emulator Tips

- **Enable developer mode**: Settings > About > Tap "Build number" 7 times
- **Enable USB debugging**: Settings > Developer Options > USB Debugging
- **Reload app**: Press `R` twice in the app or shake the device (Cmd+M/Ctrl+M)
- **Developer menu**: Cmd+M (Mac) or Ctrl+M (Windows/Linux)
- **View logs**: `adb logcat | grep ReactNative`

---

## Deploying with Expo

### Expo Development Build

For full native module support:

1. **Install EAS CLI**:
```bash
npm install -g eas-cli
```

2. **Login to Expo**:
```bash
eas login
```

3. **Configure EAS**:
```bash
eas build:configure
```

This creates `eas.json`:
```json
{
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal",
      "android": {
        "gradleCommand": ":app:assembleDebug",
        "buildType": "apk"
      }
    },
    "preview": {
      "android": {
        "buildType": "apk"
      }
    },
    "production": {
      "android": {
        "buildType": "apk"
      }
    }
  }
}
```

4. **Build Development Client**:
```bash
# Build for internal testing
eas build --profile development --platform android

# Build preview (like production but not signed for store)
eas build --profile preview --platform android
```

5. **Install Build**:
   - EAS will provide a download URL
   - Download APK on your Android device
   - Install the APK
   - Run the app

### Expo Go (Limited Support)

**Note**: Native modules (crypto) won't work in Expo Go.

```bash
npm start
# Scan QR with Expo Go app
```

---

## Production Deployment

### 1. Generate Signing Key

```bash
# Generate keystore
keytool -genkeypair -v -storetype PKCS12 -keystore ezkey-release-key.keystore -alias ezkey-key-alias -keyalg RSA -keysize 2048 -validity 10000

# Enter keystore password
# Enter key password
# Fill in certificate details
```

**Important**: Keep your keystore file and passwords secure!

### 2. Configure Gradle for Signing

Edit `android/app/build.gradle`:

```gradle
android {
    ...
    signingConfigs {
        release {
            storeFile file('ezkey-release-key.keystore')
            storePassword 'YOUR_KEYSTORE_PASSWORD'
            keyAlias 'ezkey-key-alias'
            keyPassword 'YOUR_KEY_PASSWORD'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

### 3. Build Release APK

```bash
cd android
./gradlew assembleRelease

# Output: android/app/build/outputs/apk/release/app-release.apk
```

### 4. Build AAB for Google Play

```bash
cd android
./gradlew bundleRelease

# Output: android/app/build/outputs/bundle/release/app-release.aab
```

### 5. Using EAS for Production

```bash
# Configure signing
eas credentials

# Build for production
eas build --platform android --profile production

# Submit to Google Play
eas submit --platform android
```

---

## Troubleshooting

### Cannot Connect to Backend

**Problem**: App shows connection error.

**Solutions**:
1. Check backend is running: `curl http://localhost:8080/actuator/health`
2. For emulator, use `10.0.2.2` instead of `localhost`
3. For device, use your computer's local IP
4. Check firewall settings allow connections on port 8080
5. Ensure `usesCleartextTraffic` is true in AndroidManifest.xml

### Native Module Not Found

**Problem**: `ExpoCryptoNative` module not found.

**Solutions**:
1. Regenerate Android project:
   ```bash
   npx expo prebuild --clean
   ```
2. Clean and rebuild:
   ```bash
   cd android
   ./gradlew clean
   ./gradlew assembleDebug
   ```
3. Check module is in `android/app/src/main/java/expo/modules/cryptonative/`

### Build Errors

**Problem**: Gradle build fails.

**Solutions**:
1. Clean build:
   ```bash
   cd android
   ./gradlew clean
   ```
2. Update Gradle wrapper:
   ```bash
   ./gradlew wrapper --gradle-version=8.0
   ```
3. Check Java version: `java -version` (should be 17+)
4. Clear Gradle cache:
   ```bash
   rm -rf ~/.gradle/caches
   ```

### Metro Bundler Issues

**Problem**: Metro won't start or has cache issues.

**Solutions**:
```bash
# Clear Metro cache
npx expo start -c

# Or
npx react-native start --reset-cache

# Clear watchman (if installed)
watchman watch-del-all
```

### APK Installation Failed

**Problem**: `adb install` fails.

**Solutions**:
1. Check device is connected: `adb devices`
2. Uninstall old version first:
   ```bash
   adb uninstall org.ezkey.mobile.v2
   adb install android/app/build/outputs/apk/debug/app-debug.apk
   ```
3. Check device has enough storage
4. Enable "Install from Unknown Sources" on device

### AsyncStorage Warnings

**Problem**: Warnings about AsyncStorage.

**Solution**: AsyncStorage is properly installed. Warnings are informational. For production, consider:
- Expo SecureStore for sensitive data
- React Native MMKV for performance

---

## Quick Reference

### Common Commands

```bash
# Development
npm start                  # Start Expo dev server
npm run android           # Run on Android emulator
npx expo prebuild         # Generate native projects

# Building
./build-android.sh        # Build with script
./gradlew assembleDebug   # Build debug APK (from android/)
./gradlew assembleRelease # Build release APK

# Installation
adb install path/to/app.apk           # Install APK
adb install -r path/to/app.apk        # Reinstall APK
adb uninstall org.ezkey.mobile.v2     # Uninstall app

# Debugging
adb logcat                            # View all logs
adb logcat | grep ReactNative         # Filter React Native logs
adb shell                             # Shell into device
```

### File Locations

- **Debug APK**: `android/app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `android/app/build/outputs/apk/release/app-release.apk`
- **AAB Bundle**: `android/app/build/outputs/bundle/release/app-release.aab`
- **Native Module**: `modules/expo-crypto-native/android/`
- **Android Project**: `android/` (generated by expo prebuild)

---

## Next Steps

1. ✅ Complete development setup
2. ✅ Build and test on emulator
3. ✅ Test on physical device
4. ⬜ Generate production signing key
5. ⬜ Build release APK
6. ⬜ Submit to Google Play Store (optional)

---

For more information, see:
- [README.md](README.md) - Project overview and usage
- [Expo Documentation](https://docs.expo.dev/)
- [React Native Documentation](https://reactnative.dev/)
- [Android Developer Guide](https://developer.android.com/)
