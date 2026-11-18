# Compile all sources in src/ and run a main class. No build tool needed.
#
# Usage:
#   .\run.ps1                                   runs orbitslot.Main
#   .\run.ps1 some args                         passes args to orbitslot.Main
#   .\run.ps1 -Main orbitslot.tools.Predict4JavaCheck
#
# If PowerShell blocks the script, run:
#   powershell -ExecutionPolicy Bypass -File run.ps1

[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$Main = "orbitslot.Main",
    [Parameter(ValueFromRemainingArguments = $true)] [string[]]$AppArgs
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Find javac: PATH first, then JAVA_HOME.
$javac = (Get-Command javac -ErrorAction SilentlyContinue).Source
$java  = (Get-Command java  -ErrorAction SilentlyContinue).Source
if (-not $javac -and $env:JAVA_HOME) {
    $javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
    $java  = Join-Path $env:JAVA_HOME "bin\java.exe"
}
if (-not $javac -or -not (Test-Path $javac)) {
    Write-Error "javac not found. Install JDK 21 (e.g. winget install EclipseAdoptium.Temurin.21.JDK) and open a new terminal."
}

if (Test-Path out) { Remove-Item -Recurse -Force out }
$src = Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName }

& $javac --release 21 -encoding UTF-8 -d out -cp "lib/*" $src
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $java -cp "out;lib/*" $Main @AppArgs
exit $LASTEXITCODE
