# Helper para navegar y capturar la app Shalev por USB
# Uso: .\tools\promo\ui.ps1 <cmd> [p1] [p2]
param(
  [string]$cmd,
  [string]$p1,
  [string]$p2
)
$ErrorActionPreference = 'Stop'
$dev = '4bc9f8f3'
$dir = 'app_usb_check/promo'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

function Adb($a) { & adb.exe -s $dev @a }

function Get-UiXml {
  Adb @('shell','uiautomator','dump','/sdcard/ui.xml') | Out-Null
  Adb @('pull','/sdcard/ui.xml',"$dir/ui.xml") | Out-Null
  [xml](Get-Content "$dir/ui.xml" -Raw)
}

function Get-Center($bounds) {
  if ($bounds -match '^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$') {
    $l=[int]$Matches[1]; $t=[int]$Matches[2]; $r=[int]$Matches[3]; $b=[int]$Matches[4]
    return [pscustomobject]@{x=[int](($l+$r)/2); y=[int](($t+$b)/2); w=($r-$l); h=($b-$t); l=$l; t=$t; r=$r; b=$b}
  }
  return $null
}

function Find-Node($doc, $needle) {
  $out = @()
  foreach ($n in $doc.SelectNodes('//node')) {
    $t = $n.'text'; $d = $n.'content-desc'
    if (($t -like "*$needle*") -or ($d -like "*$needle*")) {
      $c = Get-Center $n.'bounds'
      if ($c) { $out += [pscustomobject]@{node=$n; c=$c} }
    }
  }
  return $out
}

switch ($cmd) {
  'shot' {
    $out = "$dir/$p1.png"
    Adb @('shell','screencap','-p','/sdcard/s.png') | Out-Null
    Adb @('pull','/sdcard/s.png',$out) | Out-Null
    "shot=$out"
  }
  'taptxt' {
    $doc = Get-UiXml
    $cands = Find-Node $doc $p1
    if ($cands.Count -eq 0) { "NOTFOUND $p1"; foreach ($n in $doc.SelectNodes('//node')) { if ($n.'text' -or $n.'content-desc') { "  n: text='$($n.'text')' desc='$($n.'content-desc')' click=$($n.'clickable') bounds=$($n.'bounds')" } }; exit 2 }
    $idx = 0
    if ($p2) { $idx = [int]$p2 }
    $best = $cands[$idx]
    $c = $best.c
    Adb @('shell','input','tap',$c.x,$c.y)
    "TAPPED '$($best.node.'text')' desc='$($best.node.'content-desc')' @ $($c.x),$($c.y) (cands=$($cands.Count))"
    foreach ($cd in $cands) { " - opt: text='$($cd.node.'text')' desc='$($cd.node.'content-desc')' class=$($cd.node.'class') click=$($cd.node.'clickable') bounds=$($cd.node.'bounds')" }
  }
  'tapid' {
    $doc = Get-UiXml
    $n = $doc.SelectNodes("//node[@resource-id='$p1']") | Select-Object -First 1
    if (-not $n) { "NOTFOUND id $p1"; exit 2 }
    $c = Get-Center $n.'bounds'
    Adb @('shell','input','tap',$c.x,$c.y)
    "TAPPED id $p1 @ $($c.x),$($c.y)"
  }
  'tapxy' { Adb @('shell','input','tap',$p1,$p2); "TAPXY $p1 $p2" }
  'dump' {
    $doc = Get-UiXml
    $lines = @()
    foreach ($n in $doc.SelectNodes('//node')) {
      if ($n.'text' -or $n.'content-desc' -or $n.'clickable' -eq 'true') {
        $lines += ("{0} text='{1}' desc='{2}' id='{3}' click={4} bounds={5}" -f $n.'class', $n.'text', $n.'content-desc', $n.'resource-id', $n.'clickable', $n.'bounds')
      }
    }
    $lines | Set-Content "$dir/nodos.txt" -Encoding UTF8
    "nodos: $($lines.Count) -> $dir/nodos.txt"
  }
  'night' { Adb @('shell','cmd','uimode','night',$p1); "night=$p1" }
  'key' { Adb @('shell','input','keyevent',$p1); "KEY $p1" }
  'back' { Adb @('shell','input','keyevent','4'); "BACK" }
  'wait' { Start-Sleep -Seconds ([int]$p1); "WAIT $p1" }
  'text' { Adb @('shell','input','text',"$p1"); "TEXT $p1" }
  'scroll' {
    $doc = Get-UiXml
    $top = 300; $bot = 1500
    if ($p1 -eq 'down') { Adb @('shell','input','swipe','360','1250','360','450','350') }
    else { Adb @('shell','input','swipe','360','500','360','1300','350') }
    "SCROLL $p1"
  }
  'record' { Adb @('shell','screenrecord','--time-limit',$p1,'--bit-rate','12000000','/sdcard/reel_raw.mp4'); "REC done $p1s" }
  'pullreel' { Adb @('pull','/sdcard/reel_raw.mp4',"$dir/reel_raw.mp4"); "pulled" }
  default { "cmd desconocido: $cmd" }
}
