@echo off
setlocal

REM Run InvestPro with Java 27 and Maven-copied dependencies.
set "APP_HOME=%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=%ProgramFiles%\Java\jdk-27"
if not exist "%JAVA_HOME%\bin\javac.exe" (
  echo Set JAVA_HOME to your Java 27 JDK installation.
  exit /b 1
)

REM The launcher uses target\classes directly. Rebuild if a required runtime
REM class is missing so stale output does not fail with NoClassDefFoundError.
REM A normal incremental build may leave a deleted supporting class missing.
REM Rebuild the whole output before launching when the chart palette is absent.
if not exist "%APP_HOME%target\classes\org\investpro\exchange\ibkr\IbkrTwsSession.class" (
  pushd "%APP_HOME%"
  call "%APP_HOME%mvnw.cmd" -DskipTests package
  if errorlevel 1 (
    popd
    exit /b 1
  )
  popd
)
if not exist "%APP_HOME%target\classes\org\investpro\ui\charts\ChartColors.class" (
  pushd "%APP_HOME%"
  call "%APP_HOME%mvnw.cmd" -DskipTests clean package
  if errorlevel 1 (
    popd
    exit /b 1
  )
  popd
)

if not exist "%APP_HOME%target\classes\org\investpro\ui\charts\ChartColors.class" (
  echo Build output is missing ChartColors.class. InvestPro was not started.
  exit /b 1
)

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

if not exist "%APP_HOME%target\classes\org\investpro\ui\tools\ChartToolbar.class" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
  if errorlevel 1 exit /b %errorlevel%
)

if not exist "%APP_HOME%target\classes\org\investpro\ui\charts\CandleStickChart.class" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
  if errorlevel 1 exit /b %errorlevel%
)

if not exist "%APP_HOME%target\classes\org\investpro\ui\charts\CandleStickChart$CandlePageConsumer.class" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
  if errorlevel 1 exit /b %errorlevel%
)

if not exist "%APP_HOME%target\lib" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
  if errorlevel 1 exit /b %errorlevel%
)

"%JAVA_HOME%\bin\java.exe" ^
  "-Dinvestpro.ibkr.apiDirectory=%APP_HOME%lib\ibkr" ^
  -cp "%APP_HOME%target\classes;%APP_HOME%target\lib\*" ^
  org.investpro.InvestProLauncher

endlocal
