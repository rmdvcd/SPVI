$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
python -m venv .venv
if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el entorno Python" }
& .\.venv\Scripts\python.exe -m pip install -r requirements.txt pyinstaller==6.16.0
if ($LASTEXITCODE -ne 0) { throw "No se pudieron instalar las dependencias" }
$env:PYTHONPATH = $PSScriptRoot
& .\.venv\Scripts\python.exe -m unittest discover -s tests -v
if ($LASTEXITCODE -ne 0) { throw "Las pruebas fallaron; no se genera el ejecutable" }
& .\.venv\Scripts\python.exe -m PyInstaller --noconfirm --clean --onefile --name SPVI --collect-data cheroot --collect-data reportlab --hidden-import spvi_web.selftest --hidden-import qrcode.image.pil --add-data "spvi_web/templates;spvi_web/templates" --add-data "spvi_web/static;spvi_web/static" launcher.py
if ($LASTEXITCODE -ne 0) { throw "No se pudo generar SPVI.exe" }
& .\dist\SPVI.exe --self-test
if ($LASTEXITCODE -ne 0) { throw "El ejecutable no superó la prueba de funcionamiento" }
$hash = (Get-FileHash .\dist\SPVI.exe -Algorithm SHA256).Hash.ToLower()
"$hash  SPVI.exe" | Set-Content -Encoding ascii .\dist\SPVI.exe.sha256
Copy-Item README.md .\dist\LEEME.md
Write-Host "Ejecutable verificado: dist\SPVI.exe. Para evaluación: SPVI.exe --local"
