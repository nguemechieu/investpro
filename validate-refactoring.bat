@echo off
REM InvestPro Compilation Validation Script
REM Run this to verify the refactoring progress

echo.
echo ================================================================
echo InvestPro Architecture Refactoring - Compilation Validation
echo ================================================================
echo.

cd c:\Users\nguem\Documents\GitHub\investpro

echo [1/3] Running Maven clean compile...
echo.

call mvnw clean compile -q

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================================
    echo ✅ COMPILATION SUCCESSFUL
    echo ================================================================
    echo.
    echo Next Steps:
    echo   1. Read ARCHITECTURE.md for design overview
    echo   2. Read STATUS.md for current progress
    echo   3. Read REFACTORING_CHECKLIST.md for next actions
    echo   4. Verify agent behavior implementations
    echo   5. Test streaming lifecycle (bot on/off)
    echo.
) else (
    echo.
    echo ================================================================
    echo ❌ COMPILATION FAILED
    echo ================================================================
    echo.
    echo Re-running with error details...
    echo.
    call mvnw clean compile
    echo.
    echo Check errors above and fix in priority order:
    echo   1. Missing method implementations
    echo   2. Type mismatches (Symbol vs String vs TradePair)
    echo   3. Missing dependencies
    echo   4. Threading issues (Platform.runLater)
    echo.
)

pause
