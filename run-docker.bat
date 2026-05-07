@echo off
REM InvestPro Docker Deployment Script for Windows
REM This script automates building and running InvestPro with PostgreSQL and noVNC

setlocal enabledelayedexpansion

:menu
cls
echo.
echo ======================================
echo   InvestPro Docker Management
echo ======================================
echo.
echo 1. Build and Start Services
echo 2. Start Services (without rebuild)
echo 3. Stop Services
echo 4. View Logs
echo 5. View Specific Service Logs
echo 6. Access noVNC Web Interface
echo 7. Connect via VNC Client
echo 8. Database Shell
echo 9. Application Shell
echo 10. Restart All Services
echo 11. Clean Up (stop and remove containers)
echo 12. Exit
echo.
set /p choice=Enter your choice (1-12):

if "%choice%"=="1" goto build_start
if "%choice%"=="2" goto start
if "%choice%"=="3" goto stop
if "%choice%"=="4" goto logs
if "%choice%"=="5" goto logs_specific
if "%choice%"=="6" goto novnc
if "%choice%"=="7" goto vnc_client
if "%choice%"=="8" goto db_shell
if "%choice%"=="9" goto app_shell
if "%choice%"=="10" goto restart
if "%choice%"=="11" goto cleanup
if "%choice%"=="12" goto end
goto menu

:build_start
cls
echo Building and starting services...
docker-compose up --build -d
if errorlevel 1 (
    echo Error during build/start. Check Docker installation and logs.
    pause
    goto menu
)
timeout /t 5
echo Services are starting. Checking status...
docker-compose ps
echo.
echo noVNC Web Interface: http://localhost:6080
echo VNC Direct Connection: localhost:5900
echo Database: localhost:5432
echo.
pause
goto menu

:start
cls
echo Starting services...
docker-compose up -d
timeout /t 3
docker-compose ps
pause
goto menu

:stop
cls
echo Stopping services...
docker-compose stop
echo Services stopped.
pause
goto menu

:logs
cls
echo Showing logs (press Ctrl+C to exit)...
docker-compose logs -f
goto menu

:logs_specific
cls
echo Which service logs?
echo 1. investpro-app
echo 2. postgres
echo 3. novnc
echo.
set /p service=Enter choice (1-3):
if "%service%"=="1" (
    docker-compose logs -f investpro-app
) else if "%service%"=="2" (
    docker-compose logs -f postgres
) else if "%service%"=="3" (
    docker-compose logs -f novnc
) else (
    echo Invalid choice
    pause
)
goto menu

:novnc
cls
echo Opening noVNC web interface...
start http://localhost:6080
pause
goto menu

:vnc_client
cls
echo.
echo To connect via VNC client:
echo - Host: localhost
echo - Port: 5900
echo - Password: investpro
echo.
echo Recommended VNC clients:
echo - RealVNC Viewer (https://www.realvnc.com/en/connect/download/viewer/)
echo - TightVNC (https://www.tightvnc.com/)
echo - UltraVNC (https://www.uvnc.com/)
echo.
pause
goto menu

:db_shell
cls
echo Connecting to PostgreSQL database...
docker exec -it investpro-postgres psql -U investpro -d investpro
goto menu

:app_shell
cls
echo Entering application container...
docker exec -it investpro-app bash
goto menu

:restart
cls
echo Restarting all services...
docker-compose restart
timeout /t 3
docker-compose ps
pause
goto menu

:cleanup
cls
echo WARNING: This will stop and remove all containers!
set /p confirm=Continue? (yes/no):
if /i "%confirm%"=="yes" (
    echo Cleaning up...
    docker-compose down -v
    echo Cleanup complete. All containers and volumes removed.
) else (
    echo Cleanup cancelled.
)
pause
goto menu

:end
cls
echo Exiting...
endlocal
exit /b 0
