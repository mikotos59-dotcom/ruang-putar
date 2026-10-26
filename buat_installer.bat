@echo off
setlocal
cd /d "%~dp0"
title Membuat installer Ruang Putar

echo === 1/3  Menyiapkan bahan (JDK 17 + libmpv) ===
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ambil_bahan.ps1"
if errorlevel 1 goto gagal

set "JAVA_HOME=%~dp0bahan\jdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo.
echo === 2/3  Membangun aplikasi (pertama kali agak lama) ===
call gradlew.bat createDistributable
if errorlevel 1 goto gagal

echo.
echo === 3/3  Membuat installer (Inno Setup 6) ===
set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" (
  echo Inno Setup 6 belum terpasang: https://jrsoftware.org/isdl.php
  echo Aplikasi tanpa installer sudah ada di build\compose\binaries\main\app\RuangPutar
  goto selesai
)
for /f "tokens=2 delims==" %%v in ('findstr /b "versiApp=" gradle.properties') do set "VERSI=%%v"
"%ISCC%" /DVersiApp=%VERSI% "/DSumber=%~dp0build\compose\binaries\main\app\RuangPutar" installer\ruangputar.iss
if errorlevel 1 goto gagal
echo.
echo Selesai: dist\RuangPutar-%VERSI%-setup.exe
goto selesai

:gagal
echo.
echo *** Gagal. Salin pesan di atas dan kirim ke Claude. ***

:selesai
pause
