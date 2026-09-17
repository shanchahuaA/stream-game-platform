@echo off
REM Launcher for the game-finder shopping assistant microservice (Windows).
REM Kept ASCII-only on purpose: a batch file containing non-ASCII bytes is
REM mis-parsed by cmd.exe, which splits lines in the wrong place.
REM See README.md for the Chinese instructions.

echo ================================================
echo   Game Shopping Assistant (Python + FastAPI)
echo ================================================
echo.

cd /d "%~dp0game-finder"
echo [Check] Working directory: %cd%
echo.

echo [Check] Python version ...
python --version
if errorlevel 1 (
    echo ERROR: python not found. Install Python 3.8+ and add it to PATH.
    pause
    exit /b 1
)
echo.

if not exist ".env" (
    echo ERROR: game-finder\.env is missing.
    echo        Copy .env.example to .env in the same folder, fill in
    echo        DEEPSEEK_API_KEY, and check that STREAM_BASE_URL points
    echo        at the running Java app. Then run this script again.
    echo.
    pause
    exit /b 1
)

echo [1/2] Installing dependencies ...
python -m pip install -r requirements.txt
if errorlevel 1 (
    echo ERROR: dependency installation failed. See the messages above.
    pause
    exit /b 1
)
echo.

echo [2/2] Starting the shopping assistant (port: GAME_FINDER_PORT in .env, default 8090) ...
echo       Keep this window open while the service is running.
echo.
python main.py

echo.
echo Service stopped.
pause