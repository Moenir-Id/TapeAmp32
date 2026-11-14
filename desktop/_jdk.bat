@echo off
rem Cari JDK 17-24 (Gradle 8.14 TIDAK bisa jalan di JDK 25) lalu set JAVA_HOME untuk sesi ini.
set "JDK="
for %%D in ("C:\Program Files\Java\jdk-24" "C:\Program Files\Java\jdk-21") do (
  if not defined JDK if exist "%%~D\bin\java.exe" set "JDK=%%~D"
)
if not defined JDK for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*" "C:\Program Files\Java\jdk-2*" "C:\Program Files\Eclipse Adoptium\jdk-17*" "C:\Program Files\Java\jdk-17*") do (
  if not defined JDK if exist "%%~D\bin\java.exe" set "JDK=%%~D"
)
if defined JDK (
  set "JAVA_HOME=%JDK%"
  set "PATH=%JDK%\bin;%PATH%"
  echo Memakai JDK: %JDK%
) else (
  echo [PERINGATAN] JDK 17-24 tidak ditemukan. Memakai JAVA_HOME bawaan: %JAVA_HOME%
  echo Kalau gagal dengan pesan "25.0.x", pasang JDK 21 atau 24 lalu jalankan lagi.
)
