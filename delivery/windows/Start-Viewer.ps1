param(
  [string]$Pairing,
  [string]$Install = "$env:APPDATA\.Triggers Client",
  [string]$ViewerDir = "$env:LOCALAPPDATA\MinecraftHandoff\viewer",
  [string]$Java,
  [switch]$Vanilla,
  [switch]$NoGeneric
)
$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path -Parent $PSScriptRoot
# Self-update through the same script Start-Viewer.cmd uses; HANDOFF_NO_UPDATE=1 skips it.
if (-not $env:HANDOFF_UPDATED -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'Update-Package.cmd'))) {
  $updater = Join-Path $PSScriptRoot 'Update-Package.cmd'
  # PowerShell quotes each argument that has spaces, so paths with spaces reach cmd intact.
  & $env:ComSpec /d /c call $updater $packageRoot
  if ($LASTEXITCODE -eq 100) {
    $env:HANDOFF_UPDATED = '1'
    & (Join-Path $PSScriptRoot 'Start-Viewer.ps1') @PSBoundParameters
    exit $LASTEXITCODE
  }
}
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
$launchArgs = @('--add-modules','jdk.attach','-jar',(Join-Path $packageRoot 'lib\handoff-service.jar'),'viewer',
  '--pair',$Pairing,'--install',$Install,'--viewer-dir',$ViewerDir,'--guard',(Join-Path $packageRoot 'lib\viewer-mod.jar'))
if ($Vanilla) { $launchArgs += '--vanilla' }
# Packet forwarding is on by default; pass -NoGeneric to turn it off.
if (-not $NoGeneric) { $launchArgs += '--generic' }
Write-Host 'Windows launches a private local viewer. Do not start a second SAO login.'
& $Java @launchArgs
if ($LASTEXITCODE -ne 0) { throw "Handoff viewer exited with code $LASTEXITCODE. See the guide and viewer logs." }
