# Compile src/ and test/ and run all JUnit 5 tests. No build tool needed.
#
# Usage:
#   .\test.ps1                         all tests
#   .\test.ps1 --select-class orbitslot.scheduler.SchedulerTest
#                                      any other JUnit console launcher option
#
# If PowerShell blocks the script, run:
#   powershell -ExecutionPolicy Bypass -File test.ps1

[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(ValueFromRemainingArguments = $true)] [string[]]$JUnitArgs
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Same lookup as run.ps1: PATH, then this terminal's JAVA_HOME, then the system one.
$javac = (Get-Command javac -ErrorAction SilentlyContinue).Source
$java  = (Get-Command java  -ErrorAction SilentlyContinue).Source
if (-not $javac) {
    $javaHome = $env:JAVA_HOME
    if (-not $javaHome) { $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine") }
    if (-not $javaHome) { $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User") }
    if ($javaHome) {
        $javac = Join-Path $javaHome "bin\javac.exe"
        $java  = Join-Path $javaHome "bin\java.exe"
    }
}
if (-not $javac -or -not (Test-Path $javac)) {
    Write-Error "javac not found. Install JDK 21 (e.g. winget install EclipseAdoptium.Temurin.21.JDK) and open a new terminal."
}

$ErrorActionPreference = "Continue"

# Tests compile into their own folder, so they never end up in out/ (the app's classes).
if (Test-Path out-test) { Remove-Item -Recurse -Force out-test }
$src = @(Get-ChildItem -Recurse src, test -Filter *.java | ForEach-Object { $_.FullName })

& $javac --release 21 -encoding UTF-8 -d out-test -cp "lib/*" $src
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not $JUnitArgs) { $JUnitArgs = @("--scan-class-path") }
& $java -jar lib/junit-platform-console-standalone-1.11.3.jar execute `
    --class-path "out-test;lib/jackson-databind-2.17.2.jar;lib/jackson-core-2.17.2.jar;lib/jackson-annotations-2.17.2.jar;lib/predict4java-1.3.1.jar;lib/commons-lang-2.6.jar;lib/commons-logging-1.2.jar;lib/guava-mini-0.1.2.jar" `
    --disable-banner --details=tree @JUnitArgs
exit $LASTEXITCODE
