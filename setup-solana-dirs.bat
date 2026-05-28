@echo off
echo Creating Solana package directories...
mkdir "C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\solana" 2>nul
if exist "C:\Users\nguem\Documents\GitHub\investpro\src\main\java\org\investpro\exchange\solana" (
    echo [OK] Directory created successfully.
) else (
    echo [ERROR] Failed to create directory. Try running as Administrator.
    exit /b 1
)
echo Done.

