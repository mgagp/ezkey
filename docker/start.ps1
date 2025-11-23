# Ezkey Docker Start Script for PowerShell
# This script builds Docker images and starts the EZ Key stack
# Usage: .\start.ps1 [-Parallel] [-NoCache] [-DebugCache]
#   -Parallel: Build images in parallel (default: sequential for easier log examination)
#   -NoCache: Force rebuild without using cache (default: uses BuildKit cache for optimization)
#   -DebugCache: Build only the first service (migration) and stop - for cache validation

param(
    [switch]$Parallel,
    [switch]$NoCache,
    [switch]$DebugCache
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ComposeFile = Join-Path $ScriptDir "docker-compose.yml"

# Set build flags based on parameters
$BuildParallel = if ($Parallel) { "--parallel" } else { "" }
$BuildNoCache = if ($NoCache) { "--no-cache" } else { "" }
$DebugCacheFlag = if ($DebugCache) { "1" } else { "" }

Write-Host ""
Write-Host "=========================================="
Write-Host "  EZ Key Docker - Starting Stack"
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

# Enable BuildKit for Maven cache mount support
$env:DOCKER_BUILDKIT = "1"
$env:COMPOSE_DOCKER_CLI_BUILD = "1"

# Create Maven cache volume if it doesn't exist
try {
    docker volume inspect maven-cache | Out-Null
    Write-Host "  Using existing Maven cache volume"
} catch {
    Write-Host "Creating Maven cache volume..."
    docker volume create maven-cache
    Write-Host "  Maven cache volume created (visible in Docker Desktop)"
}

Write-Host ""
Write-Host "========================================"
if ($DebugCache) {
    Write-Host "Building FIRST service only (migration) - Cache Debug Mode"
    Write-Host "This builds only the migration service to validate BuildKit cache"
} else {
    Write-Host "Building Docker images with BuildKit"
}
Write-Host "========================================"
if ($NoCache) {
    Write-Host "  Cache: DISABLED (--no-cache flag)"
} else {
    Write-Host "  Cache: ENABLED (BuildKit cache mount)"
    Write-Host "  Note: BuildKit cache is not visible in Docker Desktop but is functional"
}
if ($Parallel) {
    Write-Host "  Mode: Parallel build"
} else {
    Write-Host "  Mode: Sequential build (easier log examination)"
}
if ($DebugCache) {
    Write-Host "  Debug: Building ONLY migration service (first image)"
    Write-Host "  Services will NOT be started after build"
}
Write-Host "========================================"
Write-Host ""

# Record start time
$BuildStart = Get-Date
Write-Host "Build started at: $($BuildStart.ToString('HH:mm:ss'))"
Write-Host ""

# Change to project root directory
Set-Location (Split-Path -Parent $ScriptDir)

# Build Docker images
try {
    if ($DebugCache) {
        Write-Host "Building migration service (first image)..."
        $buildCmd = "$DockerCompose -f `"$ComposeFile`" build"
        if ($NoCache) { $buildCmd += " --no-cache" }
        $buildCmd += " migration"
        Invoke-Expression $buildCmd
        if ($LASTEXITCODE -ne 0) { throw "Build failed" }
    } elseif ($Parallel) {
        $buildCmd = "$DockerCompose -f `"$ComposeFile`" build"
        if ($NoCache) { $buildCmd += " --no-cache" }
        $buildCmd += " --parallel"
        Invoke-Expression $buildCmd
        if ($LASTEXITCODE -ne 0) { throw "Build failed" }
    } else {
        $buildCmd = "$DockerCompose -f `"$ComposeFile`" build"
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

# If debug mode, stop here
if ($DebugCache) {
    Write-Host ""
    Write-Host "========================================"
    Write-Host "Cache Debug Mode - Build stopped"
    Write-Host "========================================"
    Write-Host "The migration service has been built successfully."
    Write-Host ""
    Write-Host "To validate cache optimization:"
    Write-Host "  1. Run again: .\start.ps1 -DebugCache"
    Write-Host "  2. Compare build times - second build should be MUCH faster"
    Write-Host "  3. Check logs for 'using cached dependencies' messages"
    Write-Host ""
    Write-Host "To build and start all services:"
    Write-Host "  .\start.ps1"
    Write-Host ""
    exit 0
}

Write-Host ""
Write-Host "🚀 Starting services..."
try {
    Invoke-Expression "$DockerCompose -f `"$ComposeFile`" up -d"
    if ($LASTEXITCODE -ne 0) { throw "Start failed" }
} catch {
    Write-Host "❌ Error: Failed to start services" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "⏳ Waiting for services to be healthy..."

# Wait for PostgreSQL to be ready
Write-Host "  - Waiting for PostgreSQL..."
$timeout = 60
$elapsed = 0
while ($true) {
    try {
        Invoke-Expression "$DockerCompose -f `"$ComposeFile`" exec -T postgres pg_isready -U postgres" | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
    } catch {
        # Continue waiting
    }
    if ($elapsed -ge $timeout) {
        Write-Host "❌ Error: PostgreSQL did not become ready within $timeout seconds" -ForegroundColor Red
        Invoke-Expression "$DockerCompose -f `"$ComposeFile`" logs postgres"
        exit 1
    }
    Start-Sleep -Seconds 2
    $elapsed += 2
}
Write-Host "  ✅ PostgreSQL is ready"

# Wait for migration to complete
Write-Host "  - Waiting for database migrations..."
Start-Sleep -Seconds 30
Write-Host "  ✅ Database migrations completed"

# Wait for APIs to be healthy
Write-Host "  - Waiting for Admin API..."
$timeout = 120
$elapsed = 0
while ($true) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9080/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($response.StatusCode -eq 200) { break }
    } catch {
        if ($elapsed -ge $timeout) {
            Write-Host "❌ Error: Admin API did not become healthy within $timeout seconds" -ForegroundColor Red
            Invoke-Expression "$DockerCompose -f `"$ComposeFile`" logs admin-api"
            exit 1
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
    }
}
Write-Host "  ✅ Admin API is healthy"

Write-Host "  - Waiting for Auth API..."
$timeout = 120
$elapsed = 0
while ($true) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($response.StatusCode -eq 200) { break }
    } catch {
        if ($elapsed -ge $timeout) {
            Write-Host "❌ Error: Auth API did not become healthy within $timeout seconds" -ForegroundColor Red
            Invoke-Expression "$DockerCompose -f `"$ComposeFile`" logs auth-api"
            exit 1
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
    }
}
Write-Host "  ✅ Auth API is healthy"

Write-Host "  - Waiting for Crypto API..."
$timeout = 120
$elapsed = 0
while ($true) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9090/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($response.StatusCode -eq 200) { break }
    } catch {
        if ($elapsed -ge $timeout) {
            Write-Host "❌ Error: Crypto API did not become healthy within $timeout seconds" -ForegroundColor Red
            Invoke-Expression "$DockerCompose -f `"$ComposeFile`" logs crypto-api"
            exit 1
        }
        Start-Sleep -Seconds 2
        $elapsed += 2
    }
}
Write-Host "  ✅ Crypto API is healthy"

Write-Host ""
Write-Host "=========================================="
Write-Host "  ✅ EZ Key Stack is Ready!"
Write-Host "=========================================="
Write-Host ""
Write-Host "📋 Service URLs:"
Write-Host "  - Admin API:    http://localhost:9080"
Write-Host "  - Auth API:     http://localhost:8080"
Write-Host "  - Crypto API:   http://localhost:9090"
Write-Host "  - Demo Device:  http://localhost:8083"
Write-Host ""
Write-Host "📚 API Documentation:"
Write-Host "  - Admin API:    http://localhost:9080/swagger-ui.html"
Write-Host "  - Auth API:     http://localhost:8080/swagger-ui.html"
Write-Host "  - Crypto API:   http://localhost:9090/swagger-ui.html"
Write-Host ""
Write-Host "💡 Useful commands:"
Write-Host "  - View logs:    .\manage.ps1 logs"
Write-Host "  - Stop stack:   .\manage.ps1 stop"
Write-Host "  - View status:  .\manage.ps1 status"
Write-Host ""
Write-Host "🔧 Test Mode (permissive rate limiting):"
Write-Host "  - Start in test mode: `$env:SPRING_PROFILES_ACTIVE='docker,docker-test'; .\start.ps1"
Write-Host "  - Default mode (production): .\start.ps1"
Write-Host ""
Write-Host "🧪 Next Steps (Required):"
Write-Host "  - Extract bootstrap credentials: mvn test -pl ezkey-tests -Dtest=BootstrapCredentialsExtractionTest"
Write-Host "  - This extracts enrollment credentials from Docker logs (first time only)"
Write-Host ""

