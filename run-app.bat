@echo off
setlocal

REM Run InvestPro with the local Java 21 JDK and Maven-copied dependencies.
set "APP_HOME=%~dp0"
set "JAVA_HOME=C:\Users\nguem\.jdks\ms-21.0.11"

REM The launcher uses target\classes directly. Rebuild if a required runtime
REM class is missing so stale output does not fail with NoClassDefFoundError.
if not exist "%APP_HOME%target\classes\org\investpro\InvestPro.class" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
  if errorlevel 1 exit /b %errorlevel%
)

if not exist "%APP_HOME%target\classes\org\investpro\ui\panels\MarketWatchPanel.class" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
  if errorlevel 1 exit /b %errorlevel%
)

if not exist "%APP_HOME%target\classes\org\investpro\ui\models\MarketWatchRow.class" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
  if errorlevel 1 exit /b %errorlevel%
)

if not exist "%APP_HOME%target\lib" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
  if errorlevel 1 exit /b %errorlevel%
)

"%JAVA_HOME%\bin\java.exe" ^
  -cp "%APP_HOME%target\classes;%APP_HOME%target\lib\*" ^
  org.investpro.InvestProLauncher

endlocal
