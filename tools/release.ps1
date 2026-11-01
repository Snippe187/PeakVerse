# Baut eine PeakVerse-Version und legt sie als ENTWURF im oeffentlichen Release-Repo an. Veroeffentlichen (Klick auf "Publish release" bei GitHub) machst du selbst:
# erst dann sehen die Server das Update.
#
# Aufruf:  .\tools\release.ps1                      (bauen mit Tests, Dateien in dist\release\<Version> ablegen, nichts hochladen)
#          .\tools\release.ps1 -Upload              (zusaetzlich als Entwurf zu GitHub hochladen)
#          .\tools\release.ps1 -SkipBuild           (nur aus dem vorhandenen dist\ zusammenstellen)
# Dateien im Release: PeakCore-<V>.jar (Server-Plugin mit allen Modulen), PeakCore-Mod-<V>.jar, PeakSound-Mod-<V>.jar und je Datei eine .sha256 (SHA-256).
param(
    [string]$Repo = 'Snippe187/PeakVerse-Releases',
    [switch]$Upload,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$version = ((Get-Content (Join-Path $root 'Bundle\gradle.properties') | Where-Object { $_ -match '^bundle_version=' }) -split '=')[1].Trim()
if ($version -notmatch '^\d+\.\d+\.\d+([-.][0-9A-Za-z.]+)?$') { throw "Ungueltige Version '$version' in Bundle\gradle.properties" }
$out = Join-Path $root "dist\release\$version"

if (-not $SkipBuild) {
    Write-Host "== Bauen mit Tests" -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot 'build-all.ps1')
    if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) { throw 'Build fehlgeschlagen' }
}

$files = [ordered]@{
    "PeakCore-$version.jar"      = Join-Path $root "dist\PeakCore\Plugin\PeakCore-$version.jar"
    "PeakCore-Mod-$version.jar"  = Join-Path $root "dist\PeakCore\Mod\PeakCore-$version.jar"
    "PeakSound-Mod-$version.jar" = Join-Path $root "dist\PeakSound\Mod\PeakSound-$version.jar"
}
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null
$assets = @()
foreach ($name in $files.Keys) {
    $src = $files[$name]
    if (-not (Test-Path $src)) { throw "Fehlt: $src (zuerst bauen, ohne -SkipBuild)" }
    Copy-Item $src (Join-Path $out $name)
    $hash = (Get-FileHash (Join-Path $out $name) -Algorithm SHA256).Hash.ToLower()
    [IO.File]::WriteAllText((Join-Path $out "$name.sha256"), "$hash  $name`n", (New-Object Text.UTF8Encoding($false)))
    $assets += (Join-Path $out $name), (Join-Path $out "$name.sha256")
    Write-Host ("  {0}  {1:N0} Bytes  {2}" -f $name, (Get-Item (Join-Path $out $name)).Length, $hash.Substring(0, 16))
}

# Beschreibung: der Abschnitt der Version aus den Changelogs
function Get-Section([string]$file, [string]$version) {
    if (-not (Test-Path $file)) { return '' }
    $lines = Get-Content $file -Encoding UTF8
    $res = New-Object System.Collections.Generic.List[string]; $in = $false
    foreach ($l in $lines) {
        if ($l -match '^##\s+(\S+)') { if ($in) { break }; if ($Matches[1] -eq $version) { $in = $true; continue } }
        elseif ($in) { $res.Add($l) }
    }
    ($res -join "`n").Trim()
}
$notes = "# PeakVerse $version`n`n"
$notes += "Dateien:`n- **PeakCore-$version.jar**: Server-Plugin (Paper, Minecraft 26.2) mit allen Modulen: PeakSound, TexturePeak, PeakBoard`n- **PeakCore-Mod-$version.jar** und **PeakSound-Mod-$version.jar**: Fabric-Mods fuer Spieler (PeakSound braucht PeakCore und die Fabric API)`n- jede Datei mit einer .sha256-Pruefsumme`n`n"
$notes += "Update auf dem Server: in der PeakCore-config.yml ``update.enabled: true`` setzen; die Web-Oberflaeche zeigt dann diese Version an.`n`n"
foreach ($m in 'PeakCore', 'PeakSound', 'TexturePeak', 'PeakBoard') {
    $s = Get-Section (Join-Path $root "$m\CHANGELOG.md") $version
    if ($s) { $notes += "## $m`n$s`n`n" }
}
$notesFile = Join-Path $out 'RELEASE-NOTES.md'
[IO.File]::WriteAllText($notesFile, $notes, (New-Object Text.UTF8Encoding($false)))
Write-Host "Dateien in $out" -ForegroundColor Green

if (-not $Upload) { Write-Host 'Nichts hochgeladen (zum Hochladen als Entwurf: -Upload).' -ForegroundColor Yellow; return }
gh repo view $Repo --json name | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Das Repo $Repo gibt es nicht (oeffentlich anlegen, siehe docs/ANLEITUNG.md)." }
gh release create "v$version" --repo $Repo --draft --title "PeakVerse $version" --notes-file $notesFile @assets
if ($LASTEXITCODE -ne 0) { throw 'Release konnte nicht angelegt werden.' }
Write-Host "Entwurf angelegt. Zum Freigeben bei GitHub oeffnen: https://github.com/$Repo/releases und 'Publish release' klicken." -ForegroundColor Green
