@echo off
echo ============================================================
echo Starting IP-SAKTI Sahayak (RAG, Backend, Frontend)...
echo ============================================================

REM Free up ports 8000, 8080, 5173 if already occupied
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8000 ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5173 ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1

set ROOT=%~dp0

start "IP-SAKTI [1/3: Python RAG]" cmd /k "cd /d %ROOT%ip-sakti-rag && python -m uvicorn app.api.main:app --host 0.0.0.0 --port 8000 --reload"

start "IP-SAKTI [2/3: Spring Boot Backend]" cmd /k "cd /d %ROOT%ip-sakti-backend && mvnw.cmd spring-boot:run"

start "IP-SAKTI [3/3: React Frontend]" cmd /k "cd /d %ROOT%Frontend && npm run dev"

echo.
echo All 3 services have been launched in separate terminal windows!
echo   - Python RAG:  http://localhost:8000
echo   - Backend API: http://localhost:8080
echo   - Frontend UI: http://localhost:5173/ask
echo ============================================================
