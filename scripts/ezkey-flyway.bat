@echo off
setlocal enabledelayedexpansion

echo Ezkey Flyway Migration Tool
echo ===========================

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0
set MIGRATION_DIR=%SCRIPT_DIR%..\ezkey-migration

REM Check if ezkey-migration directory exists
if not exist "%MIGRATION_DIR%" (
    echo Error: ezkey-migration directory not found at %MIGRATION_DIR%
    exit /b 1
)

REM Change to the migration directory
cd /d "%MIGRATION_DIR%"

REM Check if migration JAR exists, build if needed
REM Use wildcard to find the JAR with version
for %%f in (target\ezkey-migration-*.jar) do set MIGRATION_JAR=%%f
if not exist "%MIGRATION_JAR%" (
    echo Migration JAR not found. Building...
    mvn clean package -Pmigration-jar -q
    REM Re-find the JAR after building
    for %%f in (target\ezkey-migration-*.jar) do set MIGRATION_JAR=%%f
)

REM Run the migration application
if "%1"=="" (
    echo Running default migration...
    mvn spring-boot:run -q
) else (
    echo Running Flyway command: %*
    mvn spring-boot:run -Dspring-boot.run.arguments="%*" -q
)

echo Done!
endlocal
