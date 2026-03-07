@echo off
REM Ezkey OpenAPI Specifications Update Script for Windows
REM This script centralizes the management of OpenAPI specifications for all Ezkey projects

setlocal enabledelayedexpansion

REM Configuration
set "PROJECT_ROOT=%~dp0.."
set "SPECS_DIR=%PROJECT_ROOT%\specs"
set "ADMIN_API_URL=http://localhost:9080/api-docs"
set "AUTH_API_URL=http://localhost:8080/api-docs"

echo === Ezkey OpenAPI Specifications Update Script ===
echo.

REM Check if curl is available
where curl >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] curl is not installed or not in PATH
    echo Please install curl to download OpenAPI specifications
    exit /b 1
)

REM Ensure specs directory exists
if not exist "%SPECS_DIR%" mkdir "%SPECS_DIR%"
if not exist "%SPECS_DIR%\admin-api" mkdir "%SPECS_DIR%\admin-api"
if not exist "%SPECS_DIR%\auth-api" mkdir "%SPECS_DIR%\auth-api"

REM Parse command line arguments
set "UPDATE_ADMIN=true"
set "UPDATE_AUTH=true"

:parse_args
if "%~1"=="" goto :start_update
if "%~1"=="--admin-only" (
    set "UPDATE_ADMIN=true"
    set "UPDATE_AUTH=false"
    shift
    goto :parse_args
)
if "%~1"=="--auth-only" (
    set "UPDATE_ADMIN=false"
    set "UPDATE_AUTH=true"
    shift
    goto :parse_args
)
if "%~1"=="--all" (
    set "UPDATE_ADMIN=true"
    set "UPDATE_AUTH=true"
    shift
    goto :parse_args
)
if "%~1"=="--help" (
    call :show_help
    exit /b 0
)
echo [ERROR] Unknown option: %~1
call :show_help
exit /b 1

:start_update
echo [INFO] Starting Ezkey OpenAPI specifications update...
echo [INFO] Project root: %PROJECT_ROOT%
echo [INFO] Specs directory: %SPECS_DIR%
echo.

set "SUCCESS_COUNT=0"
set "TOTAL_COUNT=0"

REM Update admin-api specification
if "%UPDATE_ADMIN%"=="true" (
    set /a TOTAL_COUNT+=1
    call :update_spec "admin-api" "%ADMIN_API_URL%"
    if !errorlevel! equ 0 set /a SUCCESS_COUNT+=1
)

REM Update auth-api specification
if "%UPDATE_AUTH%"=="true" (
    set /a TOTAL_COUNT+=1
    call :update_spec "auth-api" "%AUTH_API_URL%"
    if !errorlevel! equ 0 set /a SUCCESS_COUNT+=1
)

REM Summary
echo.
if %SUCCESS_COUNT% equ %TOTAL_COUNT% (
    echo [SUCCESS] All specifications updated successfully! (%SUCCESS_COUNT%/%TOTAL_COUNT%)
    echo [INFO] You can now build demo projects and SDKs with updated specifications
) else (
    echo [WARNING] Some specifications failed to update (%SUCCESS_COUNT%/%TOTAL_COUNT%)
    echo [INFO] Check that APIs are running and accessible
    exit /b 1
)

exit /b 0

REM Function to update a specification
:update_spec
set "API_NAME=%~1"
set "URL=%~2"
set "SPEC_FILE=%SPECS_DIR%\%API_NAME%\openapi-spec.json"
set "BACKUP_FILE=%SPEC_FILE%.backup"

echo [INFO] Updating %API_NAME% specification...

REM Check if API is available
curl -s --connect-timeout 5 "%URL%" >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] %API_NAME% is not accessible at %URL%
    echo [WARNING] Make sure the API is running before updating specifications
    exit /b 1
)

REM Create backup if file exists
if exist "%SPEC_FILE%" (
    copy "%SPEC_FILE%" "%BACKUP_FILE%" >nul
    echo [INFO] Backup created: %BACKUP_FILE%
)

REM Download new specification
curl -s "%URL%" -o "%SPEC_FILE%"
if %errorlevel% equ 0 (
    REM Try to format JSON if jq is available
    where jq >nul 2>&1
    if %errorlevel% equ 0 (
        jq . "%SPEC_FILE%" > "%SPEC_FILE%.tmp" 2>nul
        if %errorlevel% equ 0 (
            move "%SPEC_FILE%.tmp" "%SPEC_FILE%" >nul
            echo [SUCCESS] %API_NAME% specification updated, validated, and formatted
        ) else (
            del "%SPEC_FILE%.tmp" >nul 2>&1
            echo [SUCCESS] %API_NAME% specification updated (formatting failed)
        )
    ) else (
        echo [SUCCESS] %API_NAME% specification updated (formatting skipped - jq not available)
        echo [WARNING] Consider installing jq for JSON validation and formatting
    )
    call :update_project_links "%API_NAME%" "%SPEC_FILE%"
    exit /b 0
) else (
    echo [ERROR] Failed to download %API_NAME% specification
    REM Restore backup
    if exist "%BACKUP_FILE%" (
        move "%BACKUP_FILE%" "%SPEC_FILE%" >nul
        echo [WARNING] Restored backup for %API_NAME%
    )
    exit /b 1
)

REM Function to update project links
:update_project_links
set "API_NAME=%~1"
set "SPEC_FILE=%~2"

if "%API_NAME%"=="admin-api" (
    echo [INFO] Updating admin-api links...
    call :update_link "%SPEC_FILE%" "%PROJECT_ROOT%\ezkey-demo-app-acme\openapi-spec.json"
    call :update_link "%SPEC_FILE%" "%PROJECT_ROOT%\ezkey-sdk\admin-api-spec.json"
    call :update_link "%SPEC_FILE%" "%PROJECT_ROOT%\ezkey-admin-ui\openapi-spec.json"
) else if "%API_NAME%"=="auth-api" (
    echo [INFO] Updating auth-api links...
    call :update_link "%SPEC_FILE%" "%PROJECT_ROOT%\ezkey-demo-device\openapi-spec.json"
    call :update_link "%SPEC_FILE%" "%PROJECT_ROOT%\ezkey-sdk\auth-api-spec.json"
)

exit /b 0

REM Function to create copies (Windows doesn't support symlinks easily)
:update_link
set "SOURCE=%~1"
set "TARGET=%~2"
set "TARGET_DIR=%~dp2"

REM Ensure target directory exists
if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"

REM Copy file
copy "%SOURCE%" "%TARGET%" >nul
echo [INFO] Copied to %~nx2

exit /b 0

REM Function to show help
:show_help
echo Ezkey OpenAPI Specifications Update Script
echo.
echo Usage: %~nx0 [OPTIONS]
echo.
echo Options:
echo   --admin-only    Update only admin-api specification
echo   --auth-only     Update only auth-api specification
echo   --all           Update all specifications (default)
echo   --help          Show this help message
echo.
echo Examples:
echo   %~nx0                    # Update all specifications
echo   %~nx0 --admin-only       # Update only admin-api
echo   %~nx0 --auth-only        # Update only auth-api
echo.
echo Prerequisites:
echo   - APIs must be running on localhost:9080 (admin) and localhost:8080 (auth)
echo   - curl must be available for downloading specifications
echo   - jq is optional but recommended for JSON validation and formatting
echo.
exit /b 0
