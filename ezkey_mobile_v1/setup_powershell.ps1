# Ezkey Mobile V1 - Android Environment Setup Script for PowerShell
# This script sets up all necessary environment variables for Android development
#
# Usage: .\setup_powershell.ps1
# Note: Run this script in PowerShell to set environment variables

Write-Host "🔧 Setting up Android development environment for Ezkey Mobile V1..." -ForegroundColor Green

# Android SDK paths (adjust these paths according to your installation)
$env:ANDROID_SDK_ROOT = "C:\Users\marcg\AppData\Local\Android\Sdk"
$env:ANDROID_HOME = "C:\Users\marcg\AppData\Local\Android\Sdk"

# Add Android tools to PATH
$env:PATH = "$env:PATH;$env:ANDROID_HOME\platform-tools"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\tools"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\tools\bin"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\emulator"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\cmdline-tools\latest\bin"

# Java environment (if not already set)
# $env:JAVA_HOME = "C:\path\to\your\java"
# $env:PATH = "$env:PATH;$env:JAVA_HOME\bin"

# Verify environment setup
Write-Host "✅ Environment variables set:" -ForegroundColor Green
Write-Host "   ANDROID_SDK_ROOT: $env:ANDROID_SDK_ROOT" -ForegroundColor Yellow
Write-Host "   ANDROID_HOME: $env:ANDROID_HOME" -ForegroundColor Yellow
Write-Host "   PATH updated with Android tools" -ForegroundColor Yellow

# Verify tools availability
Write-Host ""
Write-Host "🔍 Verifying Android tools availability..." -ForegroundColor Cyan

try {
    $adbVersion = & "$env:ANDROID_HOME\platform-tools\adb.exe" version 2>$null | Select-Object -First 1
    if ($adbVersion) {
        Write-Host "   ✅ ADB found: $adbVersion" -ForegroundColor Green
    } else {
        Write-Host "   ❌ ADB not found" -ForegroundColor Red
    }
} catch {
    Write-Host "   ❌ ADB not found in PATH" -ForegroundColor Red
}

try {
    $emulatorVersion = & "$env:ANDROID_HOME\emulator\emulator.exe" -version 2>$null | Select-Object -First 1
    if ($emulatorVersion) {
        Write-Host "   ✅ Emulator found: $emulatorVersion" -ForegroundColor Green
    } else {
        Write-Host "   ❌ Emulator not found" -ForegroundColor Red
    }
} catch {
    Write-Host "   ❌ Emulator not found in PATH" -ForegroundColor Red
}

# Check if SDK directory exists
if (Test-Path $env:ANDROID_SDK_ROOT) {
    Write-Host "   ✅ Android SDK directory exists: $env:ANDROID_SDK_ROOT" -ForegroundColor Green
} else {
    Write-Host "   ❌ Android SDK directory not found: $env:ANDROID_SDK_ROOT" -ForegroundColor Red
    Write-Host "   Please update ANDROID_SDK_ROOT in this script" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🚀 Environment setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Create an AVD: Tools → AVD Manager in Android Studio" -ForegroundColor White
Write-Host "2. Start emulator: emulator -avd YOUR_AVD_NAME" -ForegroundColor White
Write-Host "3. Build project: .\gradlew assembleDebug" -ForegroundColor White
Write-Host "4. Install APK: adb install app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor White
Write-Host "5. Launch app: adb shell am start -n org.ezkey.mobile.v1/.MainActivity" -ForegroundColor White
Write-Host ""
Write-Host "Note: Run this script in PowerShell to set environment variables" -ForegroundColor Yellow
