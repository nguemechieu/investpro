@echo off
cd /d "C:\Users\nguem\Documents\GitHub\investpro"
mkdir "src\main\java\org\investpro\exchange\execution" 2>nul
mkdir "src\main\java\org\investpro\exchange\routing" 2>nul
mkdir "src\main\java\org\investpro\exchange\throttle" 2>nul
mkdir "src\main\java\org\investpro\exchange\coordination" 2>nul
mkdir "src\main\java\org\investpro\exchange\cache" 2>nul
mkdir "src\main\java\org\investpro\exchange\blockchain" 2>nul
mkdir "src\main\java\org\investpro\exchange\distributed" 2>nul
echo Directories created successfully
echo Verifying...
if exist "src\main\java\org\investpro\exchange\execution" echo ✓ execution
if exist "src\main\java\org\investpro\exchange\routing" echo ✓ routing
if exist "src\main\java\org\investpro\exchange\throttle" echo ✓ throttle
if exist "src\main\java\org\investpro\exchange\coordination" echo ✓ coordination
if exist "src\main\java\org\investpro\exchange\cache" echo ✓ cache
if exist "src\main\java\org\investpro\exchange\blockchain" echo ✓ blockchain
if exist "src\main\java\org\investpro\exchange\distributed" echo ✓ distributed
