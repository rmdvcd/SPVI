# Genera los tres sonidos de notificación de Shalev (WAV PCM 22 050 Hz, mono, 16 bits).
#
#   powershell -ExecutionPolicy Bypass -File tools/sonidos/generar-sonidos.ps1
#
# Los archivos van a app/src/main/res/raw/: conectada.wav, desconectada.wav, sincronizada.wav.
# Son tonos cortos y claros (se oyen por encima del ruido de un local). Para cambiarlos, sustituye los .wav
# conservando el nombre: la app los usa tal cual.

$ErrorActionPreference = 'Stop'
$destino = Join-Path $PSScriptRoot '..\..\app\src\main\res\raw'
$destino = [System.IO.Path]::GetFullPath($destino)
New-Item -ItemType Directory -Force -Path $destino | Out-Null

$muestra = 22050

function Sonido([double[]]$notas, [double]$msPorNota = 110) {
    # $notas: frecuencias en Hz. Cada nota dura $msPorNota y se le añade un silencio de 40 ms.
    $total = 0
    foreach ($n in $notas) { $total += [int]($muestra * $msPorNota / 1000) + [int]($muestra * 0.04) }
    $muestras = New-Object 'System.Int16[]' $total
    $i = 0
    foreach ($n in $notas) {
        $largo = [int]($muestra * $msPorNota / 1000)
        for ($k = 0; $k -lt $largo; $k++) {
            # Ataque y caída suaves (evita el chasquido).
            $f = 0.35 + 0.65 * [Math]::Sin([Math]::PI * $k / $largo)
            $muestras[$i++] = [int16](14000 * $f * [Math]::Sin(2 * [Math]::PI * $n * $k / $muestra))
        }
        for ($k = 0; $k -lt [int]($muestra * 0.04); $k++) { $muestras[$i++] = 0 }
    }
    return $muestras
}

function Guardar([string]$nombre, [double[]]$notas, [double]$msPorNota = 110) {
    $datos = Sonido $notas $msPorNota
    $bytes = New-Object 'System.Byte[]' ($datos.Length * 2)
    for ($i = 0; $i -lt $datos.Length; $i++) {
        $v = $datos[$i]
        $bytes[$i * 2] = [byte]($v -band 0xFF)
        $bytes[$i * 2 + 1] = [byte](($v -shr 8) -band 0xFF)
    }
    $cabecera = [System.Text.Encoding]::ASCII.GetBytes('RIFF')
    $riff = [System.BitConverter]::GetBytes([int](36 + $bytes.Length))
    $wave = [System.Text.Encoding]::ASCII.GetBytes('WAVEfmt ')
    $fmt = [System.BitConverter]::GetBytes([int]16)
    $audio = [System.BitConverter]::GetBytes([int16]1)
    $canales = [System.BitConverter]::GetBytes([int16]1)
    $freq = [System.BitConverter]::GetBytes([int]$muestra)
    $byteRate = [System.BitConverter]::GetBytes([int]($muestra * 2))
    $align = [System.BitConverter]::GetBytes([int16]2)
    $bits = [System.BitConverter]::GetBytes([int16]16)
    $dato = [System.Text.Encoding]::ASCII.GetBytes('data')
    $tam = [System.BitConverter]::GetBytes([int]$bytes.Length)
    $todo = @($cabecera + $riff + $wave + $fmt + $audio + $canales + $freq + $byteRate + $align + $bits + $dato + $tam + $bytes)
    [System.IO.File]::WriteAllBytes((Join-Path $destino $nombre), $todo)
    Write-Host "$nombre  ($([math]::Round($todo.Length / 1024)) KB)"
}

# Conectada: dos notas que suben (la app se conectó).
Guardar 'conectada.wav' @(784, 1175)
# Desconectada: dos notas que bajan (la app se desconectó).
Guardar 'desconectada.wav' @(659, 440)
# Sincronización terminada: tres notas ascendentes (listo).
Guardar 'sincronizada.wav' @(784, 988, 1319) 90
