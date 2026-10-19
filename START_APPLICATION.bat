@echo off
if exist "C:\Program Files\Java\jdk-21" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    set "PATH=C:\Program Files\Java\jdk-21\bin;%PATH%"
)
echo.
echo ========================================
echo   Service Spot Application Starter
echo ========================================
echo.

echo [1] Starting Backend (Port 8080)...
echo.
cd backend
start cmd /k "title Backend - Service Spot & if exist "C:\Program Files\Java\jdk-21" set "JAVA_HOME=C:\Program Files\Java\jdk-21" & if exist "C:\Program Files\Java\jdk-21" set "PATH=C:\Program Files\Java\jdk-21\bin;%PATH%" & mvn spring-boot:run"

echo [2] Waiting for Backend to start (30 seconds)...
timeout /t 30 /nobreak

echo.
echo [3] Starting Frontend (Port 5173)...
echo.
cd ..\frontend
start cmd /k "title Frontend - Service Spot & npm run dev"

echo.
echo ========================================
echo   Application Starting!
echo ========================================
echo.
echo Backend:  http://localhost:8080
echo Frontend: http://localhost:5173
echo.
echo Once frontend loads, navigate to:
echo   1. Login as Customer (or sign up)
echo   2. Click "Book Service"
echo   3. Services will auto-load from backend
echo.
echo Demo credentials:
echo   Admin:
echo     Email: admin@servicespot.com
echo     Password: admin123
echo.
timeout /t 5
