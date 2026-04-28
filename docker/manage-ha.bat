@echo off
REM Ezkey Docker HA Management Script for Windows
REM This script provides commands to manage the EZ Key HA Docker stack
REM Usage: manage-ha.bat [command]

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set COMMAND=%1

set COMPOSE_FILE=%SCRIPT_DIR%docker-compose.ha.yml

REM Determine docker compose command
docker compose version >nul 2>&1
if errorlevel 1 (
    set DOCKER_COMPOSE=docker-compose
) else (
    set DOCKER_COMPOSE=docker compose
)

REM Enable BuildKit
set DOCKER_BUILDKIT=1
set COMPOSE_DOCKER_CLI_BUILD=1

if "%COMMAND%"=="" goto usage

if "%COMMAND%"=="start" goto start_services
if "%COMMAND%"=="stop" goto stop_services
if "%COMMAND%"=="restart" goto restart_services
if "%COMMAND%"=="logs" goto show_logs
if "%COMMAND%"=="status" goto show_status
if "%COMMAND%"=="clean" goto clean_all
if "%COMMAND%"=="build" goto build_images

:usage
echo Usage: %0 {start^|stop^|restart^|logs^|status^|clean^|build}
echo.
echo Commands:
echo   start   - Start all HA services
echo   stop    - Stop all HA services
echo   restart - Restart all HA services
echo   logs    - Show logs (use 'logs ^<service^>' for specific service)
echo   status  - Show status of all HA services
echo   clean   - Stop and remove all containers, networks, and volumes
echo   build   - Build all Docker images
echo.
echo Examples:
echo   %0 logs admin-api-1    - View logs for admin-api-1
echo   %0 logs haproxy-admin   - View HAProxy admin logs
goto end

:start_services
echo Starting EZ Key HA stack...
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" up -d
if errorlevel 1 (
    echo [31mError: Failed to start services[0m
    exit /b 1
)
echo [32m[OK][0m HA services started
goto end

:stop_services
echo Stopping EZ Key HA stack...
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" stop
echo [32m[OK][0m HA services stopped
goto end

:restart_services
echo Restarting EZ Key HA stack...
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" restart
echo [32m[OK][0m HA services restarted
goto end

:show_logs
cd /d "%SCRIPT_DIR%.."
if "%2"=="" (
    %DOCKER_COMPOSE% -f "%COMPOSE_FILE%" logs -f
) else (
    %DOCKER_COMPOSE% -f "%COMPOSE_FILE%" logs -f %2
)
goto end

:show_status
echo EZ Key HA Stack Status:
echo.
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" ps
echo.
echo Health Checks:
echo.
curl -sf http://localhost:9080/actuator/health >nul 2>&1
if errorlevel 1 (
    echo   [31m[FAIL][0m Admin API (via HAProxy): Unhealthy
) else (
    echo   [32m[OK][0m Admin API (via HAProxy): Healthy
)
curl -sf http://localhost:8080/actuator/health >nul 2>&1
if errorlevel 1 (
    echo   [31m[FAIL][0m Auth API (via HAProxy): Unhealthy
) else (
    echo   [32m[OK][0m Auth API (via HAProxy): Healthy
)
echo.
echo HAProxy Statistics:
echo   - Admin API LB: http://localhost:9081/stats
echo   - Auth API LB:  http://localhost:8085/stats
goto end

:clean_all
echo Cleaning up EZ Key HA stack...
echo [33mWarning: This will remove all containers, networks, and volumes (including database data)[0m
set /p confirm="Are you sure? (y/N): "
if /i not "%confirm%"=="y" (
    echo Cancelled.
    goto end
)
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" down -v --remove-orphans
echo [32m[OK][0m Cleanup completed
goto end

:build_images
echo Building Docker images for HA stack...

echo.
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" build
if errorlevel 1 (
    echo [31mError: Failed to build Docker images[0m
    exit /b 1
)
echo [32m[OK][0m Build completed
goto end

:end
endlocal
