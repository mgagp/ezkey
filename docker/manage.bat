@echo off
REM Ezkey Docker Management Script for Windows
REM This script provides commands to manage the EZ Key Docker stack
REM Usage: manage.bat [command] [--parallel] [--no-cache]
REM   --parallel: Build images in parallel (default: sequential for easier log examination)
REM   --no-cache: Force rebuild without using cache (default: uses BuildKit cache for optimization)

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set COMMAND=%1
set BUILD_PARALLEL=""
set BUILD_NO_CACHE=""

REM Parse flags
if "%2"=="--parallel" (
    set BUILD_PARALLEL=--parallel
)
if "%2"=="--no-cache" (
    set BUILD_NO_CACHE=--no-cache
)
if "%3"=="--parallel" (
    set BUILD_PARALLEL=--parallel
)
if "%3"=="--no-cache" (
    set BUILD_NO_CACHE=--no-cache
)

set COMPOSE_FILE=%SCRIPT_DIR%docker-compose.yml

REM Determine docker compose command
docker compose version >nul 2>&1
if errorlevel 1 (
    set DOCKER_COMPOSE=docker-compose
) else (
    set DOCKER_COMPOSE=docker compose
)

REM Enable BuildKit for Maven cache mount support
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
echo Usage: %0 {start^|stop^|restart^|logs^|status^|clean^|build} [--parallel] [--no-cache]
echo.
echo Commands:
echo   start   - Start all services
echo   stop    - Stop all services
echo   restart - Restart all services
echo   logs    - Show logs (use 'logs ^<service^>' for specific service)
echo   status  - Show status of all services
echo   clean   - Stop and remove all containers, networks, and volumes
echo   build   - Build all Docker images
echo.
echo Build options:
echo   --parallel - Build images in parallel (default: sequential for easier log examination)
echo   --no-cache - Force rebuild without using cache
exit /b 1

:start_services
echo Starting EZ Key stack...
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" up -d
if errorlevel 1 (
    echo [31mError: Failed to start services[0m
    exit /b 1
)
echo [32m[OK][0m Services started
goto end

:stop_services
echo Stopping EZ Key stack...
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" stop
echo [32m[OK][0m Services stopped
goto end

:restart_services
echo Restarting EZ Key stack...
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" restart
echo [32m[OK][0m Services restarted
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
echo EZ Key Stack Status:
echo.
cd /d "%SCRIPT_DIR%.."
%DOCKER_COMPOSE% -f "%COMPOSE_FILE%" ps
echo.
echo Health Checks:
echo.
curl -sf http://localhost:9080/actuator/health >nul 2>&1
if errorlevel 1 (
    echo   [31m[FAIL][0m Admin API: Unhealthy
) else (
    echo   [32m[OK][0m Admin API: Healthy
)
curl -sf http://localhost:8080/actuator/health >nul 2>&1
if errorlevel 1 (
    echo   [31m[FAIL][0m Auth API: Unhealthy
) else (
    echo   [32m[OK][0m Auth API: Healthy
)
curl -sf http://localhost:9090/actuator/health >nul 2>&1
if errorlevel 1 (
    echo   [31m[FAIL][0m Crypto API: Unhealthy
) else (
    echo   [32m[OK][0m Crypto API: Healthy
)
goto end

:clean_all
echo Cleaning up EZ Key stack...
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
echo Building Docker images...

REM Create Maven cache volume if it doesn't exist
docker volume inspect maven-cache >nul 2>&1
if errorlevel 1 (
    echo Creating Maven cache volume...
    docker volume create maven-cache
    echo   Maven cache volume created (visible in Docker Desktop)
) else (
    echo   Using existing Maven cache volume
)

echo.
echo ========================================
echo Building Docker images with BuildKit
echo ========================================
if "%BUILD_NO_CACHE%"=="--no-cache" (
    echo   Cache: DISABLED (--no-cache flag)
) else (
    echo   Cache: ENABLED (BuildKit cache mount)
)
if "%BUILD_PARALLEL%"=="--parallel" (
    echo   Mode: Parallel build
    cd /d "%SCRIPT_DIR%.."
    %DOCKER_COMPOSE% -f "%COMPOSE_FILE%" build %BUILD_NO_CACHE% --parallel
) else (
    echo   Mode: Sequential build (easier log examination)
    cd /d "%SCRIPT_DIR%.."
    %DOCKER_COMPOSE% -f "%COMPOSE_FILE%" build %BUILD_NO_CACHE%
)
if errorlevel 1 (
    echo [31mError: Failed to build Docker images[0m
    exit /b 1
)
echo [32m[OK][0m Build completed
goto end

:end
endlocal
