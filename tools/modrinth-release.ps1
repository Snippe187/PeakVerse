# Modrinth-Release je Ziel: Version + Changelog aus dem CHANGELOG.md, Jar aus dist\.
# Standard ist ein TROCKENLAUF: es wird nur gezeigt, was hochgeladen wuerde. Hochgeladen wird nur mit -Upload UND dem Token in der
# Umgebungsvariable MODRINTH_TOKEN (nie in eine Datei schreiben, nie ins Chat-Fenster einfuegen).
#
# Aufruf:  .\tools\modrinth-release.ps1 -Target peakcore-plugin -Version 0.1.0              (Trockenlauf)
#          .\tools\modrinth-release.ps1 -Target all -Version 0.1.0                           (Trockenlauf fuer alle)
#          $env:MODRINTH_TOKEN = '...'; .\tools\modrinth-release.ps1 -Target peaksound-mod -Version 0.1.0 -Upload
# Die Projekt-IDs stehen in tools\modrinth.json (leer = Ziel wird uebersprungen; ID steht auf der Modrinth-Projektseite unter "..." > Copy ID).
param(
    [Parameter(Mandatory)][string]$Target,
    [Parameter(Mandatory)][string]$Version,
    [switch]$Upload
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$config = Get-Content (Join-Path $PSScriptRoot 'modrinth.json') -Raw -Encoding UTF8 | ConvertFrom-Json

if ($Upload -and [string]::IsNullOrWhiteSpace($env:MODRINTH_TOKEN)) {
    throw 'Upload verlangt den Token in der Umgebungsvariable MODRINTH_TOKEN ($env:MODRINTH_TOKEN = ...).'
}

# Der Abschnitt "## <version>" aus dem Changelog (bis zur naechsten "## ")
function Get-Changelog([string]$file, [string]$version) {
    $lines = Get-Content (Join-Path $root $file) -Encoding UTF8
    $out = New-Object System.Collections.Generic.List[string]
    $in = $false
    foreach ($l in $lines) {
        if ($l -match '^##\s+(\S+)') {
            if ($in) { break }
            if ($Matches[1] -eq $version) { $in = $true; continue }
        } elseif ($in) { $out.Add($l) }
    }
    if (-not $in) { throw "Im $file gibt es keinen Abschnitt '## $version'." }
    ($out -join "`n").Trim()
}

$names = if ($Target -eq 'all') { @($config.targets.PSObject.Properties.Name) } else { @($Target) }
foreach ($name in $names) {
    $t = $config.targets.$name
    if (-not $t) { throw "Unbekanntes Ziel '$name' (siehe tools\modrinth.json)." }
    $jar = Join-Path $root ($t.jar -replace '\{version\}', $Version)
    if (-not (Test-Path $jar)) { throw "$name`: Jar fehlt: $jar (zuerst .\tools\build-all.ps1)" }
    $log = Get-Changelog $t.changelog $Version
    if ([string]::IsNullOrWhiteSpace($log)) { throw "$name`: Der Changelog-Abschnitt ist leer." }
    $data = [ordered]@{
        name           = ($t.name -replace '\{version\}', $Version)
        version_number = $Version
        changelog      = $log
        dependencies   = @()
        game_versions  = @($config.gameVersion)
        version_type   = $config.versionType
        loaders        = @($t.loaders)
        featured       = $false
        project_id     = $t.projectId
        file_parts     = @('file')
        primary_file   = 'file'
    }
    $hash = (Get-FileHash $jar -Algorithm SHA512).Hash.ToLower()
    Write-Host "== $name" -ForegroundColor Cyan
    Write-Host ("  Datei:     {0} ({1:N0} Bytes)" -f $jar, (Get-Item $jar).Length)
    Write-Host "  SHA512:    $($hash.Substring(0, 16))..."
    Write-Host "  Version:   $Version ($($config.versionType)), Spiel $($config.gameVersion), Loader $($t.loaders -join ', ')"
    Write-Host "  Changelog:`n$(($log -split "`n" | ForEach-Object { '    ' + $_ }) -join "`n")"
    if ([string]::IsNullOrWhiteSpace($t.projectId)) {
        Write-Host "  UEBERSPRUNGEN: projectId fehlt in tools\modrinth.json" -ForegroundColor Yellow
        continue
    }
    if (-not $Upload) {
        Write-Host '  TROCKENLAUF: nichts hochgeladen (zum Hochladen -Upload).' -ForegroundColor Green
        continue
    }
    $tmp = Join-Path ([IO.Path]::GetTempPath()) "modrinth-$name.json"
    [IO.File]::WriteAllText($tmp, ($data | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
    try {
        # curl.exe (in Windows enthalten) kann multipart; der Token steht nur im Header dieses einen Aufrufs
        $result = & curl.exe -sS -w "`n%{http_code}" -X POST 'https://api.modrinth.com/v2/version' -H "Authorization: $env:MODRINTH_TOKEN" -H "User-Agent: $($config.userAgent)" -F "data=@$tmp;type=application/json" -F "file=@$jar"
        $code = ($result | Select-Object -Last 1)
        if ($code -ne '200') { throw "Modrinth antwortet mit $code`: $(($result | Select-Object -SkipLast 1) -join ' ')" }
        Write-Host '  HOCHGELADEN.' -ForegroundColor Green
    } finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}
