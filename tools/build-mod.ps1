# Baut die EINE Client-Mod "PeakVerse": ein Fabric-Jar, das PeakCore-Mod und PeakSound-Mod als Jar-in-Jar enthaelt (Fabric laedt beide daraus).
# Voraussetzung: tools\build-all.ps1 hat dist\PeakCore\Mod und dist\PeakSound\Mod gebaut (build-all ruft dieses Skript am Ende selbst auf).
# Ergebnis: dist\PeakVerse\Mod\PeakVerse-<Version>.jar
param([string]$Version = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Version) { $Version = ((Get-Content (Join-Path $root 'Bundle\gradle.properties') | Where-Object { $_ -match '^bundle_version=' }) -split '=')[1].Trim() }
$core = Join-Path $root "dist\PeakCore\Mod\PeakCore-$Version.jar"
$sound = Join-Path $root "dist\PeakSound\Mod\PeakSound-$Version.jar"
foreach ($j in $core, $sound) { if (-not (Test-Path $j)) { throw "Fehlt: $j (zuerst tools\build-all.ps1)" } }
$outDir = Join-Path $root 'dist\PeakVerse\Mod'
New-Item -ItemType Directory -Force $outDir | Out-Null
$out = Join-Path $outDir "PeakVerse-$Version.jar"
if (Test-Path $out) { Remove-Item $out -Force }

$meta = [ordered]@{
    schemaVersion = 1
    id            = 'peakverse'
    version       = $Version
    name          = 'PeakVerse'
    description   = 'All-in-one client mod for the PeakVerse server plugin (PeakCore): PeakSound music and audio, custom textures and more. Needs the PeakCore plugin on the server.'
    authors       = @('PeakVerse contributors')
    license       = 'GPL-3.0-only'
    environment   = 'client'
    depends       = [ordered]@{ fabricloader = '>=0.20.1'; minecraft = '~26.2'; java = '>=25'; 'fabric-api' = '*' }
    jars          = @(@{ file = "META-INF/jars/PeakCore-$Version.jar" }, @{ file = "META-INF/jars/PeakSound-$Version.jar" })
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open($out, 'Create')
try {
    $fixed = [DateTimeOffset]::new(2026, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
    function Add-Entry($name, [byte[]]$bytes) {
        $e = $zip.CreateEntry($name, 'Optimal'); $e.LastWriteTime = $fixed
        $s = $e.Open(); try { $s.Write($bytes, 0, $bytes.Length) } finally { $s.Dispose() }
    }
    Add-Entry 'fabric.mod.json' ([Text.Encoding]::UTF8.GetBytes(($meta | ConvertTo-Json -Depth 5)))
    Add-Entry "META-INF/jars/PeakCore-$Version.jar" ([IO.File]::ReadAllBytes($core))
    Add-Entry "META-INF/jars/PeakSound-$Version.jar" ([IO.File]::ReadAllBytes($sound))
    foreach ($lic in 'LICENSE', 'LICENSE.txt') { $p = Join-Path $root $lic; if (Test-Path $p) { Add-Entry 'LICENSE' ([IO.File]::ReadAllBytes($p)); break } }
}
finally { $zip.Dispose() }
Write-Host ("  PeakVerse-Mod -> {0}  ({1:N0} Bytes)" -f $out, (Get-Item $out).Length)