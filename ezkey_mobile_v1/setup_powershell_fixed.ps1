# Ezkey Mobile V1 - Android Environment Setup Script for PowerShell
# This script sets up all necessary environment variables for Android development

Write-Host "Setting up Android development environment for Ezkey Mobile V1..." -ForegroundColor Green

# Android SDK paths (adjust these paths according to your installation)
$env:ANDROID_SDK_ROOT = "C:\Users\marcg\AppData\Local\Android\Sdk"
$env:ANDROID_HOME = "C:\Users\marcg\AppData\Local\Android\Sdk"

# Add Android tools to PATH
$env:PATH = "$env:PATH;$env:ANDROID_HOME\platform-tools"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\tools"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\tools\bin"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\emulator"
$env:PATH = "$env:PATH;$env:ANDROID_HOME\cmdline-tools\latest\bin"

# Verify environment setup
Write-Host "Environment variables set:" -ForegroundColor Green
Write-Host "   ANDROID_SDK_ROOT: $env:ANDROID_SDK_ROOT" -ForegroundColor Yellow
Write-Host "   ANDROID_HOME: $env:ANDROID_HOME" -ForegroundColor Yellow
Write-Host "   PATH updated with Android tools" -ForegroundColor Yellow

# Check if SDK directory exists
if (Test-Path $env:ANDROID_SDK_ROOT) {
    Write-Host "   Android SDK directory exists: $env:ANDROID_SDK_ROOT" -ForegroundColor Green
} else {
    Write-Host "   Android SDK directory not found: $env:ANDROID_SDK_ROOT" -ForegroundColor Red
    Write-Host "   Please update ANDROID_SDK_ROOT in this script" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Environment setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Build project: .\gradlew assembleDebug" -ForegroundColor White
Write-Host "2. Create AVD in Android Studio if needed" -ForegroundColor White
Write-Host "3. Install APK: adb install app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor White
