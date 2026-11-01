param([string[]]$Versions = @(), [string]$JavaHome = '')
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$matrix = Get-Content -LiteralPath (Join-Path $repo 'versions.json') -Raw | ConvertFrom-Json
if ($Versions.Count) {
    foreach ($version in $Versions) {
        if ($version -notin $matrix.minecraft) { throw "Unsupported build target: $version" }
    }
    $matrix = @($matrix | Where-Object { $_.minecraft -in $Versions })
}
Push-Location $repo
try {
    foreach ($target in $matrix) {
        Write-Host "Building NMR for Minecraft $($target.minecraft)"
        $arguments = @(':fabric:build', "-Pmc=$($target.minecraft)", '--console=plain')
        if ($JavaHome) { $arguments += "-Dorg.gradle.java.home=$JavaHome" }
        & .\gradlew.bat @arguments
        if ($LASTEXITCODE -ne 0) { throw "Build failed for $($target.minecraft). No release claim should be made for this target." }
    }
    $releaseVersion = ((Get-Content (Join-Path $repo 'gradle.properties') | Where-Object { $_ -match '^mod_version=' }) -split '=', 2)[1]
    $destination = Join-Path $repo "build/releases/$releaseVersion"
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    $artifacts = foreach ($target in $matrix) {
        $name = "no-more-rat-$($target.minecraft)-$releaseVersion.jar"
        $source = Join-Path $repo "fabric/build/$($target.minecraft)/libs/$name"
        if (!(Test-Path -LiteralPath $source)) { throw "Missing release artifact: $source" }
        Copy-Item -LiteralPath $source -Destination (Join-Path $destination $name) -Force
        Join-Path $destination $name
    }
    $artifacts | ForEach-Object { "$( (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant())  $(Split-Path -Leaf $_)" } |
        Set-Content -LiteralPath (Join-Path $destination 'SHA256SUMS.txt') -Encoding ascii
    if (!$Versions.Count) {
        Compress-Archive -LiteralPath @($artifacts + (Join-Path $destination 'SHA256SUMS.txt')) -DestinationPath (Join-Path $destination "no-more-rat-$releaseVersion-multiversion.zip") -Force
    }
    Write-Host "Verified build artifacts: $destination"
} finally { Pop-Location }
