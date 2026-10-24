<#
.SYNOPSIS
  独立产品一键停止：只停本产品自己启动的进程，不误杀别的 java。

.DESCRIPTION
  读 data/runtime.json 里的 PID 与端口，逐个校验：
    1) PID 还存在；2) 进程名是 java；3) 命令行包含产品根路径。
  三条都满足才 Stop-Process；任一不满足就跳过并告警（PID 可能被系统复用，宁可不杀）。
  最后把 runtime.json 标记为 STOPPED，并确认 harness/应用端口已释放。

.PARAMETER ProductRoot
  产品根目录（默认 = scripts 目录的上级），用于定位 data/runtime.json 与校验命令行。

.PARAMETER Force
  跳过「命令行包含产品路径」的二次校验（仅在你确认 PID 归属时使用；默认不开）。

.EXAMPLE
  pwsh -NoProfile -File scripts/stop.ps1

.NOTES
  编码 UTF-8，推荐 pwsh 7。停止后端口仍被监听时，脚本会给定位命令，不会强杀陌生进程。
#>
param(
    [string]$ProductRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

function Warn([string]$Message) { Write-Host "[WARN] $Message" -ForegroundColor Yellow }
function Ok([string]$Message) { Write-Host "[ OK ] $Message" -ForegroundColor Green }
function Skip([string]$Message) { Write-Host "[SKIP] $Message" -ForegroundColor DarkGray }
function Step([string]$Message) { Write-Host "[STEP] $Message" -ForegroundColor Cyan }

function Test-PortListening([int]$Port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
        return ($null -ne $conn)
    } catch { return $false }
}

if (-not (Test-Path -LiteralPath $ProductRoot)) {
    Write-Host "[FAIL] 产品目录不存在：$ProductRoot" -ForegroundColor Red
    exit 1
}
$ProductRoot = (Resolve-Path -LiteralPath $ProductRoot).Path
$RuntimeJson = Join-Path $ProductRoot 'data/runtime.json'
if (-not (Test-Path -LiteralPath $RuntimeJson)) {
    Write-Host "[FAIL] 找不到 data/runtime.json：无法确定 PID，不敢按端口盲杀" -ForegroundColor Red
    Write-Host '       这台机器上产品可能从未用 start.ps1 启动过，或已被手工清理。' -ForegroundColor Yellow
    Write-Host '       手工确认：Get-NetTCPConnection -LocalPort 18100,18081 -State Listen | Select-Object LocalPort,OwningProcess' -ForegroundColor Yellow
    Write-Host '       核对命令行后再决定是否 Stop-Process -Id <pid>。' -ForegroundColor Yellow
    exit 1
}

$state = Get-Content -LiteralPath $RuntimeJson -Raw -Encoding UTF8 | ConvertFrom-Json
Step "停止产品：$ProductRoot"

# 逐个停止：app 先停（用户可见面），harness 后停（插件挂在其上）
$targets = @()
if ($null -ne $state.app) { $targets += [pscustomobject]@{ Name = '应用'; Entry = $state.app } }
if ($null -ne $state.harness) { $targets += [pscustomobject]@{ Name = 'harness'; Entry = $state.harness } }

$killed = @()
foreach ($target in $targets) {
    $name = $target.Name
    $entry = $target.Entry
    if ($null -eq $entry.pid) {
        Skip "$name 无 PID 记录（启动时复用了已有进程），跳过"
        continue
    }
    $procId = [int]$entry.pid
    $ci = Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -ErrorAction SilentlyContinue
    if ($null -eq $ci) {
        Skip "$name PID $procId 已不存在（可能已退出）"
        continue
    }
    if ($ci.Name -notmatch '^java(w)?\.exe$') {
        Warn "$name PID $procId 当前是 $($ci.Name)，不是 java：跳过，避免误杀"
        continue
    }
    $cmdline = [string]$ci.CommandLine
    if (-not $Force -and $cmdline.IndexOf($ProductRoot, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
        Warn "$name PID $procId 命令行不含产品路径，跳过（确认归属后可用 -Force 或手工 Stop-Process）"
        continue
    }
    Write-Host "[INFO] 停止 $name PID $procId" -ForegroundColor Cyan
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $deadline) {
        if ($null -eq (Get-Process -Id $procId -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 300
    }
    if ($null -ne (Get-Process -Id $procId -ErrorAction SilentlyContinue)) {
        Warn "$name PID $procId 10s 内未退出，手工确认：Get-Process -Id $procId"
    } else {
        $killed += $name
        Ok "$name 已停止"
    }
}

# 标记状态（保留 apiKey：下次 start.ps1 会复用它，避免无谓换 key）
# 注意：ConvertFrom-Json 得到的 PSCustomObject 不能直接加新属性，用 Add-Member -Force（旧文件缺字段也安全）
$stoppedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz')
$state | Add-Member -NotePropertyName status -NotePropertyValue 'STOPPED' -Force
$state | Add-Member -NotePropertyName stoppedAt -NotePropertyValue $stoppedAt -Force
if ($null -ne $state.harness) { $state.harness | Add-Member -NotePropertyName pid -NotePropertyValue $null -Force }
if ($null -ne $state.app) { $state.app | Add-Member -NotePropertyName pid -NotePropertyValue $null -Force }
$state | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $RuntimeJson -Encoding UTF8
Ok "data/runtime.json 已标记 STOPPED"

# 端口释放校验：仍在听说明有遗留进程或另一产品占用，给出定位命令而不是继续杀
$ports = @()
foreach ($entry in @($state.harness, $state.app)) {
    if ($null -ne $entry -and $null -ne $entry.port) { $ports += [int]$entry.port }
}
$remaining = @($ports | Where-Object { Test-PortListening -Port $_ } | Sort-Object -Unique)
if ($remaining.Count -eq 0) {
    Ok "端口已释放：$($ports -join ', ')"
    exit 0
}
Warn "端口仍在监听：$($remaining -join ', ')（可能有遗留进程或其它产品占用）"
foreach ($port in $remaining) {
    Write-Host "       Get-NetTCPConnection -LocalPort $port -State Listen | Select-Object LocalPort,OwningProcess" -ForegroundColor Yellow
}
Write-Host '       核对命令行后再 Stop-Process -Id <pid>；不确认归属不要杀。' -ForegroundColor Yellow
exit 1
