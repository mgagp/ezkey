# Ezkey Docker Management Script for PowerShell
# This script provides commands to manage the EZ Key Docker stack
# Usage: .\manage.ps1 <command> [service] [-Parallel] [-NoCache]
#   -Parallel: Build images in parallel (default: sequential for easier log examination)
#   -NoCache: Force rebuild without using cache (default: uses BuildKit cache for optimization)

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("start", "stop", "restart", "logs", "status", "clean", "build")]
    [string]$Command,
    [Parameter(Position = 1)]
    [string]$Service,
    [switch]$Parallel,
    [switch]$NoCache
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ComposeFile = Join-Path $ScriptDir "docker-compose.yml"

function Get-DockerComposeCommand {
    try {
        docker compose version | Out-Null
        return @("docker", "compose")
    } catch {
        return @("docker-compose")
    }
}

$DockerComposeCommand = Get-DockerComposeCommand

function Invoke-DockerCompose {
    param([string[]]$Args)
    if ($DockerComposeCommand.Length -gt 1) {
        & $DockerComposeCommand[0] $DockerComposeCommand[1] @Args
    } else {
        & $DockerComposeCommand[0] @Args
    }
}

# Enable BuildKit for Maven cache mount support
$env:DOCKER_BUILDKIT = "1"
$env:COMPOSE_DOCKER_CLI_BUILD = "1"

function Start-Services {
    Write-Host "Starting EZ Key stack..."
    Set-Location (Split-Path -Parent $ScriptDir)
    Invoke-DockerCompose @("-f", $ComposeFile, "up", "-d")
    Write-Host "Services started"
}

function Stop-Services {
    Write-Host "Stopping EZ Key stack..."
    Set-Location (Split-Path -Parent $ScriptDir)
    Invoke-DockerCompose @("-f", $ComposeFile, "stop")
    Write-Host "Services stopped"
}

function Restart-Services {
    Write-Host "Restarting EZ Key stack..."
    Set-Location (Split-Path -Parent $ScriptDir)
    Invoke-DockerCompose @("-f", $ComposeFile, "restart")
    Write-Host "Services restarted"
}

function Show-Logs {
    Set-Location (Split-Path -Parent $ScriptDir)
    if ([string]::IsNullOrWhiteSpace($Service)) {
        Invoke-DockerCompose @("-f", $ComposeFile, "logs", "-f")
    } else {
        Invoke-DockerCompose @("-f", $ComposeFile, "logs", "-f", $Service)
    }
}

function Show-Status {
    Write-Host "EZ Key Stack Status:"
    Write-Host ""
    Set-Location (Split-Path -Parent $ScriptDir)
    Invoke-DockerCompose @("-f", $ComposeFile, "ps")
    Write-Host ""
    Write-Host "Health Checks:"
    Write-Host ""

    try {
        Invoke-DockerCompose @("-f", $ComposeFile, "exec", "-T", "postgres", "pg_isready", "-U", "postgres") | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  PostgreSQL: Healthy"
        } else {
            Write-Host "  PostgreSQL: Unhealthy"
        }
    } catch {
        Write-Host "  PostgreSQL: Unhealthy"
    }

    try {
        Invoke-DockerCompose @("-f", $ComposeFile, "exec", "-T", "admin-api", "curl", "-sf", "http://localhost:9081/actuator/health") | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Admin API: Healthy"
        } else {
            Write-Host "  Admin API: Unhealthy"
        }
    } catch {
        Write-Host "  Admin API: Unhealthy"
    }

    try {
        Invoke-DockerCompose @("-f", $ComposeFile, "exec", "-T", "auth-api", "curl", "-sf", "http://localhost:8085/actuator/health") | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Auth API: Healthy"
        } else {
            Write-Host "  Auth API: Unhealthy"
        }
    } catch {
        Write-Host "  Auth API: Unhealthy"
    }

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9090/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "  Crypto API: Healthy"
        } else {
            Write-Host "  Crypto API: Unhealthy"
        }
    } catch {
        Write-Host "  Crypto API: Unhealthy"
    }

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8083/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "  Demo Device: Healthy"
        } else {
            Write-Host "  Demo Device: Not available (optional service)"
        }
    } catch {
        Write-Host "  Demo Device: Not available (optional service)"
    }
}

function Clean-All {
    Write-Host "Cleaning up EZ Key stack..."
    Write-Host "This will remove all containers, networks, and volumes (including database data)"
    $confirm = Read-Host "Are you sure? (y/N)"
    if ($confirm -notin @("y", "Y")) {
        Write-Host "Cancelled."
        return
    }
    Set-Location (Split-Path -Parent $ScriptDir)
    Invoke-DockerCompose @("-f", $ComposeFile, "down", "-v", "--remove-orphans")
    Write-Host "Cleanup completed"
}

function Build-Images {
    Write-Host "Building Docker images..."

    try {
        docker volume inspect maven-cache | Out-Null
        Write-Host "  Using existing Maven cache volume"
    } catch {
        Write-Host "Creating Maven cache volume..."
        docker volume create maven-cache | Out-Null
        Write-Host "  Maven cache volume created (visible in Docker Desktop)"
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
        Write-Host "  Mode: Sequential build (easier log examination)"
    }
    Write-Host "========================================"
    Write-Host ""

    Set-Location (Split-Path -Parent $ScriptDir)

    $buildArgs = @("-f", $ComposeFile, "build")
    if ($NoCache) {
        $buildArgs += "--no-cache"
    }
    if ($Parallel) {
        $buildArgs += "--parallel"
    }

    Invoke-DockerCompose $buildArgs
    Write-Host "Build completed"
}

switch ($Command) {
    "start" { Start-Services }
    "stop" { Stop-Services }
    "restart" { Restart-Services }
    "logs" { Show-Logs }
    "status" { Show-Status }
    "clean" { Clean-All }
    "build" { Build-Images }
}
