# Build BydloPrefixes against local paper-api + PlaceholderAPI
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRoot = Split-Path -Parent $root
$src = Join-Path $root 'src\main\java'
$res = Join-Path $root 'src\main\resources'
$out = Join-Path $root 'build\classes'
$dist = Join-Path $root 'build\libs'
$api = Join-Path $serverRoot '_build_libs\paper-api.jar'

if (-not (Test-Path -LiteralPath $api)) { throw "Missing paper-api.jar at $api" }

Remove-Item -Recurse -Force (Join-Path $root 'build') -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$cp = @($api)
Get-ChildItem -LiteralPath (Join-Path $serverRoot '_build_libs') -Filter '*.jar' -ErrorAction SilentlyContinue | ForEach-Object { $cp += $_.FullName }
$classpath = ($cp -join ';')

$files = Get-ChildItem -LiteralPath $src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
Write-Host ("Compiling {0} sources..." -f $files.Count)

& javac --release 21 -encoding UTF-8 -cp $classpath -d $out @files
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item -Recurse -Force (Join-Path $res '*') $out
$jar = Join-Path $dist 'BydloPrefixes.jar'
if (Test-Path -LiteralPath $jar) { Remove-Item -LiteralPath $jar -Force }

Push-Location $out
& jar cf $jar *
Pop-Location

$dest = Join-Path $serverRoot 'plugins\BydloPrefixes.jar'
Copy-Item -Force -LiteralPath $jar -Destination $dest
Write-Host "OK -> $jar"
Write-Host "Copied to $dest"
