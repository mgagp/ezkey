@echo off
REM Ezkey JavaScript/TypeScript SDK Build Script for Windows
REM This script builds the JavaScript SDK and demo application

echo === Building Ezkey JavaScript/TypeScript SDK ===
echo.

REM Check if Node.js is available
where node >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Node.js is not installed or not in PATH
    echo Please install Node.js 14+ to build the JavaScript SDK
    exit /b 1
)

REM Check if npm is available
where npm >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: npm is not installed or not in PATH
    echo Please install npm to build the JavaScript SDK
    exit /b 1
)

echo ✓ Prerequisites check passed
echo.

REM Install dependencies
echo Installing dependencies...
call npm install

if %errorlevel% neq 0 (
    echo ✗ Dependency installation failed
    exit /b 1
)
echo ✓ Dependencies installed successfully

REM Generate OpenAPI clients
echo Generating OpenAPI clients...
if not exist generated mkdir generated

REM Generate Admin API client
echo   Generating Admin API client...
java -jar ..\openapi-generator-cli-7.9.0.jar generate -i ..\admin-api-spec.json -g typescript-fetch -o generated\admin --additional-properties=typescriptThreePlus=true,supportsES6=true,npmName=ezkey-admin-client,withoutRuntimeChecks=true >nul 2>&1

REM Generate Auth API client
echo   Generating Auth API client...
java -jar ..\openapi-generator-cli-7.9.0.jar generate -i ..\auth-api-spec.json -g typescript-fetch -o generated\auth --additional-properties=typescriptThreePlus=true,supportsES6=true,npmName=ezkey-auth-client,withoutRuntimeChecks=true >nul 2>&1

if %errorlevel% neq 0 (
    echo ✗ OpenAPI client generation failed
    exit /b 1
)
echo ✓ OpenAPI clients generated successfully

REM Compile TypeScript
echo Compiling TypeScript...
call npx tsc

if %errorlevel% neq 0 (
    echo ✗ TypeScript compilation failed
    exit /b 1
)
echo ✓ TypeScript compiled successfully

echo.
echo === Build completed successfully! ===
echo.
echo To run the demo application:
echo   run-demo.cmd
echo.
echo To use the SDK in your project:
echo   Import from dist\index.js or use the TypeScript definitions in dist\index.d.ts
echo.