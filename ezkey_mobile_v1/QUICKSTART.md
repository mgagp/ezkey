# Ezkey Mobile V1 - Quick Start Guide

## Overview

Simple command-line guide to build, deploy, and test the Ezkey Mobile V1 Android app with Pixel 7 Pro emulator using ngrok for API access.

## Prerequisites

- ✅ **Android Studio** installed
- ✅ **Java JDK 21+** installed (REQUIRED - see installation below)
- ✅ **ezkey-auth-api** running on localhost:8080
- ✅ **ngrok** running with URL: `https://goateed-katalina-monsoonal.ngrok-free.dev`

### Java Installation (REQUIRED)

If Java is not installed or not in PATH:

1. **Download OpenJDK 21:**
   - Go to: https://adoptium.net/temurin/releases/
   - Download "OpenJDK 21 (LTS)" for Windows x64
   - Install with default settings

2. **Set JAVA_HOME:**
   ```bash
   # Find Java installation
   bash -c "find /c/Program\ Files -name java.exe 2>/dev/null | head -1"
   
   # Set JAVA_HOME (replace with actual path)
   export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.1.12-hotspot"
   export PATH="$JAVA_HOME/bin:$PATH"
   ```

3. **Verify Java:**
   ```bash
   java -version
   javac -version
   ```

## Step-by-Step Instructions

### 1. Verify Environment

```bash
# Check Java version (REQUIRED - install if missing)
java -version

# If Java not found, install OpenJDK 21:
# 1. Go to: https://adoptium.net/temurin/releases/
# 2. Download "OpenJDK 21 (LTS)" for Windows x64
# 3. Install with default settings
# 4. Restart terminal and try again

# Check Android SDK
bash -c 'echo $ANDROID_SDK_ROOT'

# Test ngrok connectivity
curl https://goateed-katalina-monsoonal.ngrok-free.dev
```

### 2. Build the App

```bash
# Navigate to project
cd ezkey_mobile_v1

# Set Java environment (REQUIRED - adjust path if different)
export JAVA_HOME="C:/Tools/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"

# Verify Java is working
java -version

# Clean previous builds
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Verify APK was created
ls -la app/build/outputs/apk/debug/app-debug.apk
```

### 3. Create/Verify Pixel 7 Pro AVD

```bash
# List existing AVDs
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/emulator/emulator -list-avds'

# If Pixel_7_Pro_API_34 doesn't exist, create it
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd --name "Pixel_7_Pro_API_34" --package "system-images;android-34;google_apis;x86_64" --device "pixel_7_pro"'
```

### 4. Start Emulator

```bash
# Start emulator in background
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/emulator/emulator -avd Pixel_7_Pro_API_34 &'

# Wait for device to boot (this may take 2-3 minutes)
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb wait-for-device'

# Verify device is ready
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb devices'
```

### 5. Deploy App

```bash
# Install APK
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk'

# Launch app
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb shell am start -n org.ezkey.mobile.v1/.MainActivity'
```

### 6. Monitor Logs (ESSENTIAL)

```bash
# Monitor app logs in real-time
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb logcat -s "AuthService:*" "MainActivity:*" "System.err:*"'
```

## Testing Workflow

### 1. Crypto Validation (Automatic)
- App runs crypto tests automatically on startup
- Look for "✅ ALL CRYPTOGRAPHIC VALIDATION TESTS COMPLETED SUCCESSFULLY!" in logs

### 2. API Communication Test
- Click "🔍 Check Pending Auth" button in app
- Monitor logcat for detailed API request/response logs
- Should see HTTP 200 (pending found) or 204 (no pending)

### 3. Authentication Flow Test
- If pending auth found, click "✅ Accept Auth" button
- Monitor logs for accept request/response
- Should see "🎉 AUTHENTICATION ACCEPTED SUCCESSFULLY!" in app

## Troubleshooting

### Common Issues

**1. "Failed to connect" or "Unable to resolve host"**
```bash
# Test ngrok connectivity
curl https://goateed-katalina-monsoonal.ngrok-free.dev

# Test emulator internet
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb shell ping -c 3 8.8.8.8'
```

**2. "App crashes on startup"**
```bash
# Check for exceptions
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb logcat | grep -i exception'

# Rebuild app
bash -c "./gradlew clean assembleDebug"
```

**3. "Emulator won't start"**
```bash
# Check if AVD exists
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/emulator/emulator -list-avds'

# Create AVD if missing
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd --name "Pixel_7_Pro_API_34" --package "system-images;android-34;google_apis;x86_64" --device "pixel_7_pro"'
```

**4. "HTTP 401 or 403 from API"**
- Verify ezkey-auth-api is running on localhost:8080
- Check ngrok is forwarding correctly
- Verify enrollment ID 24 exists in backend

**5. "Build fails"**
```bash
# Clean and rebuild
bash -c "./gradlew clean"
bash -c "./gradlew assembleDebug"
```

## Command Reference

### Build Commands
```bash
cd ezkey_mobile_v1
bash -c "./gradlew clean"
bash -c "./gradlew assembleDebug"
```

### Emulator Commands
```bash
# List AVDs
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/emulator/emulator -list-avds'

# Start emulator
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/emulator/emulator -avd Pixel_7_Pro_API_34 &'

# Wait for device
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb wait-for-device'
```

### Deployment Commands
```bash
# Install APK
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk'

# Uninstall (if needed)
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb uninstall org.ezkey.mobile.v1'

# Launch app
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb shell am start -n org.ezkey.mobile.v1/.MainActivity'
```

### Logging Commands
```bash
# Monitor app logs
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb logcat -s "AuthService:*" "MainActivity:*"'

# Clear logs
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb logcat -c'

# Save logs to file
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb logcat > app-logs.txt'
```

### Debugging Commands
```bash
# Check app is installed
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb shell pm list packages | grep ezkey'

# Check internet connectivity
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb shell ping -c 3 google.com'

# View app info
bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb shell dumpsys package org.ezkey.mobile.v1'
```

## Success Indicators

✅ **Setup Complete:**
- APK builds without errors
- Emulator starts and boots successfully
- App installs and launches

✅ **Crypto Validation:**
- All 5 crypto tests pass on startup
- Logs show "✅ ALL CRYPTOGRAPHIC VALIDATION TESTS COMPLETED SUCCESSFULLY!"

✅ **API Communication:**
- App connects to ngrok URL successfully
- HTTP requests/responses logged with full details
- Pending check returns HTTP 200 or 204

✅ **Authentication Flow:**
- Accept authentication completes successfully
- Backend receives and processes the authentication

## Notes

- All commands use `bash -c` for Windows compatibility
- ngrok URL is hard-coded for POC (can be made configurable later)
- Conservative API levels (34/33/24) ensure build reliability
- Extensive logging is critical for mobile debugging
- This is native Android/Kotlin, NOT React Native

