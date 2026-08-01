@echo off
REM TruckMgmt Firebase bootstrap (mirrors SareChild setup-firebase.bat)
REM Prerequisite: firebase login (firebase login --reauth if needed)
cd /d "%~dp0.."

echo === TruckMgmt Firebase setup ===
echo Project: truckmgmt-dev
echo.

where firebase >nul 2>&1
if errorlevel 1 (
  echo ERROR: Firebase CLI not found. Install: npm install -g firebase-tools
  exit /b 1
)

echo Linking project truckmgmt-dev...
firebase use truckmgmt-dev
if errorlevel 1 (
  echo.
  echo Project truckmgmt-dev not found. Create it:
  echo   firebase projects:create truckmgmt-dev --display-name "TruckMgmt Dev"
  exit /b 1
)

echo.
echo Deploying Firestore rules + indexes...
firebase deploy --only firestore:rules,firestore:indexes
if errorlevel 1 exit /b 1

echo.
echo Deploying Storage rules (optional — skipped if Storage not enabled)...
firebase deploy --only storage
if errorlevel 1 (
  echo NOTE: Storage deploy skipped. Media uses Cloudflare R2 via r2-proxy/ — not a blocker.
)

echo.
echo Fetching Android + web SDK configs...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fetch-firebase-config.ps1"
if errorlevel 1 exit /b 1

echo.
echo Optional — Cloud Functions (requires Blaze billing plan):
echo   cd functions ^&^& npm install ^&^& npm run build ^&^& cd .. ^&^& firebase deploy --only functions
echo.
echo Optional — Firebase Hosting for dispatcher-web (GitHub Pages hosts marketing/TCD):
echo   cd dispatcher-web ^&^& npm run build ^&^& cd .. ^&^& firebase deploy --only hosting
echo.
echo Manual console steps (one-time):
echo   1. Authentication: Email/Password + Google + Anonymous (already enabled on Spark)
echo      https://console.firebase.google.com/project/truckmgmt-dev/authentication/providers
echo   2. Google Maps: restrict API keys in Cloud Console
echo   3. Cloudflare: run scripts\setup-cloudflare.bat for R2/D1/KV + Worker
echo   4. GitHub Pages: enable Actions-based Pages in repo settings
echo   5. Optional later — Firebase Storage: console Get Started then re-run storage deploy
echo      https://console.firebase.google.com/project/truckmgmt-dev/storage
echo.
pause
