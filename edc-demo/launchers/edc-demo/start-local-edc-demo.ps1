param(
    [string]$ConfigFile = ".\config\edc-demo.local.properties",
    [switch]$SkipBuild,
    [string]$TxRepoPath = "",
    [string]$FleetPath = ""
)

$ErrorActionPreference = "Stop"

$launcherDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $launcherDir "..\..")
$configCandidate = if ([System.IO.Path]::IsPathRooted($ConfigFile)) {
    $ConfigFile
} else {
    Join-Path $launcherDir $ConfigFile
}
$configPath = Resolve-Path $configCandidate
$jarPath = Join-Path $launcherDir "build\libs\edc-demo.jar"

if (-not $SkipBuild) {
    if ([string]::IsNullOrWhiteSpace($TxRepoPath)) {
        throw "Building requires -TxRepoPath pointing to a local tractusx-edc checkout. Otherwise run with -SkipBuild and use a prebuilt jar."
    }
    if ([string]::IsNullOrWhiteSpace($FleetPath)) {
        throw "Building requires -FleetPath pointing to a local Fleet checkout so the reconciler modules can be assembled through a composite build."
    }

    $txRepo = Resolve-Path $TxRepoPath
    $fleetRepo = Resolve-Path $FleetPath
    Write-Host "Building edc-demo launcher..." -ForegroundColor Cyan
    & (Join-Path $txRepo "gradlew.bat") `
        "-PuseLocalFleetBuild=true" `
        "-PfleetLocalPath=$fleetRepo" `
        "-PuseLocalTxBuild=true" `
        "-PtxLocalPath=$txRepo" `
        -p $repoRoot `
        :launchers:edc-demo:shadowJar
}

if (-not (Test-Path $jarPath)) {
    throw "Launcher jar not found: $jarPath"
}

Write-Host "Starting edc-demo with config $configPath" -ForegroundColor Cyan
& java "-Dedc.fs.config=$configPath" "-jar" $jarPath
