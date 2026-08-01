@echo off
REM TruckMgmt Cloudflare bootstrap (R2 + D1 + KV + r2-proxy Worker)
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-cloudflare.ps1"
if errorlevel 1 exit /b 1
pause
