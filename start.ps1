$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$pythonPath = Join-Path $projectRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $pythonPath)) { throw 'Install the Python virtual environment described in README.md first.' }
$nodePath = (Get-Command node -ErrorAction Stop).Source
$apiProcess = Start-Process -FilePath $pythonPath -WorkingDirectory $projectRoot -ArgumentList '-m','uvicorn','backend.app:app','--host','127.0.0.1','--port','8000' -WindowStyle Hidden -PassThru
$uiProcess = Start-Process -FilePath $nodePath -WorkingDirectory $projectRoot -ArgumentList 'node_modules/vite/bin/vite.js','--host','127.0.0.1' -WindowStyle Hidden -PassThru
Write-Output "Folio: http://127.0.0.1:5173 (API PID $($apiProcess.Id), UI PID $($uiProcess.Id))"
Write-Output 'Stop these processes when finished. See README.md for AI configuration.'
