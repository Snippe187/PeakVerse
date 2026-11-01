# Baut alle PeakVerse-Module, sammelt die Jars in dist\ und kopiert die Server-Plugins in den gemeinsamen Test-Server.
# Aufruf:  .\tools\build-all.ps1            (bauen + Tests + Jars sammeln)
#          .\tools\build-all.ps1 -Install   (zusaetzlich Plugins nach run\server\plugins kopieren)
#          .\tools\build-all.ps1 -SkipTests
param([switch]$Install, [switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root 'dist'

# Modul-Ordner mit Gradle-Projekt; Server-Plugin-Jar und Client-Mod-Jar je Modul (relativ zum Modul-Ordner).
# Das Server-Plugin ist ein einziges All-in-One-Jar (PeakCore-<Version>.jar), das Bundle aus Core und Modulen zusammensetzt.
$modules = @(
    @{ Name = 'PeakCore';  Mod = 'core-mod\build\libs' },      # Reihenfolge: Basis zuerst
    @{ Name = 'PeakSound'; Mod = 'fabric-mod\build\libs' },
    @{ Name = 'TexturePeak' },
    @{ Name = 'PeakBoard' },
    @{ Name = 'Bundle';    Plugin = 'build\libs'; Dist = 'PeakCore'; JarPrefix = 'PeakCore' }   # zuletzt: braucht die Module
)

foreach ($m in $modules) {
    $dir = Join-Path $root $m.Name
    if (-not (Test-Path (Join-Path $dir 'gradlew.bat'))) { Write-Host "$($m.Name): kein Gradle-Projekt, uebersprungen"; continue }
    Write-Host "== $($m.Name) bauen"
    Push-Location $dir
    try {
        $task = if ($SkipTests) { 'assemble' } else { 'build' }
        # Windows PowerShell 5.1 wertet jede Ausgabe auf stderr als Fehler; der Exit-Code entscheidet
        $ErrorActionPreference = 'Continue'
        & .\gradlew.bat $task -q
        $code = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($code -ne 0) { throw "$($m.Name): Build fehlgeschlagen (Exit $code)" }
    } finally { Pop-Location }

    foreach ($kind in 'Plugin', 'Mod') {
        if (-not $m[$kind]) { continue }   # Modul ohne Mod-Teil
        $libs = Join-Path $dir $m[$kind]
        # nur das Haupt-Jar, keine Quell-Jars
        $jar = Get-ChildItem $libs -Filter '*.jar' -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch 'sources|dev' } | Sort-Object LastWriteTime | Select-Object -Last 1
        if (-not $jar) { Write-Host "  kein $kind-Jar gefunden in $libs"; continue }
        $distName = if ($m.Dist) { $m.Dist } else { $m.Name }
        $target = Join-Path $dist (Join-Path $distName $kind)
        New-Item -ItemType Directory -Force $target | Out-Null
        Copy-Item $jar.FullName $target -Force
        Write-Host "  $kind -> $target\$($jar.Name)"
        if ($Install -and $kind -eq 'Plugin') {
            $plugins = Join-Path $root 'run\server\plugins'
            New-Item -ItemType Directory -Force $plugins | Out-Null
            # aeltere Jars desselben Moduls (z. B. anderer Dateiname) entfernen, sonst meldet Paper doppelte Plugin-Namen
            Get-ChildItem $plugins -Filter "$($m.JarPrefix)-*.jar" | Where-Object { $_.Name -ne $jar.Name } | Remove-Item -Force
            # PeakSound ist seit dem All-in-One-Jar kein eigenes Plugin mehr; ein altes Jar wuerde den Befehl /peaksound doppelt belegen
            Get-ChildItem $plugins -Filter 'PeakSound-*.jar' | Remove-Item -Force
            Copy-Item $jar.FullName (Join-Path $plugins $jar.Name) -Force
            Write-Host "  installiert in $plugins"
        }
    }
}
Write-Host 'fertig'
