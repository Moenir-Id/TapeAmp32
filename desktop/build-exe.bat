@echo off
cd /d "%~dp0"
call _jdk.bat
call gradlew.bat packageExe
if errorlevel 1 (
  echo.
  echo Build GAGAL. Kirim pesan error di atas.
  pause
  exit /b 1
)
echo.
echo Selesai. Installer ada di build\compose\binaries\main\exe
explorer "build\compose\binaries\main\exe"
pause
