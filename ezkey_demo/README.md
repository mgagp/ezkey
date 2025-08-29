# Ezkey Demo - Flutter Application

## Overview

Ezkey Demo is a single-screen Flutter application that demonstrates the fundamental Ezkey workflows:
- **Enrollment** (BIND → Generate Keys → VERIFY)
- **Authentication** (PENDING → Approve/Deny → RESPOND)

## Features

- ✅ **Dark Theme** with cyan accent color
- ✅ **Single Screen Interface** - No navigation, no menus
- ✅ **Mock Workflows** - Simulated enrollment and authentication
- ✅ **Responsive Design** - Works on mobile and web
- ✅ **Loading States** - Visual feedback during operations

## Flutter Installation Location

### Current Setup
- **Flutter SDK Location:** `C:\Tools\flutter`
- **Flutter Version:** 3.32.5 (stable)
- **Dart Version:** 3.8.1
- **Channel:** stable

### Command Usage
Since Flutter is not in the system PATH, use the full path for commands:

```powershell
# Flutter commands
C:\Tools\flutter\bin\flutter.bat --version
C:\Tools\flutter\bin\flutter.bat doctor
C:\Tools\flutter\bin\flutter.bat run -d chrome

# Dart commands
C:\Tools\flutter\bin\dart.bat --version
C:\Tools\flutter\bin\dart.bat pub get
```

## Development Commands

### Project Setup
```powershell
# Navigate to project
cd ezkey_demo

# Get dependencies
C:\Tools\flutter\bin\flutter.bat pub get

# Run in debug mode (web)
C:\Tools\flutter\bin\flutter.bat run -d chrome

# Run in debug mode (Android)
C:\Tools\flutter\bin\flutter.bat run -d android
```

### Building
```powershell
# Build for web
C:\Tools\flutter\bin\flutter.bat build web

# Build APK for Android
C:\Tools\flutter\bin\flutter.bat build apk

# Build APK bundle for Play Store
C:\Tools\flutter\bin\flutter.bat build appbundle
```

## 📱 Installation on Pixel 7 Pro

### Prerequisites

#### 1. Enable Developer Options
1. Go to **Settings** → **About phone**
2. Tap **Build number** 7 times
3. You'll see "You are now a developer!"

#### 2. Enable USB Debugging
1. Go to **Settings** → **Developer options**
2. Enable **USB debugging**
3. Enable **Install via USB** (if available)

#### 3. Install ADB (Android Debug Bridge)
If not already installed:
```powershell
# Download Android Platform Tools
# From: https://developer.android.com/studio/releases/platform-tools
# Extract to C:\Tools\platform-tools
# Add C:\Tools\platform-tools to PATH
```

### Installation Methods

#### Method 1: Direct USB Installation (Recommended)

1. **Connect your Pixel 7 Pro**
   ```powershell
   # Check if device is connected
   C:\Tools\platform-tools\adb.exe devices
   ```

2. **Build and install APK**
   ```powershell
   # Build APK
   C:\Tools\flutter\bin\flutter.bat build apk
   
   # Install APK
   C:\Tools\platform-tools\adb.exe install build\app\outputs\flutter-apk\app-debug.apk
   ```

3. **Launch the app**
   - Find "Ezkey Demo" in your app drawer
   - Or use: `adb shell am start -n com.example.ezkey_demo/.MainActivity`

#### Method 2: Wireless Installation (ADB over WiFi)

1. **Enable Wireless Debugging**
   - Go to **Settings** → **Developer options**
   - Enable **Wireless debugging**
   - Tap **Wireless debugging** → **Pair device with pairing code**

2. **Connect wirelessly**
   ```powershell
   # Connect to device IP (shown on phone)
   C:\Tools\platform-tools\adb.exe pair 192.168.1.100:12345
   C:\Tools\platform-tools\adb.exe connect 192.168.1.100:5555
   
   # Verify connection
   C:\Tools\platform-tools\adb.exe devices
   ```

3. **Install APK wirelessly**
   ```powershell
   C:\Tools\flutter\bin\flutter.bat build apk
   C:\Tools\platform-tools\adb.exe install build\app\outputs\flutter-apk\app-debug.apk
   ```

#### Method 3: Using Android Studio

1. **Open project in Android Studio**
   - Open Android Studio
   - Open the `ezkey_demo` folder
   - Let Android Studio sync the project

2. **Connect device**
   - Connect Pixel 7 Pro via USB
   - Enable USB debugging when prompted
   - Select your device in Android Studio

3. **Run the app**
   - Click the **Run** button (green play icon)
   - Or press **Shift + F10**

### Troubleshooting Installation

#### Common Issues

1. **"Device not found"**
   ```powershell
   # Check USB connection
   C:\Tools\platform-tools\adb.exe devices
   
   # Restart ADB server
   C:\Tools\platform-tools\adb.exe kill-server
   C:\Tools\platform-tools\adb.exe start-server
   ```

2. **"Installation failed"**
   - Uninstall previous version: `adb uninstall com.example.ezkey_demo`
   - Check available storage on device
   - Ensure "Install unknown apps" is enabled

3. **"App not appearing"**
   ```powershell
   # Check if app is installed
   C:\Tools\platform-tools\adb.exe shell pm list packages | findstr ezkey
   
   # Launch app manually
   C:\Tools\platform-tools\adb.exe shell am start -n com.example.ezkey_demo/.MainActivity
   ```

4. **"Permission denied"**
   - Accept USB debugging prompt on phone
   - Grant necessary permissions when app launches
   - Check if device is authorized in developer options

### Development Workflow

#### Hot Reload (During Development)
```powershell
# Run app in debug mode
C:\Tools\flutter\bin\flutter.bat run -d android

# While app is running:
# - Press 'r' for hot reload
# - Press 'R' for hot restart
# - Press 'q' to quit
```

#### Debugging
```powershell
# View logs
C:\Tools\platform-tools\adb.exe logcat | findstr flutter

# Take screenshot
C:\Tools\platform-tools\adb.exe shell screencap /sdcard/screenshot.png
C:\Tools\platform-tools\adb.exe pull /sdcard/screenshot.png
```

## 🎯 Using the App

### Demo Workflow

1. **Enrollment**
   - Enter any URL in the enrollment field
   - Click "Start Enrollment"
   - Watch the mock enrollment process

2. **Authentication**
   - After enrollment, click "Check for Pending Authentication"
   - You'll see a mock pending request
   - Click "Approve" or "Deny"
   - Enter a challenge response if prompted

### Features Demonstrated

- ✅ **Dark Theme** - Professional dark interface
- ✅ **Loading States** - Visual feedback during operations
- ✅ **Form Validation** - Input validation and error handling
- ✅ **State Management** - Dynamic UI updates
- ✅ **Responsive Design** - Works on different screen sizes

## 🔧 Development Notes

### Project Structure
```
ezkey_demo/
├── lib/
│   ├── main.dart              # App entry point
│   └── widgets/
│       └── demo_screen.dart   # Main demo interface
├── android/                   # Android-specific files
├── web/                      # Web-specific files
└── pubspec.yaml              # Dependencies
```

### Dependencies
- **http:** API calls (for future real implementation)
- **crypto:** Cryptographic operations
- **pointycastle:** RSA key generation and signing

### Next Steps for Real Implementation
1. Replace mock API calls with real Ezkey API endpoints
2. Implement actual RSA key generation and signing
3. Add proper error handling and validation
4. Implement secure storage for keys

## 📞 Support

### Getting Help
- Check the troubleshooting section above
- Verify Flutter installation: `C:\Tools\flutter\bin\flutter.bat doctor`
- Check device connection: `adb devices`

### Common Commands Reference
```powershell
# Flutter commands
C:\Tools\flutter\bin\flutter.bat doctor
C:\Tools\flutter\bin\flutter.bat clean
C:\Tools\flutter\bin\flutter.bat pub get
C:\Tools\flutter\bin\flutter.bat run -d android

# ADB commands
C:\Tools\platform-tools\adb.exe devices
C:\Tools\platform-tools\adb.exe install app.apk
C:\Tools\platform-tools\adb.exe uninstall com.example.ezkey_demo
```

---

**Last Updated:** January 2025  
**Flutter Version:** 3.32.5  
**Dart Version:** 3.8.1  
**Target Device:** Pixel 7 Pro (Android 14)
