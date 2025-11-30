#!/bin/bash

# Ezkey Mobile V1 - Deployment Script (BASH ONLY)
# This script handles deployment and testing, NOT building

echo "🚀 Ezkey Mobile V1 - Deployment Script"
echo "======================================"
echo ""

# Check if APK exists
if [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "❌ APK not found!"
    echo "   Please build the APK first using Android Studio:"
    echo "   1. Open ezkey_mobile_v1 in Android Studio"
    echo "   2. Build → Build APK(s)"
    echo "   3. Run this script again"
    exit 1
fi

echo "✅ APK found: app/build/outputs/apk/debug/app-debug.apk"
echo ""

# Set Android SDK paths
export ANDROID_SDK_ROOT="/c/Users/marcg/AppData/Local/Android/Sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$PATH:$ANDROID_SDK_ROOT/platform-tools"
export PATH="$PATH:$ANDROID_SDK_ROOT/emulator"
export PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"

echo "🔍 Checking Android SDK..."
if [ ! -d "$ANDROID_SDK_ROOT" ]; then
    echo "❌ Android SDK not found at $ANDROID_SDK_ROOT"
    echo "   Please install Android Studio and SDK first"
    exit 1
fi

echo "✅ Android SDK found: $ANDROID_SDK_ROOT"
echo ""

# List existing AVDs
echo "📱 Checking existing AVDs..."
bash -c "$ANDROID_SDK_ROOT/emulator/emulator -list-avds"

echo ""
echo "🔧 Creating Pixel 7 Pro AVD (if not exists)..."
bash -c "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager create avd --name \"Pixel_7_Pro_API_34\" --package \"system-images;android-34;google_apis;x86_64\" --device \"pixel_7_pro\""

echo ""
echo "🚀 Starting emulator..."
bash -c "$ANDROID_SDK_ROOT/emulator/emulator -avd Pixel_7_Pro_API_34 &"

echo "⏳ Waiting for device to boot (this may take 2-3 minutes)..."
bash -c "$ANDROID_SDK_ROOT/platform-tools/adb wait-for-device"

echo "✅ Device ready!"
echo ""

echo "📦 Installing APK..."
bash -c "$ANDROID_SDK_ROOT/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk"

echo ""
echo "🎯 Launching app..."
bash -c "$ANDROID_SDK_ROOT/platform-tools/adb shell am start -n org.ezkey.mobile.v1/.MainActivity"

echo ""
echo "📊 Monitoring logs (press Ctrl+C to stop)..."
echo "   Look for 'AuthService:*' and 'MainActivity:*' logs"
echo ""

# Monitor logs
bash -c "$ANDROID_SDK_ROOT/platform-tools/adb logcat -s \"AuthService:*\" \"MainActivity:*\" \"System.err:*\""
