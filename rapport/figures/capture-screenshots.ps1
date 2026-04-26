# =============================================================================
# Script PowerShell de capture automatique des screenshots du dashboard
# Usage : pwsh capture-screenshots.ps1
# Pré-requis : Google Chrome installé, stack Docker démarrée (docker compose up -d)
# =============================================================================

$ErrorActionPreference = "Stop"

# Localiser Chrome
$chrome = $null
foreach ($p in @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
)) {
    if (Test-Path $p) { $chrome = $p; break }
}
if (-not $chrome) {
    Write-Error "Google Chrome introuvable. Installe-le ou prends les screenshots manuellement."
    exit 1
}
Write-Host "[OK] Chrome trouve : $chrome" -ForegroundColor Green

# Vérifier que le dashboard répond
try {
    $r = Invoke-WebRequest -Uri "http://localhost:3000" -TimeoutSec 5 -UseBasicParsing
    if ($r.StatusCode -ne 200) { throw "HTTP $($r.StatusCode)" }
} catch {
    Write-Error "Dashboard introuvable sur http://localhost:3000. Lance 'docker compose up -d' d'abord."
    exit 1
}
Write-Host "[OK] Dashboard accessible" -ForegroundColor Green

# Attendre 5 sec pour avoir des données
Write-Host "Attente 5s pour accumuler des transactions..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# Dossier de sortie
$out = $PSScriptRoot
Write-Host "Sortie : $out" -ForegroundColor Cyan

# Capture vue d'ensemble
$file = Join-Path $out "dashboard-overview.png"
& $chrome --headless --disable-gpu --hide-scrollbars `
  --window-size=1600,1100 `
  --screenshot="$file" `
  "http://localhost:3000" 2>$null
Write-Host "[OK] $file" -ForegroundColor Green

# Capture plus large pour le flux (largeur 1600, hauteur 1400 pour voir plus de transactions)
$file = Join-Path $out "dashboard-flux.png"
& $chrome --headless --disable-gpu --hide-scrollbars `
  --window-size=1600,1400 `
  --screenshot="$file" `
  "http://localhost:3000" 2>$null
Write-Host "[OK] $file" -ForegroundColor Green

# Pour la modale, le panneau agents et les logs : capture manuelle requise
Write-Host ""
Write-Host "=== Captures restantes (a faire manuellement) ===" -ForegroundColor Yellow
Write-Host "  - dashboard-modal.png  : Ouvre http://localhost:3000, clic sur une transaction, capture la modale" -ForegroundColor Yellow
Write-Host "  - dashboard-agents.png : Zoom sur le panneau d'agents avec Win+Shift+S" -ForegroundColor Yellow
Write-Host "  - logs-jade.png        : 'docker logs --tail 30 pfa-jade-backend' dans une console large, capture" -ForegroundColor Yellow
Write-Host ""
Write-Host "Captures automatiques terminees !" -ForegroundColor Green
