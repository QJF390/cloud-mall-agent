# ai-service launcher
# Usage:  cd d:/demo/exdemo/ai-service ; .\start.ps1
# Notes are in English on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI,
# so non-ASCII comments can be garbled.

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$python = ".venv\Scripts\python.exe"

if (-not (Test-Path $python)) {
    Write-Host "[1/4] Creating virtualenv .venv ..." -ForegroundColor Cyan
    python -m venv .venv
}

Write-Host "[2/4] Installing dependencies ..." -ForegroundColor Cyan
& $python -m pip install --disable-pip-version-check -q -r requirements.txt

if (-not (Test-Path ".env")) {
    Write-Host "[3/4] .env not found, copied from .env.example (fill LLM_API_KEY later)" -ForegroundColor Yellow
    Copy-Item .env.example .env
}
else {
    Write-Host "[3/4] .env exists, skip" -ForegroundColor DarkGray
}

Write-Host "[4/4] Starting ai-service on http://localhost:8000  (Ctrl+C to stop)" -ForegroundColor Green
& $python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
