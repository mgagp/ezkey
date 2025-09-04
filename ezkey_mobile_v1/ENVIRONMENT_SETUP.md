# Environment Setup for Ezkey Mobile V1

This document explains how to set up your development environment for building and running the Ezkey Mobile V1 Android application.

## Prerequisites

- **Java Development Kit (JDK)** 21 or higher
- **Android Studio** with Android SDK installed
- **Android SDK** with API level 34 (Android 14)
- **Android Build Tools** 34.0.0 or higher

## Environment Setup Scripts

### For Bash (Linux/macOS/Git Bash on Windows)

1. **Make the script executable:**
   ```bash
   chmod +x setup_bash.sh
   ```

2. **Source the script in your current shell:**
   ```bash
   source setup_bash.sh
   ```

3. **Verify the setup:**
   ```bash
   echo $ANDROID_SDK_ROOT
   echo $ANDROID_HOME
   adb version
   ```

### For PowerShell (Windows)

1. **Run the PowerShell script:**
   ```powershell
   .\setup_powershell.ps1
   ```

2. **Verify the setup:**
   ```powershell
   $env:ANDROID_SDK_ROOT
   $env:ANDROID_HOME
   adb version
   ```

## Manual Environment Setup

If you prefer to set environment variables manually:

### Bash
```bash
export ANDROID_SDK_ROOT="/c/Users/marcg/AppData/Local/Android/Sdk"
export ANDROID_HOME="/c/Users/marcg/AppData/Local/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
export PATH="$PATH:$ANDROID_HOME/tools"
export PATH="$PATH:$ANDROID_HOME/tools/bin"
export PATH="$PATH:$ANDROID_HOME/emulator"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
```

### PowerShell
```powershell
$env:ANDROID_SDK_ROOT = "C:\Users\marcg\AppData\Local\Android\Sdk"
$env:ANDROID_HOME = "C:\Users\marcg\AppData\Local\Android\Sdk"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\platform-tools"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\tools"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\tools\bin"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\emulator"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\cmdline-tools\latest\bin"
```

## What These Scripts Do

The setup scripts:

1. **Set Android SDK paths:**
   - `ANDROID_SDK_ROOT`: Main Android SDK directory
   - `ANDROID_HOME`: Alternative name for Android SDK (some tools use this)

2. **Add Android tools to PATH:**
   - `platform-tools`: Contains `adb`, `fastboot`, etc.
   - `tools`: Contains older SDK tools
   - `emulator`: Contains Android emulator
   - `cmdline-tools`: Contains `sdkmanager`, `avdmanager`

3. **Verify the setup:**
   - Check if tools are available
   - Verify SDK directory exists
   - Display version information

## Troubleshooting

### Common Issues

1. **"adb not found"**
   - Ensure `ANDROID_SDK_ROOT` points to the correct SDK directory
   - Check if `platform-tools` directory exists in your SDK

2. **"emulator not found"**
   - Ensure `emulator` directory exists in your SDK
   - Install Android Emulator via Android Studio SDK Manager

3. **"Permission denied"**
   - Make sure the script is executable: `chmod +x setup_bash.sh`
   - Use `source` instead of `./` for bash scripts

4. **Path not updated in current shell**
   - Use `source setup_bash.sh` (bash) or run the PowerShell script
   - Don't use `./setup_bash.sh` as it runs in a subshell

### Verify Your Android SDK Installation

1. **Open Android Studio**
2. **Go to File → Settings → Appearance & Behavior → System Settings → Android SDK**
3. **Note the "Android SDK Location" path**
4. **Update the scripts with your actual SDK path if different**

## Next Steps After Setup

Once your environment is configured:

1. **Build the project:**
   ```bash
   ./gradlew assembleDebug
   ```

2. **Create an AVD (Android Virtual Device):**
   - Open Android Studio
   - Go to Tools → AVD Manager
   - Create Virtual Device → Pixel 7 Pro → API 34

3. **Start the emulator:**
   ```bash
   emulator -avd YOUR_AVD_NAME
   ```

4. **Install and run the app:**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n org.ezkey.mobile.v1/.MainActivity
   ```

## Support

If you encounter issues:

1. Check that all paths in the scripts match your actual installation
2. Ensure Android Studio has downloaded all required SDK components
3. Verify that your Java installation is compatible (JDK 21+)
4. Check the project's main README.md for additional troubleshooting steps
