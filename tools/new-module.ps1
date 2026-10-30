# Legt aus der Vorlage ModuleTemplate ein neues PeakVerse-Modul an.
# Aufruf:  .\tools\new-module.ps1 -Id texturepeak -Name TexturePeak
#   -Id    Modul-ID: 2-24 Zeichen, a-z 0-9 _, beginnt mit einem Buchstaben (Netzwerk-Kennung, Config-Schluessel, Pack-Namensraum)
#   -Name  Anzeigename und Ordnername, z. B. TexturePeak
param(
    [Parameter(Mandatory)][string]$Id,
    [Parameter(Mandatory)][string]$Name
)
$ErrorActionPreference = 'Stop'
if ($Id -notmatch '^[a-z][a-z0-9_]{1,23}$') { throw "Ungueltige Modul-ID '$Id' (2-24 Zeichen, a-z 0-9 _, mit Buchstabe beginnen)" }
if ($Name -notmatch '^[A-Za-z][A-Za-z0-9]{1,39}$') { throw "Ungueltiger Name '$Name' (nur Buchstaben und Ziffern)" }

$root = Split-Path -Parent $PSScriptRoot
$template = Join-Path $root 'ModuleTemplate'
$target = Join-Path $root $Name
if (Test-Path $target) { throw "$target gibt es schon" }

# Ordner ohne Build-Ergebnisse kopieren
New-Item -ItemType Directory -Path $target | Out-Null
robocopy $template $target /E /XD build .gradle /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "Kopieren fehlgeschlagen (robocopy $LASTEXITCODE)" }

# Ordner und Pakete umbenennen: de.peakverse.example -> de.peakverse.<id>
foreach ($sub in 'src\main\java', 'src\test\java') {
    $old = Join-Path $target "$sub\de\peakverse\example"
    if (Test-Path $old) { Rename-Item $old $Id }
}
$old = Join-Path $target 'src\main\resources\modules\example'
Rename-Item $old $Id

$utf8 = New-Object System.Text.UTF8Encoding($false)
Get-ChildItem $target -Recurse -File | Where-Object { $_.Extension -in '.java', '.kts', '.yml', '.properties', '.PeakModule' } | ForEach-Object {
    $text = [IO.File]::ReadAllText($_.FullName)
    $new = $text.Replace('ExampleModule', "${Name}Module").Replace('de.peakverse.example', "de.peakverse.$Id").Replace('"example"', "`"$Id`"").Replace('modules/example', "modules/$Id").Replace('plugins/Example', "plugins/$Name").Replace('"Example"', "`"$Name`"").Replace('Modul "Example"', "Modul `"$Name`"").Replace('Beispiel-Modul', "Modul $Name")
    if ($new -ne $text) { [IO.File]::WriteAllText($_.FullName, $new, $utf8) }
}
foreach ($f in Get-ChildItem $target -Recurse -File -Filter 'ExampleModule*.java') { Rename-Item $f.FullName ($f.Name.Replace('ExampleModule', "${Name}Module")) }

Write-Host "Modul $Name ($Id) angelegt: $target"
Write-Host @"

Noch drei Handgriffe, damit es im All-in-One-Jar landet:
  1. Bundle\settings.gradle.kts:   includeBuild("../$Name")
  2. Bundle\build.gradle.kts:      implementation("de.peakverse:$Id")      (unter den eingebauten Modulen)
  3. PeakCore\core-plugin\src\main\resources\config.yml:   modules: ${Id}: enabled: true
Dann in $Name bauen (gradlew build) und mit tools\build-all.ps1 das Gesamt-Jar erzeugen.
"@
