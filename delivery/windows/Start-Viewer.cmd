@echo off
rem Plain cmd on purpose: GeForce NOW and other locked-down PCs have no powershell.exe.
setlocal EnableExtensions DisableDelayedExpansion
rem Windows tools by full path, so a GNU tar or curl earlier in PATH is never picked.
set "SYS=%SystemRoot%\System32"
if not defined LOCALAPPDATA set "LOCALAPPDATA=%USERPROFILE%\AppData\Local"
set "PACKAGE=%~dp0.."
rem Self-update. cmd reads a running batch file as it goes, so the update runs from a copy and the whole
rem check-and-restart is one block. HANDOFF_NO_UPDATE=1 skips it.
if not defined HANDOFF_UPDATED if exist "%~dp0Update-Package.cmd" (
  call "%~dp0Update-Package.cmd" "%PACKAGE%"
  if errorlevel 100 (set "HANDOFF_UPDATED=1" & call "%~dp0Start-Viewer.cmd" %* & exit /b)
)
set "PAIRING="
set "INSTALL="
set "DEFAULT_INSTALL=%APPDATA%\.Triggers Client"
set "VIEWERDIR=%LOCALAPPDATA%\MinecraftHandoff\viewer"
set "JAVA="
set "VANILLA="
set "GENERIC=--generic"
rem Pinned Temurin build. The hash is fixed here so a swapped download is refused.
set "JDK_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip"
set "JDK_SHA256=a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9"
set "JDK_HOME=%LOCALAPPDATA%\MinecraftHandoff\jdk21"

:args
if "%~1"=="" goto argsdone
if /i "%~1"=="-Pairing" goto argPairing
if /i "%~1"=="-Install" goto argInstall
if /i "%~1"=="-ViewerDir" goto argViewerDir
if /i "%~1"=="-Java" goto argJava
if /i "%~1"=="-Vanilla" goto argVanilla
if /i "%~1"=="-NoGeneric" goto argNoGeneric
echo Unknown option: %1
echo Options: -Pairing FILE -Install FOLDER -ViewerDir FOLDER -Java FILE -Vanilla -NoGeneric
goto fail
:argPairing
set "PAIRING=%~2"
shift & shift & goto args
:argInstall
set "INSTALL=%~2"
shift & shift & goto args
:argViewerDir
set "VIEWERDIR=%~2"
shift & shift & goto args
:argJava
set "JAVA=%~2"
shift & shift & goto args
:argVanilla
set "VANILLA=--vanilla"
shift & goto args
:argNoGeneric
set "GENERIC="
shift & goto args
:argsdone

rem ---- Game folder: Enter keeps the default ----
set "TRIES=0"
:askInstall
set /a TRIES+=1
if %TRIES% gtr 5 goto tooMany
if defined INSTALL goto checkInstall
echo.
echo Triggers Client folder (contains versions, runtime and instances).
echo Press Enter for the default: %DEFAULT_INSTALL%
set /p "INSTALL=Folder: "
if not defined INSTALL set "INSTALL=%DEFAULT_INSTALL%"
:checkInstall
set "INSTALL=%INSTALL:"=%"
rem An answer of only quotes leaves INSTALL empty; the substring test below is a syntax error on an empty value.
if not defined INSTALL set "INSTALL=%DEFAULT_INSTALL%"
if "%INSTALL:~-1%"=="\" set "INSTALL=%INSTALL:~0,-1%"
if exist "%INSTALL%\versions\1.21.4\1.21.4.json" goto installOk
echo Not a Triggers Client folder: "%INSTALL%"
echo It must contain versions\1.21.4\1.21.4.json
set "INSTALL="
goto askInstall
:installOk

rem ---- Java 21 with jdk.attach ----
if not defined JAVA goto findJava
call :checkJava "%JAVA%"
if not errorlevel 1 goto javaOk
echo -Java is not a Java 21 JDK: "%JAVA%"
set "JAVA="
:findJava
call :tryJava "%INSTALL%\runtime\jre-21.0.7-windows-x64\bin\java.exe"
if defined JAVA goto javaOk
call :tryJava "%JDK_HOME%\bin\java.exe"
if defined JAVA goto javaOk
if defined JAVA_HOME call :tryJava "%JAVA_HOME%\bin\java.exe"
if defined JAVA goto javaOk
rem for /f over a command drains redirected stdin, so the command output goes through a temp file.
set "FOUND=%TEMP%\handoff-java-where-%RANDOM%.txt"
"%SYS%\where.exe" java.exe > "%FOUND%" 2>nul <nul
for /f "usebackq delims=" %%J in ("%FOUND%") do if not defined JAVA call :tryJava "%%J"
del "%FOUND%" >nul 2>&1
if defined JAVA goto javaOk
echo.
echo Java 21 not found. Downloading Eclipse Temurin JDK 21.0.13 (about 205 MB) to "%JDK_HOME%".
call :downloadJdk
call :tryJava "%JDK_HOME%\bin\java.exe"
if defined JAVA goto javaOk
echo Automatic Java download failed.
set "TRIES=0"
:askJava
set /a TRIES+=1
if %TRIES% gtr 5 goto tooMany
echo.
set /p "JAVA=Full path of a Java 21 java.exe: "
if not defined JAVA goto askJava
set "JAVA=%JAVA:"=%"
if not defined JAVA goto askJava
call :checkJava "%JAVA%"
if not errorlevel 1 goto javaOk
echo Not a Java 21 JDK with jdk.attach: "%JAVA%"
set "JAVA="
goto askJava
:javaOk
echo Java: "%JAVA%"

rem ---- Pairing file ----
set "TRIES=0"
:askPairing
set /a TRIES+=1
if %TRIES% gtr 5 goto tooMany
if defined PAIRING goto checkPairing
echo.
set /p "PAIRING=Path of pairing.json transferred privately from Mac: "
if not defined PAIRING goto askPairing
:checkPairing
set "PAIRING=%PAIRING:"=%"
if not defined PAIRING goto askPairing
if exist "%PAIRING%" if not exist "%PAIRING%\" goto pairingOk
echo Pairing file not found: "%PAIRING%"
set "PAIRING="
goto askPairing
:pairingOk

set "JAVA_TOOL_OPTIONS="
set "JDK_JAVA_OPTIONS="
set "_JAVA_OPTIONS="
echo.
echo Windows launches a private local viewer. Do not start a second SAO login.
"%JAVA%" --add-modules jdk.attach -jar "%PACKAGE%\lib\handoff-service.jar" viewer --pair "%PAIRING%" --install "%INSTALL%" --viewer-dir "%VIEWERDIR%" --guard "%PACKAGE%\lib\viewer-mod.jar" %VANILLA% %GENERIC%
if errorlevel 1 goto failJava
pause
exit /b 0

:tooMany
echo Too many invalid answers, stopping.
goto fail

:failJava
echo Handoff viewer exited with code %ERRORLEVEL%. See launch-console.log and logs\latest.log in "%VIEWERDIR%".
:fail
pause
exit /b 1

rem ---- Subroutines ----
:tryJava
rem Sets JAVA when %1 is a usable Java 21 JDK.
call :checkJava "%~1"
if not errorlevel 1 set "JAVA=%~1"
exit /b 0

:checkJava
rem errorlevel 0 when %1 runs, reports java.base@21 and has the jdk.attach module the viewer needs.
rem Only run files named java.exe, so a mistyped path never starts some other program.
if /i not "%~nx1"=="java.exe" exit /b 1
if not exist "%~1" exit /b 1
set "MODULES=%TEMP%\handoff-java-modules-%RANDOM%.txt"
"%~1" --list-modules < nul > "%MODULES%" 2>nul
if errorlevel 1 goto checkJavaNo
"%SYS%\findstr.exe" /b /c:"java.base@21" "%MODULES%" >nul || goto checkJavaNo
"%SYS%\findstr.exe" /b /c:"jdk.attach@" "%MODULES%" >nul || goto checkJavaNo
del "%MODULES%" >nul 2>&1
exit /b 0
:checkJavaNo
del "%MODULES%" >nul 2>&1
exit /b 1

:downloadJdk
set "WORK=%LOCALAPPDATA%\MinecraftHandoff\jdk21-download"
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%" || exit /b 1
if not exist "%SYS%\curl.exe" (echo curl.exe is not available on this PC.& exit /b 1)
if not exist "%SYS%\tar.exe" (echo tar.exe is not available on this PC.& exit /b 1)
"%SYS%\curl.exe" -fL --retry 3 -o "%WORK%\jdk.zip" "%JDK_URL%" <nul
if errorlevel 1 (echo Download failed.& exit /b 1)
set "GOT="
"%SYS%\certutil.exe" -hashfile "%WORK%\jdk.zip" SHA256 > "%WORK%\hash.txt" 2>nul <nul
for /f "usebackq skip=1 delims=" %%H in ("%WORK%\hash.txt") do if not defined GOT set "GOT=%%H"
if not defined GOT (echo Could not compute the download checksum.& exit /b 1)
set "GOT=%GOT: =%"
if /i not "%GOT%"=="%JDK_SHA256%" (echo Checksum mismatch, download discarded.& rmdir /s /q "%WORK%"& exit /b 1)
mkdir "%WORK%\x" || exit /b 1
"%SYS%\tar.exe" -xf "%WORK%\jdk.zip" -C "%WORK%\x" <nul
if errorlevel 1 (echo Unpacking failed.& exit /b 1)
if not exist "%WORK%\x\jdk-21.0.13+9\bin\java.exe" (echo Unexpected archive layout.& exit /b 1)
if exist "%JDK_HOME%" rmdir /s /q "%JDK_HOME%"
move "%WORK%\x\jdk-21.0.13+9" "%JDK_HOME%" >nul || exit /b 1
rmdir /s /q "%WORK%"
exit /b 0
