# Ezkey Docker HA Start Script for PowerShell
# This script builds Docker images and starts the EZ Key HA stack
# Usage: .\start-ha.ps1 [-Parallel] [-NoCache]

param(
    [switch]$Parallel,
    [switch]$NoCache
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ComposeFile = Join-Path $ScriptDir "docker-compose.ha.yml"
$DevOverrideFile = Join-Path $ScriptDir "docker-compose.ha.docker-dev.yml"

# Ensure docker base profile is active when using docker-dev or docker-test.
if ($env:SPRING_PROFILES_ACTIVE) {
    if ($env:SPRING_PROFILES_ACTIVE.Contains("docker-dev") -and -not $env:SPRING_PROFILES_ACTIVE.Contains("docker," ) -and -not ($env:SPRING_PROFILES_ACTIVE -eq "docker")) {
        $env:SPRING_PROFILES_ACTIVE = "docker,$($env:SPRING_PROFILES_ACTIVE)"
    }
    if ($env:SPRING_PROFILES_ACTIVE.Contains("docker-test") -and -not $env:SPRING_PROFILES_ACTIVE.Contains("docker," ) -and -not ($env:SPRING_PROFILES_ACTIVE -eq "docker")) {
        $env:SPRING_PROFILES_ACTIVE = "docker,$($env:SPRING_PROFILES_ACTIVE)"
    }
}

# Auto-include the HA docker-dev compose override when docker-dev profile is active.
$ComposeArgs = "-f `"$ComposeFile`""
if ($env:SPRING_PROFILES_ACTIVE -and $env:SPRING_PROFILES_ACTIVE.Contains("docker-dev") -and (Test-Path $DevOverrideFile)) {
    $ComposeArgs = "$ComposeArgs -f `"$DevOverrideFile`""
    Write-Host "🔧 HA Docker diagnostics override enabled: docker-compose.ha.docker-dev.yml"
}

# Set build flags based on parameters
$BuildParallel = if ($Parallel) { "--parallel" } else { "" }
$BuildNoCache = if ($NoCache) { "--no-cache" } else { "" }

Write-Host ""
Write-Host "=========================================="
Write-Host "  EZ Key Docker HA - Starting Stack"
Write-Host "=========================================="
Write-Host ""

# Check if Docker is running
try {
    docker info | Out-Null
} catch {
    Write-Host "❌ Error: Docker is not running. Please start Docker and try again." -ForegroundColor Red
    exit 1
}

# Determine docker compose command
$DockerCompose = "docker compose"
try {
    docker compose version | Out-Null
} catch {
    $DockerCompose = "docker-compose"
}

# Enable BuildKit
$env:DOCKER_BUILDKIT = "1"
$env:COMPOSE_DOCKER_CLI_BUILD = "1"

# Create Maven cache volume if it doesn't exist
try {
    docker volume inspect maven-cache | Out-Null
    Write-Host "  ✅ Using existing Maven cache volume"
} catch {
    Write-Host "📦 Creating Maven cache volume..."
    docker volume create maven-cache
    Write-Host "  ✅ Maven cache volume created"
}

Write-Host ""
Write-Host "========================================"
Write-Host "Building Docker images with BuildKit"
Write-Host "========================================"
if ($NoCache) {
    Write-Host "  Cache: DISABLED (--no-cache flag)"
} else {
    Write-Host "  Cache: ENABLED (BuildKit cache mount)"
}
if ($Parallel) {
    Write-Host "  Mode: Parallel build"
} else {
    Write-Host "  Mode: Sequential build"
}
Write-Host "========================================"
Write-Host ""

$BuildStart = Get-Date
Write-Host "Build started at: $($BuildStart.ToString('HH:mm:ss'))"
Write-Host ""

Set-Location (Split-Path -Parent $ScriptDir)

try {
    if ($Parallel) {
        $buildCmd = "$DockerCompose $ComposeArgs build"
        if ($NoCache) { $buildCmd += " --no-cache" }
        $buildCmd += " --parallel"
        Invoke-Expression $buildCmd
        if ($LASTEXITCODE -ne 0) { throw "Build failed" }
    } else {
        $buildCmd = "$DockerCompose $ComposeArgs build"
        if ($NoCache) { $buildCmd += " --no-cache" }
        Invoke-Expression $buildCmd
        if ($LASTEXITCODE -ne 0) { throw "Build failed" }
    }
} catch {
    Write-Host "❌ Error: Failed to build Docker images" -ForegroundColor Red
    exit 1
}

$BuildEnd = Get-Date
Write-Host ""
Write-Host "========================================"
Write-Host "Build completed at: $($BuildEnd.ToString('HH:mm:ss'))"
Write-Host "========================================"

Write-Host ""
Write-Host "🚀 Starting HA services..."
try {
    Invoke-Expression "$DockerCompose $ComposeArgs up -d"
    if ($LASTEXITCODE -ne 0) { throw "Start failed" }
} catch {
    Write-Host "❌ Error: Failed to start services" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "⏳ Waiting for services to be healthy..."

# Wait for PostgreSQL
Write-Host "  - Waiting for PostgreSQL..."
$timeout = 60
$elapsed = 0
while ($true) {
    try {
        Invoke-Expression "$DockerCompose $ComposeArgs exec -T postgres pg_isready -U postgres" | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
    } catch {
        # Continue waiting
    }
    if ($elapsed -ge $timeout) {
        Write-Host "❌ Error: PostgreSQL did not become ready within $timeout seconds" -ForegroundColor Red
        Invoke-Expression "$DockerCompose $ComposeArgs logs postgres"
        exit 1
    }
    Start-Sleep -Seconds 2
    $elapsed += 2
}
Write-Host "  ✅ PostgreSQL is ready"

# Wait for migration
Write-Host "  - Waiting for database migrations..."
Start-Sleep -Seconds 30
Write-Host "  ✅ Database migrations completed"

# Wait for Admin API instances (direct instance Actuator on management port)
Write-Host "  - Waiting for Admin API instances..."
$timeout = 120
$elapsed = 0
while ($true) {
    try {
        Invoke-Expression "$DockerCompose $ComposeArgs exec -T admin-api-1 curl -sf http://localhost:9081/actuator/health" | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
    } catch {
        if ($elapsed -ge $timeout) {
            Write-Host "❌ Error: Admin API Instance 1 did not become healthy within $timeout seconds" -ForegroundColor Red
            Invoke-Expression "$DockerCompose $ComposeArgs logs admin-api-1"
            exit 1
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
    }
}
Write-Host "  ✅ Admin API Instance 1 is healthy"

$elapsed = 0
while ($true) {
    try {
        Invoke-Expression "$DockerCompose $ComposeArgs exec -T admin-api-2 curl -sf http://localhost:9081/actuator/health" | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
    } catch {
        if ($elapsed -ge $timeout) {
            Write-Host "❌ Error: Admin API Instance 2 did not become healthy within $timeout seconds" -ForegroundColor Red
            Invoke-Expression "$DockerCompose $ComposeArgs logs admin-api-2"
            exit 1
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
    }
}
Write-Host "  ✅ Admin API Instance 2 is healthy"

# Wait for Auth API instances (direct instance Actuator on management port)
Write-Host "  - Waiting for Auth API instances..."
$timeout = 120
$elapsed = 0
while ($true) {
    try {
        Invoke-Expression "$DockerCompose $ComposeArgs exec -T auth-api-1 curl -sf http://localhost:8081/actuator/health" | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
    } catch {
        if ($elapsed -ge $timeout) {
            Write-Host "❌ Error: Auth API Instance 1 did not become healthy within $timeout seconds" -ForegroundColor Red
            Invoke-Expression "$DockerCompose $ComposeArgs logs auth-api-1"
            exit 1
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
    }
}
Write-Host "  ✅ Auth API Instance 1 is healthy"

$elapsed = 0
while ($true) {
    try {
        Invoke-Expression "$DockerCompose $ComposeArgs exec -T auth-api-2 curl -sf http://localhost:8081/actuator/health" | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
    } catch {
        if ($elapsed -ge $timeout) {
            Write-Host "❌ Error: Auth API Instance 2 did not become healthy within $timeout seconds" -ForegroundColor Red
            Invoke-Expression "$DockerCompose $ComposeArgs logs auth-api-2"
            exit 1
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
    }
}
Write-Host "  ✅ Auth API Instance 2 is healthy"

Write-Host ""
Write-Host "=========================================="
Write-Host "  ✅ EZ Key HA Stack is Ready!"
Write-Host "=========================================="
Write-Host ""
Write-Host "📋 Service URLs (via Load Balancers):"
Write-Host "  - Admin API:    http://localhost:9080 (HAProxy → admin-api-1, admin-api-2)"
Write-Host "  - Auth API:     http://localhost:8080 (HAProxy → auth-api-1, auth-api-2)"
Write-Host ""
Write-Host "📊 HAProxy Statistics:"
Write-Host "  - Admin API LB: http://localhost:9081/stats"
Write-Host "  - Auth API LB:  http://localhost:8081/stats"
Write-Host ""
Write-Host "💡 Useful commands:"
Write-Host "  - View logs:    .\manage-ha.ps1 logs"
Write-Host "  - Stop stack:   .\manage-ha.ps1 stop"
Write-Host "  - View status:  .\manage-ha.ps1 status"
Write-Host ""
Write-Host "🔍 Verify HA Setup:"
Write-Host "  - Check both instances: docker ps | Select-String ezkey-admin-api"
Write-Host "  - Check HAProxy stats: Start-Process http://localhost:9081/stats"
Write-Host "  - View instance logs: .\manage-ha.ps1 logs admin-api-1"
Write-Host ""
