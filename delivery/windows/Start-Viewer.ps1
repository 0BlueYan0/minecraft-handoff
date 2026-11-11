param(
  [string]$Pairing,
  [string]$Install = "$env:APPDATA\.Triggers Client",
  [string]$ViewerDir = "$env:LOCALAPPDATA\MinecraftHandoff\viewer",
  [string]$Java,
  [switch]$Vanilla
)
$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $PSScriptRoot
if (-not $Java) {
  $bundledJava = Join-Path $Install 'runtime\jre-21.0.7-windows-x64\bin\java.exe'
  if (Test-Path -LiteralPath $bundledJava) { $Java = $bundledJava }
  elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { $Java = Join-Path $env:JAVA_HOME 'bin\java.exe' }
  else { $Java = (Get-Command java.exe -ErrorAction Stop).Source }
}
if (-not $Pairing) { $Pairing = Read-Host 'Path of pairing.json transferred privately from Mac' }
$Pairing = $Pairing.Trim('"')
if (-not (Test-Path -LiteralPath $Pairing -PathType Leaf)) { throw 'Pairing file not found.' }
Remove-Item Env:JAVA_TOOL_OPTIONS,Env:JDK_JAVA_OPTIONS,Env:_JAVA_OPTIONS -ErrorAction SilentlyContinue
$launchArgs = @('--add-modules','jdk.attach','-jar',(Join-Path $packageRoot 'lib\handoff-service-0.2.0.jar'),'viewer',
  '--pair',$Pairing,'--install',$Install,'--viewer-dir',$ViewerDir,'--guard',(Join-Path $packageRoot 'lib\viewer-mod-0.2.0.jar'))
if ($Vanilla) { $launchArgs += '--vanilla' }
Write-Host 'Windows launches a private local viewer. Do not start a second SAO login.'
& $Java @launchArgs
if ($LASTEXITCODE -ne 0) { throw "Handoff viewer exited with code $LASTEXITCODE. See the guide and viewer logs." }
