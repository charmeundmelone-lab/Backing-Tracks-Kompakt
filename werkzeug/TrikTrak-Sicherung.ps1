# TrikTrak-Sicherung: Hängt der Mudita per USB am Laptop, wird die TrikTrak-Datenbank (bestand.json)
# kopiert, aber nur, wenn sie sich seit der letzten Kopie geändert hat. Liest nur, ändert am Mudita nichts.
# Ablage wie bei der Werkstatt: TrikTrak-Werkstatt\sicherungen\mudita-JJJJMMTT-HHMMSS.json (die
# Werkstatt zeigt sie auf der Seite "Sicherungen" und kann von dort wiederherstellen). Die neuesten 60 bleiben.
# Läuft als Windows-Aufgabe "TrikTrak-Sicherung" alle 5 Minuten (ohne Fenster über TrikTrak-Sicherung-Start.exe).

$ErrorActionPreference = 'Stop'
$geraet  = 'LD20241004967'
$paket   = 'show.jangregersen.buehne'
$ordner  = Join-Path $env:USERPROFILE 'TrikTrak-Werkstatt'
$ablage  = Join-Path $ordner 'sicherungen'
$log     = Join-Path $ordner 'sicherung.log'
$adb     = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$behalten = 60

function Notiz([string]$text) {
    $zeile = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '  ' + $text
    Add-Content -Path $log -Value $zeile -Encoding UTF8
    # Log klein halten: die letzten 300 Zeilen reichen.
    $alle = @(Get-Content -Path $log -Encoding UTF8)
    if ($alle.Count -gt 400) { $alle | Select-Object -Last 300 | Set-Content -Path $log -Encoding UTF8 }
}

# Startet einen Befehl ohne Fenster und bricht nach $sek Sekunden ab (adb hängt manchmal).
function Lauf([string]$datei, [string]$argumente, [int]$sek) {
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo.FileName = $datei
    $p.StartInfo.Arguments = $argumente
    $p.StartInfo.UseShellExecute = $false
    $p.StartInfo.CreateNoWindow = $true
    $p.StartInfo.RedirectStandardOutput = $true
    $p.StartInfo.RedirectStandardError = $true
    [void]$p.Start()
    $aus = $p.StandardOutput.ReadToEndAsync()
    if (-not $p.WaitForExit($sek * 1000)) { try { $p.Kill() } catch {}; return $null }
    return $aus.Result
}

try {
    if (-not (Test-Path $adb)) { exit 0 }
    New-Item -ItemType Directory -Force -Path $ablage | Out-Null

    $liste = Lauf $adb 'devices' 30
    if ($null -eq $liste -or $liste -notmatch "(?m)^$geraet\s+device\s*$") { exit 0 }   # nicht angeschlossen

    # Rohe Bytes über cmd umleiten (PowerShell würde die Kodierung verändern).
    $tmp = Join-Path $ablage 'mudita-holen.tmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Force }
    $null = Lauf $env:ComSpec "/c `"`"$adb`" -s $geraet exec-out run-as $paket cat files/bestand.json > `"$tmp`"`"" 120
    if (-not (Test-Path $tmp) -or (Get-Item $tmp).Length -lt 100) { Notiz 'Mudita da, aber keine Daten bekommen (übersprungen).'; if (Test-Path $tmp) { Remove-Item $tmp -Force }; exit 0 }

    # Nur echte, lesbare Daten mit Songs ablegen.
    $text = [System.IO.File]::ReadAllText($tmp, [System.Text.Encoding]::UTF8)
    try { $daten = $text | ConvertFrom-Json } catch { $daten = $null }
    if ($null -eq $daten -or $null -eq $daten.songs -or @($daten.songs).Count -eq 0) { Notiz 'Daten vom Mudita nicht lesbar oder ohne Songs (übersprungen).'; Remove-Item $tmp -Force; exit 0 }

    $letzte = Get-ChildItem -Path $ablage -Filter 'mudita-*.json' | Sort-Object Name -Descending | Select-Object -First 1
    if ($letzte -and (Get-FileHash $letzte.FullName).Hash -eq (Get-FileHash $tmp).Hash) { Remove-Item $tmp -Force; exit 0 }   # unverändert

    $ziel = Join-Path $ablage ('mudita-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json')
    Move-Item -Path $tmp -Destination $ziel -Force
    Notiz ("gesichert: " + (Split-Path $ziel -Leaf) + "  (" + @($daten.songs).Count + " Songs, " + @($daten.gigs).Count + " Gigs)")

    Get-ChildItem -Path $ablage -Filter 'mudita-*.json' | Sort-Object Name -Descending | Select-Object -Skip $behalten | Remove-Item -Force
} catch {
    try { Notiz ('Fehler: ' + $_.Exception.Message) } catch {}
}
exit 0
