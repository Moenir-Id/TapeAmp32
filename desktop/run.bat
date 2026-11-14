@echo off
cd /d "%~dp0"
call _jdk.bat
call gradlew.bat run
pause
