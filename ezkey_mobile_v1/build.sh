#!/bin/bash

# Ezkey Mobile V1 - Build Script (Bash)
# This script uses PowerShell safely to avoid popups

echo "🔧 Building Ezkey Mobile V1..."

# Use PowerShell script to build (avoids popups)
echo "📦 Running build with PowerShell..."
powershell -ExecutionPolicy Bypass -File build.ps1

echo "✅ Build process completed!"
echo ""
echo "🚀 Next steps:"
echo "1. Create AVD: bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd --name \"Pixel_7_Pro_API_34\" --package \"system-images;android-34;google_apis;x86_64\" --device \"pixel_7_pro\"'"
echo "2. Start emulator: bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/emulator/emulator -avd Pixel_7_Pro_API_34 &'"
echo "3. Install APK: bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk'"
echo "4. Launch app: bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb shell am start -n org.ezkey.mobile.v1/.MainActivity'"
echo "5. Monitor logs: bash -c '/c/Users/marcg/AppData/Local/Android/Sdk/platform-tools/adb logcat -s \"AuthService:*\" \"MainActivity:*\"'"
