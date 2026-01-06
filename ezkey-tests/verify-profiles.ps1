# Spring Profiles Configuration Verification Script (PowerShell)
# 
# This script verifies that both Docker and Windows profiles are correctly configured
# for the Ezkey encryption key paths.
#
# Usage:
#   .\verify-profiles.ps1
#   .\verify-profiles.ps1 -Profile docker
#   .\verify-profiles.ps1 -Profile windows

param(
    [ValidateSet("docker", "windows", "all")]
    [string]$Profile = "all"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

$Green = "`e[32m"
$Red = "`e[31m"
$Yellow = "`e[33m"
$Reset = "`e[0m"

$Errors = 0

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Spring Profiles Verification Script" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

function Check-FileContains {
    param(
        [string]$FilePath,
        [string]$Pattern,
        [string]$Description
    )
    
    if (-not (Test-Path $FilePath)) {
        Write-Host "`e[31m✗ MISSING`e[0m: $FilePath"
        $global:Errors++
        return $false
    }
    
    $Content = Get-Content $FilePath -Raw
    if ($Content -match $Pattern) {
        Write-Host "`e[32m✓`e[0m $Description"
        return $true
    } else {
        Write-Host "`e[31m✗`e[0m $Description (pattern not found: $Pattern)"
        $global:Errors++
        return $false
    }
}

function Check-FileNotContains {
    param(
        [string]$FilePath,
        [string]$Pattern,
        [string]$Description
    )
    
    if (-not (Test-Path $FilePath)) {
        Write-Host "`e[31m✗ MISSING`e[0m: $FilePath"
        $global:Errors++
        return $false
    }
    
    $Content = Get-Content $FilePath -Raw
    if ($Content -notmatch $Pattern) {
        Write-Host "`e[32m✓`e[0m $Description"
        return $true
    } else {
        Write-Host "`e[31m✗`e[0m $Description (pattern found: $Pattern)"
        $global:Errors++
        return $false
    }
}

# ============================================
# ADMIN API CHECKS
# ============================================
Write-Host ""
Write-Host "📁 Admin API Configuration" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$AdminApp = Join-Path $ProjectRoot "ezkey-admin-api\config\application.properties"
$AdminDocker = Join-Path $ProjectRoot "ezkey-admin-api\config\application-docker.properties"
$AdminWindows = Join-Path $ProjectRoot "ezkey-admin-api\config\application-windows.properties"

if ($Profile -eq "all" -or $Profile -eq "docker") {
    Write-Host ""
    Write-Host "🐳 Docker Profile:"
    Check-FileContains $AdminDocker "ezkey.encryption.master-key-file=/etc/ezkey/secrets/master.key" `
        "Master key path: /etc/ezkey/secrets/master.key"
    Check-FileContains $AdminDocker "ezkey.encryption.keyset.storage-mode=DATABASE" `
        "Keyset storage: DATABASE (distributed)"
    Check-FileContains $AdminDocker "ezkey.admin.initial.username=admin.docker" `
        "Initial admin username: admin.docker"
}

if ($Profile -eq "all" -or $Profile -eq "windows") {
    Write-Host ""
    Write-Host "🪟 Windows Profile:"
    Check-FileContains $AdminWindows "ezkey.encryption.master-key-file=C:\\ProgramData\\ezkey\\secrets\\master.key" `
        "Master key path: C:\ProgramData\ezkey\secrets\master.key"
    Check-FileContains $AdminWindows "ezkey.encryption.keyset.storage-mode=FILE" `
        "Keyset storage: FILE (single-instance)"
    Check-FileContains $AdminWindows "ezkey.admin.initial.username=admin.windows" `
        "Initial admin username: admin.windows"
}

if ($Profile -eq "all") {
    Write-Host ""
    Write-Host "✋ Default Configuration (should NOT contain hardcoded paths):"
    Check-FileNotContains $AdminApp "ezkey.encryption.master-key-file=/c/ProgramData" `
        "❌ Hardcoded /c/ProgramData path removed from default config"
    Check-FileContains $AdminApp "MUST be configured via profiles" `
        "Documentation added about profile-specific paths"
}

# ============================================
# AUTH API CHECKS
# ============================================
Write-Host ""
Write-Host "📁 Auth API Configuration" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$AuthApp = Join-Path $ProjectRoot "ezkey-auth-api\config\application.properties"
$AuthDocker = Join-Path $ProjectRoot "ezkey-auth-api\config\application-docker.properties"
$AuthWindows = Join-Path $ProjectRoot "ezkey-auth-api\config\application-windows.properties"

if ($Profile -eq "all" -or $Profile -eq "docker") {
    Write-Host ""
    Write-Host "🐳 Docker Profile:"
    Check-FileContains $AuthDocker "ezkey.encryption.master-key-file=/etc/ezkey/secrets/master.key" `
        "Master key path: /etc/ezkey/secrets/master.key"
    Check-FileContains $AuthDocker "ezkey.encryption.keyset.storage-mode=DATABASE" `
        "Keyset storage: DATABASE (synchronized)"
}

if ($Profile -eq "all" -or $Profile -eq "windows") {
    Write-Host ""
    Write-Host "🪟 Windows Profile:"
    Check-FileContains $AuthWindows "ezkey.encryption.master-key-file=C:\\ProgramData\\ezkey\\secrets\\master.key" `
        "Master key path: C:\ProgramData\ezkey\secrets\master.key"
    Check-FileContains $AuthWindows "ezkey.encryption.keyset.storage-mode=FILE" `
        "Keyset storage: FILE (single-instance)"
}

if ($Profile -eq "all") {
    Write-Host ""
    Write-Host "✋ Default Configuration (should NOT contain hardcoded paths):"
    Check-FileNotContains $AuthApp "ezkey.encryption.master-key-file=/c/ProgramData" `
        "❌ Hardcoded /c/ProgramData path removed from default config"
    Check-FileContains $AuthApp "MUST be configured via profiles" `
        "Documentation added about profile-specific paths"
}

# ============================================
# DOCUMENTATION CHECKS
# ============================================
if ($Profile -eq "all") {
    Write-Host ""
    Write-Host "📚 Documentation Files" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    
    Write-Host ""
    Write-Host "✓ Checking for migration and configuration guides:"
    
    $SpringProfilesDoc = Join-Path $ProjectRoot "docs\SPRING_PROFILES_CONFIGURATION.md"
    $MigrationDoc = Join-Path $ProjectRoot "docs\MIGRATION_HARDCODED_PATHS_FIX.md"
    $FixSummary = Join-Path $ProjectRoot "FIX_SUMMARY_SPRING_PROFILES.md"
    
    if (Test-Path $SpringProfilesDoc) {
        Write-Host "`e[32m✓`e[0m SPRING_PROFILES_CONFIGURATION.md exists"
    } else {
        Write-Host "`e[31m✗`e[0m SPRING_PROFILES_CONFIGURATION.md missing"
        $global:Errors++
    }
    
    if (Test-Path $MigrationDoc) {
        Write-Host "`e[32m✓`e[0m MIGRATION_HARDCODED_PATHS_FIX.md exists"
    } else {
        Write-Host "`e[31m✗`e[0m MIGRATION_HARDCODED_PATHS_FIX.md missing"
        $global:Errors++
    }
    
    if (Test-Path $FixSummary) {
        Write-Host "`e[32m✓`e[0m FIX_SUMMARY_SPRING_PROFILES.md exists"
    } else {
        Write-Host "`e[31m✗`e[0m FIX_SUMMARY_SPRING_PROFILES.md missing"
        $global:Errors++
    }
}

# ============================================
# KEY GENERATION SCRIPTS CHECK
# ============================================
if ($Profile -eq "all") {
    Write-Host ""
    Write-Host "🔑 Master Key Generation Scripts" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    
    Write-Host ""
    Write-Host "✓ Checking for master key generation scripts:"
    
    $LinuxKeygen = Join-Path $ProjectRoot "scripts\generate-master-key.sh"
    $WindowsKeygen = Join-Path $ProjectRoot "scripts\generate-master-key.ps1"
    $DockerKeygen = Join-Path $ProjectRoot "docker\generate-encryption-keys.sh"
    
    if (Test-Path $LinuxKeygen) {
        Write-Host "`e[32m✓`e[0m scripts/generate-master-key.sh exists"
        Check-FileContains $LinuxKeygen "/etc/ezkey/secrets" `
            "  - Uses /etc/ezkey/secrets path (Linux)"
    } else {
        Write-Host "`e[31m✗`e[0m scripts/generate-master-key.sh missing"
        $global:Errors++
    }
    
    if (Test-Path $WindowsKeygen) {
        Write-Host "`e[32m✓`e[0m scripts/generate-master-key.ps1 exists"
    } else {
        Write-Host "`e[31m✗`e[0m scripts/generate-master-key.ps1 missing"
        $global:Errors++
    }
    
    if (Test-Path $DockerKeygen) {
        Write-Host "`e[32m✓`e[0m docker/generate-encryption-keys.sh exists"
        Check-FileContains $DockerKeygen "/etc/ezkey" `
            "  - Uses Docker volume paths"
    } else {
        Write-Host "`e[31m✗`e[0m docker/generate-encryption-keys.sh missing"
        $global:Errors++
    }
}

# ============================================
# SUMMARY
# ============================================
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Verification Summary" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

if ($Errors -eq 0) {
    Write-Host "`e[32m✅ All checks passed!`e[0m" -ForegroundColor Green
    Write-Host ""
    Write-Host "Configuration is ready:"
    Write-Host "  🐳 Docker: ./docker/start.sh (uses 'docker' profile)"
    Write-Host "  🪟 Windows: Set `$env:SPRING_PROFILES_ACTIVE = 'windows' before running"
    Write-Host "  📚 Documentation: See docs/SPRING_PROFILES_CONFIGURATION.md"
    Write-Host ""
    exit 0
} else {
    Write-Host "`e[31m❌ Found $Errors error(s)`e[0m" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please review the configuration and fix the issues above."
    Write-Host ""
    exit 1
}
