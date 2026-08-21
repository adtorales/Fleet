param(
    [string]$CatalogOciReference,
    [string]$CatalogOciUsername,
    [string]$CatalogOciPassword,
    [switch]$CatalogOciInsecure,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$fleetRoot = Resolve-Path (Join-Path $scriptDirectory "..\..")
$propertiesPath = Join-Path $scriptDirectory "config\registry-local.properties"
$secretsPath = Join-Path $scriptDirectory "config\registry-local-secrets.properties"
$jarPath = Join-Path $scriptDirectory "build\libs\registry-server.jar"

function Read-PropertiesFile([string]$path) {
    $properties = @{}

    if (-not (Test-Path $path)) {
        return $properties
    }

    foreach ($line in Get-Content -Path $path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }

        $key = $trimmed.Substring(0, $separatorIndex).Trim()
        $value = $trimmed.Substring($separatorIndex + 1).Trim()
        $properties[$key] = $value
    }

    return $properties
}

if (-not $SkipBuild) {
    Write-Host "Building Fleet registry launcher..."
    & (Join-Path $fleetRoot "gradlew.bat") ":registry:launcher:shadowJar"
}

if (-not (Test-Path $jarPath)) {
    throw "Registry launcher jar not found: $jarPath"
}

$localSecrets = Read-PropertiesFile $secretsPath

if ([string]::IsNullOrWhiteSpace($CatalogOciReference) -and $localSecrets.ContainsKey("edc.registry.policy.catalog.oci.reference")) {
    $CatalogOciReference = $localSecrets["edc.registry.policy.catalog.oci.reference"]
}

if ([string]::IsNullOrWhiteSpace($CatalogOciUsername) -and $localSecrets.ContainsKey("edc.registry.policy.catalog.oci.username")) {
    $CatalogOciUsername = $localSecrets["edc.registry.policy.catalog.oci.username"]
}

if ([string]::IsNullOrWhiteSpace($CatalogOciPassword) -and $localSecrets.ContainsKey("edc.registry.policy.catalog.oci.password")) {
    $CatalogOciPassword = $localSecrets["edc.registry.policy.catalog.oci.password"]
}

if (-not $CatalogOciInsecure.IsPresent -and $localSecrets.ContainsKey("edc.registry.policy.catalog.oci.insecure")) {
    $CatalogOciInsecure = [System.Management.Automation.SwitchParameter]::new(
        [System.Boolean]::Parse($localSecrets["edc.registry.policy.catalog.oci.insecure"])
    )
}

$javaArgs = @(
    "-Dedc.fs.config=$propertiesPath",
    "-Dedc.participant.id=tap74-local",
    "-Dedc.registry.url=http://localhost:8181/registry"
)

if (-not [string]::IsNullOrWhiteSpace($CatalogOciReference)) {
    $javaArgs += "-Dedc.registry.policy.catalog.oci.reference=$CatalogOciReference"

    if (-not [string]::IsNullOrWhiteSpace($CatalogOciUsername)) {
        $javaArgs += "-Dedc.registry.policy.catalog.oci.username=$CatalogOciUsername"
    }

    if (-not [string]::IsNullOrWhiteSpace($CatalogOciPassword)) {
        $javaArgs += "-Dedc.registry.policy.catalog.oci.password=$CatalogOciPassword"
    }

    if ($CatalogOciInsecure) {
        $javaArgs += "-Dedc.registry.policy.catalog.oci.insecure=true"
    }

    if (Test-Path $secretsPath) {
        Write-Host "Loaded OCI credentials from local secrets file: $secretsPath"
    }

    Write-Host "Starting Fleet registry with OCI catalog: $CatalogOciReference"
} else {
    throw "No OCI catalog reference was configured. Set it in registry-local-secrets.properties or pass -CatalogOciReference."
}

Write-Host "Registry URL: http://localhost:8181/registry"
Write-Host "Reload endpoint: POST http://localhost:8181/registry/reload"

& java @javaArgs -jar $jarPath
