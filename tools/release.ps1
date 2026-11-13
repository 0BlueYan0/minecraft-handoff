param(
  [Parameter(Mandatory = $true)][string]$Version,
  [switch]$Publish,
  [switch]$AllowDirty,
  [switch]$SkipBuild,
  [string]$JavaHome = 'C:\Users\user\AppData\Roaming\.Triggers Client\runtime\jre-21.0.7-windows-x64'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path -Parent $PSScriptRoot

if ($Version -notmatch '^(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})$') { throw "Version must look like 1.2.3: $Version" }
if ($Publish -and $AllowDirty) { throw '-AllowDirty is for local test packages only and cannot be used with -Publish.' }
if ($Publish -and $SkipBuild) { throw '-SkipBuild is for local test packages only and cannot be used with -Publish.' }

Push-Location $root
try {
  if (-not $AllowDirty) {
    $dirty = git status --porcelain
    if ($dirty) { throw "Working tree is not clean. Commit first, or use -AllowDirty for a local test package.`n$($dirty -join "`n")" }
  }
  $head = (git rev-parse HEAD).Trim()

  if (-not $SkipBuild) {
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw "JavaHome has no bin\java.exe: $JavaHome" }
    $env:JAVA_HOME = $JavaHome
    & (Join-Path $root 'gradlew.bat') ':handoff-service:jar' ':relay-bridge:remapJar' ':viewer-mod:remapJar' --console=plain
    if ($LASTEXITCODE -ne 0) { throw "gradle failed with exit code $LASTEXITCODE" }
  }

  # Loom writes the remapped jar without a classifier and the unmapped one as -dev.
  function Find-Jar([string]$module) {
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $root "$module\build\libs") -Filter "$module-*.jar" |
      Where-Object { $_.Name -notmatch '-(dev|sources|javadoc)\.jar$' } | Sort-Object LastWriteTime -Descending)
    if ($jars.Count -eq 0) { throw "No jar found for $module" }
    return $jars[0].FullName
  }
  $jars = [ordered]@{
    'handoff-service.jar' = Find-Jar 'handoff-service'
    'relay-bridge.jar'    = Find-Jar 'relay-bridge'
    'viewer-mod.jar'      = Find-Jar 'viewer-mod'
  }

  $out = Join-Path $root "build\release\$Version"
  if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Recurse -Force }
  $stage = Join-Path $out 'stage'
  New-Item -ItemType Directory -Path (Join-Path $stage 'lib') | Out-Null
  foreach ($name in $jars.Keys) { Copy-Item -LiteralPath $jars[$name] -Destination (Join-Path $stage "lib\$name") }
  Copy-Item -LiteralPath (Join-Path $root 'delivery\mac') -Destination (Join-Path $stage 'mac') -Recurse
  Copy-Item -LiteralPath (Join-Path $root 'delivery\windows') -Destination (Join-Path $stage 'windows') -Recurse
  Copy-Item -LiteralPath (Join-Path $root 'delivery\README-繁體中文.md') -Destination (Join-Path $stage 'README-繁體中文.md')
  [System.IO.File]::WriteAllText((Join-Path $stage 'VERSION'), "$Version`n", (New-Object System.Text.UTF8Encoding($false)))

  # Shell and batch scripts need LF and CRLF line endings respectively, whatever the checkout used.
  foreach ($f in Get-ChildItem -LiteralPath (Join-Path $stage 'mac') -File) {
    $text = [System.IO.File]::ReadAllText($f.FullName) -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText($f.FullName, $text, (New-Object System.Text.UTF8Encoding($false)))
  }
  foreach ($f in Get-ChildItem -LiteralPath (Join-Path $stage 'windows') -Filter '*.cmd') {
    $text = ([System.IO.File]::ReadAllText($f.FullName) -replace "`r`n", "`n") -replace "`n", "`r`n"
    [System.IO.File]::WriteAllText($f.FullName, $text, (New-Object System.Text.UTF8Encoding($false)))
  }

  # Entries are written with forward slashes (Compress-Archive in Windows PowerShell 5.1 uses backslashes)
  # and the Unix mode of the .command files is stored, so unzip on macOS keeps them executable.
  Add-Type -AssemblyName System.IO.Compression
  $zipPath = Join-Path $out 'minecraft-handoff.zip'
  $fs = [System.IO.File]::Create($zipPath)
  $zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)
  try {
    $stageFull = (Resolve-Path -LiteralPath $stage).Path.TrimEnd('\')
    foreach ($f in Get-ChildItem -LiteralPath $stageFull -Recurse -File | Sort-Object FullName) {
      $rel = $f.FullName.Substring($stageFull.Length + 1).Replace('\', '/')
      $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
      $mode = if ($rel -like 'mac/*.command') { 0x81ED } else { 0x81A4 }   # 0755 or 0644 regular file
      $entry.ExternalAttributes = $mode -shl 16
      $entry.LastWriteTime = [DateTimeOffset]::new(2000, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
      $in = [System.IO.File]::OpenRead($f.FullName)
      $es = $entry.Open()
      try { $in.CopyTo($es) } finally { $es.Dispose(); $in.Dispose() }
    }
  } finally { $zip.Dispose(); $fs.Dispose() }
  Remove-Item -LiteralPath $stage -Recurse -Force

  $hash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
  [System.IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS'), "$hash  minecraft-handoff.zip`n", (New-Object System.Text.UTF8Encoding($false)))
  [System.IO.File]::WriteAllText((Join-Path $out 'VERSION'), "$Version`n", (New-Object System.Text.UTF8Encoding($false)))
  Write-Host "Built $out"
  Get-ChildItem -LiteralPath $out | ForEach-Object { Write-Host ("  {0}  {1} bytes" -f $_.Name, $_.Length) }

  if ($Publish) {
    gh release create "v$Version" (Join-Path $out 'minecraft-handoff.zip') (Join-Path $out 'SHA256SUMS') (Join-Path $out 'VERSION') --target $head --title "v$Version"
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed with exit code $LASTEXITCODE" }
  }
} finally { Pop-Location }
