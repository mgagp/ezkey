#!/bin/bash

# Ezkey Mobile V1 - Environment Setup Script
# This script sets up the necessary environment variables for building the Android app

echo "🔧 Setting up Ezkey Mobile V1 environment..."

# Set Java installation (user provided path)
echo "🔍 Setting up Java from user-provided path..."
JAVA_HOME="/c/Tools/jdk21"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# Verify Java is accessible
if [ ! -f "$JAVA_HOME/bin/java.exe" ]; then
    echo "❌ Java not found at $JAVA_HOME/bin/java.exe"
    echo "   Please verify the path: C:/Tools/jdk21"
    echo "   Or install OpenJDK 21 from: https://adoptium.net/temurin/releases/"
    exit 1
fi

echo "✅ Java found: $JAVA_HOME"

# Set Android SDK paths
ANDROID_SDK_ROOT="/c/Users/marcg/AppData/Local/Android/Sdk"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$PATH:$ANDROID_SDK_ROOT/platform-tools"
export PATH="$PATH:$ANDROID_SDK_ROOT/tools"
export PATH="$PATH:$ANDROID_SDK_ROOT/emulator"
export PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"

echo "✅ Android SDK: $ANDROID_SDK_ROOT"

# Verify environment
echo "🔍 Verifying environment..."
echo "Java version:"
java -version

echo "Android SDK:"
echo "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
echo "ANDROID_HOME: $ANDROID_HOME"

# Check if Android SDK exists
if [ ! -d "$ANDROID_SDK_ROOT" ]; then
    echo "❌ Android SDK not found at $ANDROID_SDK_ROOT"
    echo "   Please install Android Studio and SDK first."
    exit 1
fi

echo "✅ Environment setup complete!"
echo ""
echo "🚀 You can now run:"
echo "   ./gradlew clean"
echo "   ./gradlew assembleDebug"
