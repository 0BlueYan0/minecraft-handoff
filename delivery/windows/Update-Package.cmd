@echo off
rem Self-update for the Windows package. Plain cmd on purpose (no powershell.exe on locked-down PCs).
rem Usage: Update-Package.cmd PACKAGE_ROOT
rem Exit code 100 = package was replaced, run the new Start-Viewer.cmd. Any other code = keep going with this version.
setlocal EnableExtensions DisableDelayedExpansion
rem cmd reads a batch file while it runs. This file is replaced during the update, so the real work
rem runs from a temporary copy and this original is never read again after the copy starts.
set "SELF=%TEMP%\handoff-updater-%RANDOM%%RANDOM%.cmd"
rem Started by hand (double-click, or no argument): update the package this file sits in, say when nothing
rem is newer, and keep the window open. Without this a direct run exited at once and printed nothing.
if not defined HANDOFF_UPDATER_COPY (
  set "DIRECT="
  set "ARGROOT=%~1"
  if "%~1"=="" (set "DIRECT=1" & set "ARGROOT=%~dp0..")
)
if not defined HANDOFF_UPDATER_COPY (
  copy /y "%~f0" "%SELF%" >nul 2>&1 && (
    set "HANDOFF_UPDATER_COPY=1"
    call "%SELF%" "%ARGROOT%"
    if errorlevel 100 (del "%SELF%" >nul 2>&1 & (if defined DIRECT pause) & exit /b 100)
    del "%SELF%" >nul 2>&1 & (if defined DIRECT pause) & exit /b 0
  )
  echo Update skipped: could not prepare the updater.
  if defined DIRECT pause
  exit /b 0
)

set "SYS=%SystemRoot%\System32"
set "ROOT=%~1"
if not defined ROOT exit /b 0
for %%I in ("%ROOT%") do set "ROOT=%%~fI"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
if "%HANDOFF_NO_UPDATE%"=="1" exit /b 0
if not defined HANDOFF_UPDATE_BASE set "HANDOFF_UPDATE_BASE=https://github.com/0BlueYan0/minecraft-handoff/releases/latest/download"
set "BASE=%HANDOFF_UPDATE_BASE%"
if "%BASE:~-1%"=="/" set "BASE=%BASE:~0,-1%"
set "BASEOK="
if /i "%BASE:~0,8%"=="https://" set "BASEOK=1"
if /i "%BASE:~0,17%"=="http://127.0.0.1:" set "BASEOK=1"
if /i "%BASE:~0,17%"=="http://127.0.0.1/" set "BASEOK=1"
if not defined BASEOK (echo Update skipped: the update address must start with https://.& exit /b 0)
if not exist "%SYS%\curl.exe" (echo Update skipped: curl.exe is not available on this PC.& exit /b 0)
if not exist "%SYS%\tar.exe" (echo Update skipped: tar.exe is not available on this PC.& exit /b 0)

set "WORK=%TEMP%\handoff-update-%RANDOM%%RANDOM%"
mkdir "%WORK%" >nul 2>&1
if not exist "%WORK%\" (echo Update skipped: could not create a temporary folder.& exit /b 0)
rem Not the package folder, so the folders under it can be replaced even when this was started from inside one.
pushd "%WORK%"

call :readver "%ROOT%\VERSION" LOCAL
if not defined LOCAL set "LOCAL=0.0.0"

"%SYS%\curl.exe" -fsS -L --max-redirs 5 --proto-redir =https --connect-timeout 15 --max-time 15 -o "%WORK%\VERSION" "%BASE%/VERSION" <nul 2>"%WORK%\err.txt"
if errorlevel 1 (call :fail "could not read the latest version" & goto done)
call :readver "%WORK%\VERSION" REMOTE
if not defined REMOTE (call :fail "the latest version is not a version number" & goto done)

call :key "%LOCAL%" LKEY
call :key "%REMOTE%" RKEY
if not defined LKEY (call :fail "installed version is too long to compare" & goto done)
if not defined RKEY (call :fail "latest version is too long to compare" & goto done)
if not "%RKEY%" gtr "%LKEY%" (
  if defined DIRECT echo Minecraft Handoff %LOCAL% is already the latest version.
  goto done
)

echo Updating Minecraft Handoff %LOCAL% to %REMOTE%
echo [1/5] Downloading the checksum list ...
"%SYS%\curl.exe" -fsS -L --max-redirs 5 --proto-redir =https --connect-timeout 15 --max-time 15 -o "%WORK%\SHA256SUMS" "%BASE%/SHA256SUMS" <nul 2>"%WORK%\err.txt"
if errorlevel 1 (call :fail "could not download SHA256SUMS" & goto done)
echo [2/5] Downloading minecraft-handoff.zip ...
rem The zip is large: no total time limit, but the download stops when it stalls for 15 seconds. -# draws a progress bar.
"%SYS%\curl.exe" -fS -# -L --max-redirs 5 --proto-redir =https --connect-timeout 15 --speed-limit 1024 --speed-time 15 -o "%WORK%\minecraft-handoff.zip" "%BASE%/minecraft-handoff.zip" <nul
if errorlevel 1 (call :fail "could not download minecraft-handoff.zip" & goto done)

echo [3/5] Checking the SHA-256 checksum ...
rem findstr only matches line ends in CRLF files, so the hash goes through a file that cmd itself writes.
set "WANT="
set "SUMLINE="
for /f "usebackq tokens=1,2" %%H in ("%WORK%\SHA256SUMS") do if /i "%%I"=="minecraft-handoff.zip" if not defined SUMLINE (
  set "SUMLINE=1"
  >"%WORK%\sum.line" echo %%H
)
if not defined SUMLINE (call :fail "SHA256SUMS has no entry for the zip" & goto done)
"%SYS%\findstr.exe" /x /r "[0-9a-fA-F][0-9a-fA-F]*" "%WORK%\sum.line" >nul 2>&1
if errorlevel 1 (call :fail "SHA256SUMS entry is not a SHA-256 hash" & goto done)
for /f "usebackq delims=" %%V in ("%WORK%\sum.line") do set "WANT=%%V"
if not "%WANT:~63,1%"=="" if "%WANT:~64,1%"=="" goto wantLenOk
call :fail "SHA256SUMS entry is not a SHA-256 hash" & goto done
:wantLenOk

set "GOT="
"%SYS%\certutil.exe" -hashfile "%WORK%\minecraft-handoff.zip" SHA256 > "%WORK%\hash.txt" 2>nul <nul
for /f "usebackq skip=1 delims=" %%H in ("%WORK%\hash.txt") do if not defined GOT set "GOT=%%H"
if not defined GOT (call :fail "could not compute the checksum" & goto done)
set "GOT=%GOT: =%"
if /i not "%GOT%"=="%WANT%" (call :fail "checksum mismatch, download discarded" & goto done)

echo [4/5] Unpacking ...
mkdir "%WORK%\x" >nul 2>&1
"%SYS%\tar.exe" -xf "%WORK%\minecraft-handoff.zip" -C "%WORK%\x" <nul 2>"%WORK%\err.txt"
if errorlevel 1 (call :fail "could not unpack the download" & goto done)
call :readver "%WORK%\x\VERSION" GOTVER
if not defined GOTVER (call :fail "the download has no version file" & goto done)
if not "%GOTVER%"=="%REMOTE%" (call :fail "the download is version %GOTVER%, not %REMOTE%" & goto done)
if not exist "%WORK%\x\lib\" (call :fail "the download has no lib folder" & goto done)
if not exist "%WORK%\x\mac\" (call :fail "the download has no mac folder" & goto done)
if not exist "%WORK%\x\windows\Start-Viewer.cmd" (call :fail "the download has no Start-Viewer.cmd" & goto done)
if not exist "%WORK%\x\README-*.md" (call :fail "the download has no guide" & goto done)

echo [5/5] Replacing the installed files ...
rem Swap in order lib and mac together, guide, windows, VERSION. VERSION goes last so a half-finished update is retried next start.
call :swapboth || (call :fail "could not replace lib and mac, they may be in use" & goto done)
copy /y "%WORK%\x\README-*.md" "%ROOT%\" >nul || (call :fail "could not replace the guide" & goto done)
xcopy "%WORK%\x\windows" "%ROOT%\windows\" /e /i /y /q >nul || (call :fail "could not replace the windows folder" & goto done)
copy /y "%WORK%\x\VERSION" "%ROOT%\VERSION" >nul || (call :fail "could not write VERSION" & goto done)
echo Updated to %REMOTE%.
popd
rmdir /s /q "%WORK%" >nul 2>&1
exit /b 100

:done
popd
rmdir /s /q "%WORK%" >nul 2>&1
exit /b 0

:fail
echo Update skipped: %~1. Continuing with %LOCAL%.
rem curl and tar write their reason here; GeForce NOW only showed the line above, which did not say why.
if exist "%WORK%\err.txt" for %%F in ("%WORK%\err.txt") do if %%~zF gtr 0 type "%WORK%\err.txt"
exit /b 0

:swapboth
rem Moves the old lib and mac aside, moves the new ones in. If any step fails both old folders are put back,
rem so the package never runs a new lib with an old mac script or the other way round.
set "SWFAIL="
for %%D in (lib mac) do if exist "%ROOT%\%%D.old-update" rmdir /s /q "%ROOT%\%%D.old-update" >nul 2>&1
for %%D in (lib mac) do if not defined SWFAIL if exist "%ROOT%\%%D\" (
  move "%ROOT%\%%D" "%ROOT%\%%D.old-update" >nul 2>&1
  if exist "%ROOT%\%%D\" set "SWFAIL=1"
)
if not defined SWFAIL for %%D in (lib mac) do if not defined SWFAIL (
  move "%WORK%\x\%%D" "%ROOT%\%%D" >nul 2>&1
  if not exist "%ROOT%\%%D\" set "SWFAIL=1"
)
if not defined SWFAIL (
  for %%D in (lib mac) do if exist "%ROOT%\%%D.old-update" rmdir /s /q "%ROOT%\%%D.old-update" >nul 2>&1
  exit /b 0
)
for %%D in (lib mac) do if exist "%ROOT%\%%D.old-update\" if exist "%ROOT%\%%D\" if not exist "%WORK%\x\%%D\" move "%ROOT%\%%D" "%WORK%\x\%%D" >nul 2>&1
for %%D in (lib mac) do if exist "%ROOT%\%%D.old-update\" if not exist "%ROOT%\%%D\" move "%ROOT%\%%D.old-update" "%ROOT%\%%D" >nul 2>&1
exit /b 1

:readver
rem %1 = file, %2 = variable. Takes the first line only, and only when it is exactly x.y.z; otherwise the variable stays empty.
set "%~2="
if not exist "%~1" exit /b 0
for %%F in ("%~1") do if %%~zF gtr 64 exit /b 0
rem findstr only matches line ends in CRLF files (GitHub serves this file with LF), so the first line
rem is copied through a file that cmd itself writes before it is checked.
set "FIRST="
for /f "usebackq delims=" %%L in ("%~1") do if not defined FIRST (
  set "FIRST=1"
  >"%~1.line" echo %%L
)
if not defined FIRST exit /b 0
"%SYS%\findstr.exe" /x /r "[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*" "%~1.line" >nul 2>&1
if not errorlevel 1 for /f "usebackq delims=" %%V in ("%~1.line") do set "%~2=%%V"
del "%~1.line" >nul 2>&1
exit /b 0

:key
rem %1 = x.y.z, %2 = variable. Each part is padded to 9 digits so a plain string compare orders versions; empty when a part is longer than 9 digits.
set "%~2="
setlocal EnableDelayedExpansion
set "K="
for /f "tokens=1-3 delims=." %%a in ("%~1") do (
  set "P=000000000%%a" & set "Q=%%a"
  if not "!Q:~9!"=="" exit /b 0
  set "K=!P:~-9!"
  set "P=000000000%%b" & set "Q=%%b"
  if not "!Q:~9!"=="" (set "K=") else set "K=!K!.!P:~-9!"
  set "P=000000000%%c" & set "Q=%%c"
  if not "!Q:~9!"=="" (set "K=") else if defined K set "K=!K!.!P:~-9!"
)
endlocal & set "%~2=%K%"
exit /b 0
