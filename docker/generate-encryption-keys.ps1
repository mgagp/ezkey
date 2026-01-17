# Ezkey Encryption Keys Generator for Docker (PowerShell)
# Generates master key file in Docker volume for Tink encryption.
# Usage: .\generate-encryption-keys.ps1 [-Native] [-Ha]

param(
    [switch]$Native,
    [switch]$Ha
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Native -and $Ha) {
    Write-Host "Error: -Native and -Ha options are incompatible" -ForegroundColor Red
    exit 1
}

$VolumeName = "ezkey_encryption-secrets"
if ($Native) {
    $VolumeName = "ezkey-native_encryption-secrets-native"
}
if ($Ha) {
    $VolumeName = "ezkey-ha_encryption-secrets-ha"
}

Write-Host "Ezkey Encryption Keys Generator (Docker)"
if ($Ha) {
    Write-Host "   Mode: High Availability (HA)"
} elseif ($Native) {
    Write-Host "   Mode: Native"
}
Write-Host "=========================================="
Write-Host ""

try {
    docker info | Out-Null
} catch {
    Write-Host "Error: Docker is not running. Please start Docker and try again." -ForegroundColor Red
    exit 1
}

try {
    docker volume inspect $VolumeName | Out-Null
    Write-Host "Volume already exists: $VolumeName"
} catch {
    Write-Host "Creating Docker volume: $VolumeName"
    Write-Host "   Note: This volume will also be created by docker-compose if it doesn't exist"
    docker volume create $VolumeName | Out-Null
    Write-Host "Volume created"
}

$TempImage = "alpine:latest"
$ContainerName = "ezkey-keygen-$([DateTimeOffset]::Now.ToUnixTimeSeconds())"

Write-Host "Generating master key in Docker volume..."

$KeyExists = $false
try {
    & docker run --rm -v "${VolumeName}:/etc/ezkey" $TempImage test -f /etc/ezkey/secrets/master.key | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $KeyExists = $true
    }
} catch {
    $KeyExists = $false
}

if ($KeyExists) {
    Write-Host "Warning: Master key already exists in volume"
    $confirm = Read-Host "   Do you want to overwrite it? (y/N)"
    if ($confirm -notin @("y", "Y")) {
        Write-Host "   Skipping master key generation (using existing key)"
        exit 0
    }
}

$DockerScript = @'
set -e
apk add --no-cache openssl > /dev/null 2>&1

addgroup -g 101 -S spring 2>/dev/null || true
adduser -u 100 -G spring -S spring 2>/dev/null || true

mkdir -p /etc/ezkey/secrets
mkdir -p /etc/ezkey/keysets

MASTER_KEY=$(openssl rand -base64 32)

if [ -z "$MASTER_KEY" ] || [ ${#MASTER_KEY} -lt 40 ]; then
    echo "Error: Failed to generate master key" >&2
    exit 1
fi

echo "$MASTER_KEY" > /etc/ezkey/secrets/master.key

if [ ! -f /etc/ezkey/secrets/master.key ] || [ ! -s /etc/ezkey/secrets/master.key ]; then
    echo "Error: Failed to save master key" >&2
    exit 1
fi

chmod 600 /etc/ezkey/secrets/master.key
chown spring:spring /etc/ezkey/secrets/master.key

chmod 700 /etc/ezkey/secrets
chmod 755 /etc/ezkey/keysets
chown spring:spring /etc/ezkey/secrets
chown spring:spring /etc/ezkey/keysets

echo "Master key generated successfully"
echo "Location: /etc/ezkey/secrets/master.key"
echo "Owner: spring:spring (UID 100, GID 101)"
echo "Permissions: 600 (owner read/write only)"
'@

try {
    & docker run --rm --name $ContainerName -v "${VolumeName}:/etc/ezkey" $TempImage sh -c $DockerScript
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to generate master key"
    }
} catch {
    Write-Host ""
    Write-Host "Error: Failed to generate master key. Please check the error messages above." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Master key generation complete!"
Write-Host ""
Write-Host "Next steps:"
Write-Host "   1. Start Docker stack: .\start.ps1"
Write-Host "   2. The keyset will be automatically generated on first startup"
Write-Host "   3. Both admin-api and auth-api will use the same encryption keys"
Write-Host ""
Write-Host "IMPORTANT: Backup the master key securely!"
Write-Host "   To backup: docker run --rm -v ${VolumeName}:/data alpine tar czf - /data/secrets/master.key | gzip > master-key-backup.tar.gz"
Write-Host ""
Write-Host "Note: Volume name in docker-compose.yml is 'encryption-secrets'"
Write-Host "         Docker Compose creates it as '$VolumeName' (with project prefix)"
Write-Host ""
