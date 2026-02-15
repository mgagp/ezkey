# Ezkey Docker HA Management Script for PowerShell
# This script provides commands to manage the EZ Key HA Docker stack
# Usage: .\manage-ha.ps1 <command> [service]

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("start", "stop", "restart", "logs", "status", "clean", "build")]
    [string]$Command,
    [Parameter(Position = 1)]
    [string]$Service
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ComposeFile = Join-Path $ScriptDir "docker-compose.ha.yml"

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

# Enable BuildKit
$env:DOCKER_BUILDKIT = "1"
$env:COMPOSE_DOCKER_CLI_BUILD = "1"

function Start-Services {
    Write-Host "Starting EZ Key HA stack..."
    Set-Location (Split-Path -Parent $ScriptDir)
    Invoke-DockerCompose @("-f", $ComposeFile, "up", "-d")
    Write-Host "HA services started"
}

function Stop-Services {
    Write-Host "Stopping EZ Key HA stack..."
    Set-Location (Split-Path -Parent $ScriptDir)
    Invoke-DockerCompose @("-f", $ComposeFile, "stop")
    Write-Host "HA services stopped"
}

function Restart-Services {
    Write-Host "Restarting EZ Key HA stack..."
    Set-Location (Split-Path -Parent $ScriptDir)
    Invoke-DockerCompose @("-f", $ComposeFile, "restart")
    Write-Host "HA services restarted"
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
    Write-Host "EZ Key HA Stack Status:"
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
        $response = Invoke-WebRequest -Uri "http://localhost:9081/stats" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "  Admin API Load Balancer (stats): Healthy"
        } else {
            Write-Host "  Admin API Load Balancer (stats): Unhealthy"
        }
    } catch {
        Write-Host "  Admin API Load Balancer (stats): Unhealthy"
    }

    try {
        Invoke-DockerCompose @("-f", $ComposeFile, "exec", "-T", "admin-api-1", "curl", "-sf", "http://localhost:9081/actuator/health") | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Admin API Instance 1: Healthy"
        } else {
            Write-Host "  Admin API Instance 1: Unhealthy"
        }
    } catch {
        Write-Host "  Admin API Instance 1: Unhealthy"
    }

    try {
        Invoke-DockerCompose @("-f", $ComposeFile, "exec", "-T", "admin-api-2", "curl", "-sf", "http://localhost:9081/actuator/health") | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Admin API Instance 2: Healthy"
        } else {
            Write-Host "  Admin API Instance 2: Unhealthy"
        }
    } catch {
        Write-Host "  Admin API Instance 2: Unhealthy"
    }

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8085/stats" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "  Auth API Load Balancer (stats): Healthy"
        } else {
            Write-Host "  Auth API Load Balancer (stats): Unhealthy"
        }
    } catch {
        Write-Host "  Auth API Load Balancer (stats): Unhealthy"
    }

    try {
        Invoke-DockerCompose @("-f", $ComposeFile, "exec", "-T", "auth-api-1", "curl", "-sf", "http://localhost:8085/actuator/health") | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Auth API Instance 1: Healthy"
        } else {
            Write-Host "  Auth API Instance 1: Unhealthy"
        }
    } catch {
        Write-Host "  Auth API Instance 1: Unhealthy"
    }

    try {
        Invoke-DockerCompose @("-f", $ComposeFile, "exec", "-T", "auth-api-2", "curl", "-sf", "http://localhost:8085/actuator/health") | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Auth API Instance 2: Healthy"
        } else {
            Write-Host "  Auth API Instance 2: Unhealthy"
        }
    } catch {
        Write-Host "  Auth API Instance 2: Unhealthy"
    }

    Write-Host ""
    Write-Host "📊 HAProxy Statistics:"
    Write-Host "  - Admin API LB: http://localhost:9081/stats"
    Write-Host "  - Auth API LB:  http://localhost:8085/stats"
}

function Clean-All {
    Write-Host "Cleaning up EZ Key HA stack..."
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
    Write-Host "Building Docker images for HA stack..."

    try {
        docker volume inspect maven-cache | Out-Null
        Write-Host "  Using existing Maven cache volume"
    } catch {
        Write-Host "Creating Maven cache volume..."
        docker volume create maven-cache | Out-Null
        Write-Host "  Maven cache volume created"
    }

    Set-Location (Split-Path -Parent $ScriptDir)
    Invoke-DockerCompose @("-f", $ComposeFile, "build")
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
