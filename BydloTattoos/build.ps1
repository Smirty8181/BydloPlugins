# Build BydloTattoos against local paper-api + SkinsRestorer
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRoot = Split-Path -Parent $root
$src = Join-Path $root 'src\main\java'
$res = Join-Path $root 'src\main\resources'
$out = Join-Path $root 'build\classes'
$dist = Join-Path $root 'build\libs'
$api = Join-Path $serverRoot '_build_libs\paper-api.jar'
$sr = Join-Path $serverRoot 'plugins\SkinsRestorer.jar'

if (-not (Test-Path -LiteralPath $api)) { throw "Missing paper-api.jar at $api" }
if (-not (Test-Path -LiteralPath $sr)) { throw "Missing SkinsRestorer.jar" }

Remove-Item -Recurse -Force (Join-Path $root 'build') -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$cp = @($api, $sr)
Get-ChildItem -LiteralPath (Join-Path $serverRoot '_build_libs') -Filter '*.jar' -ErrorAction SilentlyContinue | ForEach-Object { $cp += $_.FullName }
$classpath = ($cp -join ';')

$files = Get-ChildItem -LiteralPath $src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
Write-Host ("Compiling {0} sources..." -f $files.Count)

& javac --release 21 -encoding UTF-8 -cp $classpath -d $out @files
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item -Recurse -Force (Join-Path $res '*') $out
$jar = Join-Path $dist 'BydloTattoos.jar'
if (Test-Path -LiteralPath $jar) { Remove-Item -LiteralPath $jar -Force }

Push-Location $out
& jar cf $jar *
Pop-Location

$dest = Join-Path $serverRoot 'plugins\BydloTattoos.jar'
Copy-Item -Force -LiteralPath $jar -Destination $dest
Write-Host "OK -> $jar"
Write-Host "Copied to $dest"
