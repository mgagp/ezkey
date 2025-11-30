@echo off
REM Ezkey - Convert Line Endings from CRLF to LF
REM This script converts all Java files in the project to use LF line endings
REM per Google Java Style Guide

echo ====================================================================
echo Ezkey - Line Ending Conversion (CRLF to LF)
echo ====================================================================
echo.
echo This script will convert all Java files to use LF (Unix) line endings
echo as required by Google Java Style Guide.
echo.
echo Tools used:
echo   1. Git normalize (via .gitattributes)
echo   2. Maven Spotless (Google Java Format)
echo.
echo WARNING: This will modify all .java files in the repository.
echo Press Ctrl+C to cancel, or
pause

echo.
echo Step 1: Normalizing line endings via Git...
echo ====================================================================
git add --renormalize .
echo Done!

echo.
echo Step 2: Applying Spotless formatting (includes line ending normalization)...
echo ====================================================================
call mvn spotless:apply
echo Done!

echo.
echo Step 3: Verification with Checkstyle...
echo ====================================================================
echo Checking ezkey-core module...
call mvn checkstyle:check -pl ezkey-core -Dcheckstyle.skip=false

echo.
echo ====================================================================
echo Conversion Complete!
echo ====================================================================
echo.
echo Next steps:
echo   1. Review changes with: git diff
echo   2. Commit changes: git commit -m "chore: normalize line endings to LF per Google Java Style Guide"
echo   3. Push to repository
echo.
echo Note: Future commits will automatically use LF thanks to .gitattributes
echo.
pause
