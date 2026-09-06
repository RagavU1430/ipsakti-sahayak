Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Starting IP-SAKTI Sahayak (RAG, Backend, Frontend)..." -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan

$rootDir = $PSScriptRoot

# Load .env variables into process environment for child processes
if (Test-Path "$rootDir\.env") {
    Get-Content "$rootDir\.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $varName = $parts[0].Trim()
            $varVal = $parts[1].Trim()
            if (($varVal.StartsWith('"') -and $varVal.EndsWith('"')) -or ($varVal.StartsWith("'") -and $varVal.EndsWith("'"))) {
                $varVal = $varVal.Substring(1, $varVal.Length - 2)
            }
            [System.Environment]::SetEnvironmentVariable($varName, $varVal, "Process")
        }
    }
}

# Clean up existing processes on ports 8000, 8080, 5173
@(8000, 8080, 5173) | ForEach-Object {
    $port = $_
    $pids = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($p in $pids) {
        if ($p -and $p -ne 0) {
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        }
    }
}

# 1. Start Python RAG Service (Port 8000)
Write-Host "[1/3] Launching Python RAG service on port 8000..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$rootDir\ip-sakti-rag'; python -m uvicorn app.api.main:app --host 0.0.0.0 --port 8000 --reload"

# 2. Start Spring Boot Backend (Port 8080)
Write-Host "[2/3] Launching Spring Boot Backend on port 8080..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$rootDir\ip-sakti-backend'; .\mvnw.cmd spring-boot:run"

# 3. Start React Frontend (Port 5173)
Write-Host "[3/3] Launching Vite Frontend on port 5173..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$rootDir\Frontend'; npm run dev"

Write-Host "`nAll 3 services have been launched in dedicated terminal windows!" -ForegroundColor Green
Write-Host "  • Python RAG:  http://localhost:8000" -ForegroundColor Cyan
Write-Host "  • Backend API: http://localhost:8080" -ForegroundColor Cyan
Write-Host "  • Frontend UI: http://localhost:5173/ask" -ForegroundColor Cyan
Write-Host "============================================================`n" -ForegroundColor Cyan
