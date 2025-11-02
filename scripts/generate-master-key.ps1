# Ezkey Master Key Generator (PowerShell) - Development Only
# 
# Generates a cryptographically secure master key for encrypting Tink keysets.
# NOTE: Windows is for development only. Production deployments use Linux.
#
# Usage: .\scripts\generate-master-key.ps1

param(
    [string]$OutputDir = "$env:ProgramData\ezkey\secrets",
    [string]$KeysetsDir = "$env:ProgramData\ezkey\keysets"
)

Write-Host "Ezkey Master Key Generator (Dev)" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan
Write-Host ""

# Generate 32 bytes (256 bits) of cryptographically secure random data
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RNGCryptoServiceProvider]::Create().GetBytes($bytes)
$MASTER_KEY = [Convert]::ToBase64String($bytes)

# Create directories if needed
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
$KeysetsDir = [System.IO.Path]::GetFullPath($KeysetsDir)

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}
if (-not (Test-Path $KeysetsDir)) {
    New-Item -ItemType Directory -Force -Path $KeysetsDir | Out-Null
}

$MASTER_KEY_FILE = Join-Path $OutputDir "master.key"

# Write master key to file
$MASTER_KEY | Out-File -FilePath $MASTER_KEY_FILE -Encoding ASCII -NoNewline

# Verify file was created
if (-not (Test-Path $MASTER_KEY_FILE)) {
    Write-Host "ERROR: Failed to create master key file: $MASTER_KEY_FILE" -ForegroundColor Red
    exit 1
}

# Set basic permissions (dev only - production uses Linux)
try {
    $acl = Get-Acl $MASTER_KEY_FILE
    $acl.SetAccessRuleProtection($true, $false)
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $accessRule = New-Object System.Security.AccessControl.FileSystemAccessRule($currentUser, "FullControl", "Allow")
    $acl.SetAccessRule($accessRule)
    Set-Acl $MASTER_KEY_FILE $acl
} catch {
    Write-Host "WARNING: Could not set permissions: $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "SUCCESS: Master key generated: $MASTER_KEY_FILE" -ForegroundColor Green
Write-Host ""
Write-Host "Configuration for application.properties:" -ForegroundColor Cyan
$keysetFile = Join-Path $KeysetsDir "keyset.json.encrypted"
# Use forward slashes for compatibility with Git Bash paths in config
$configMasterKey = $MASTER_KEY_FILE.Replace('\', '/')
$configKeyset = $keysetFile.Replace('\', '/')
Write-Host "  ezkey.encryption.master-key-file=$configMasterKey" -ForegroundColor Yellow
Write-Host "  ezkey.encryption.keyset-file=$configKeyset" -ForegroundColor Yellow

