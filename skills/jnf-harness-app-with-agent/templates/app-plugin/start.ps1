<#
.SYNOPSIS
  独立产品一键启动：harness 运行时 + 业务插件 + 业务应用（幂等，可重复执行）。

.DESCRIPTION
  顺序：校验 jar → 端口检查 → 起 harness(默认 18100) → /actuator/health=UP
       → install/activate 插件 → PUT 插件配置(app.base-url、dataDir) → 校验 contributions
       → 起应用(默认 18081) → 首页 200 → 写 data/runtime.json → 打印地址。
  harness/应用已健康时直接复用，不重复起进程；任何一步失败打印日志路径与下一步命令，退出码 1。

.PARAMETER ProductRoot
  产品根目录（默认 = scripts 目录的上级）。harness 的进程工作目录固定为它，
  ./data 与 ./logs 才会落在产品内。

.PARAMETER HarnessPort
  产品专用 harness 端口，默认 18100（开发共享底座仍是 8090，不要混用）。

.PARAMETER AppPort
  业务应用端口，默认 18081；多产品并存时顺延（18082、18083…）。

.PARAMETER ApiKey
  底座 API Key。不传时先复用 data/runtime.json 里的，仍没有就新生成；
  生成/传入的 key 只给 harness 与 BFF（服务端），浏览器永远拿不到。

.EXAMPLE
  pwsh -NoProfile -File scripts/start.ps1

.EXAMPLE
  pwsh -NoProfile -File scripts/start.ps1 -HarnessPort 18110 -AppPort 18082

.NOTES
  编码：UTF-8。推荐用 pwsh 7 执行；Windows PowerShell 5.1 请另存为「UTF-8 with BOM」，
  否则中文注释会乱码（代码本身是 ASCII 逻辑，通常仍能跑，但排错时很痛苦）。

  改名清单（改完同步 README）：下面「本产品参数」的 PluginId、entrypoint、三个 jar 名。
#>
param(
    [string]$ProductRoot = (Split-Path -Parent $PSScriptRoot),
    [int]$HarnessPort = 18100,
    [int]$AppPort = 18081,
    [string]$ApiKey = ''
)

$ErrorActionPreference = 'Stop'

# ===== 本产品参数（换项目时改这里；jar 名与 TEMPLATE.md §6 的 finalName 对应）=====
$PluginId = 'demo-bridge'
$PluginEntrypoint = 'com.example.appplugin.plugin.DemoBridgePlugin'
$PluginVersion = '1.0.0'
$HarnessJarRel = 'runtime/jnf-harness-app.jar'
$PluginJarRel = 'runtime/plugins/sample-plugin.jar'
$AppJarRel = 'runtime/app/sample-app.jar'

# ===== 输出辅助 =====
function Fail([string]$Message, [string[]]$Hints = @()) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    foreach ($hint in $Hints) { Write-Host "       $hint" -ForegroundColor Yellow }
    exit 1
}
function Step([string]$Message) { Write-Host "[STEP] $Message" -ForegroundColor Cyan }
function Ok([string]$Message) { Write-Host "[ OK ] $Message" -ForegroundColor Green }
function Skip([string]$Message) { Write-Host "[SKIP] $Message" -ForegroundColor Yellow }

# ===== HTTP / 进程辅助 =====
# 路径含空格时 Start-Process -ArgumentList（字符串拼接模式）要自己加引号，否则会被拆成多个参数。
function Quote-Arg([string]$Value) {
    if ($Value -match '\s') { return '"' + $Value + '"' }
    return $Value
}
# PowerShell 7 才支持 -NoProxy；5.1 走 NO_PROXY 环境变量，这里按版本跳过。
function New-WebParams([string]$Url, [int]$TimeoutSec = 3, [hashtable]$Headers = $null) {
    $params = @{ Uri = $Url; TimeoutSec = $TimeoutSec; UseBasicParsing = $true }
    if ($PSVersionTable.PSVersion.Major -ge 6) { $params.NoProxy = $true }
    if ($Headers -and $Headers.Count -gt 0) { $params.Headers = $Headers }
    return $params
}
function Test-Url([string]$Url, [int]$TimeoutSec = 3, [hashtable]$Headers = $null) {
    try {
        $params = New-WebParams -Url $Url -TimeoutSec $TimeoutSec -Headers $Headers
        $resp = Invoke-WebRequest @params
        return ($resp.StatusCode -eq 200)
    } catch { return $false }
}
# 健康接口必须 body 里 status=UP 才算活；端口在听但没有 HTTP 响应不算。
# 注意：PowerShell 7 的 Invoke-WebRequest.Content 视响应类型可能返回 byte[]，
#       不能直接对 Content 做 -match（对数组做匹配返回的是筛选结果，恒为空）。
function Test-HarnessHealth([string]$BaseUrl, [string]$Key) {
    try {
        $headers = @{}
        if ($Key) { $headers['X-API-Key'] = $Key }
        $params = New-WebParams -Url "$BaseUrl/actuator/health" -TimeoutSec 3 -Headers $headers
        $resp = Invoke-WebRequest @params
        if ($resp.StatusCode -ne 200) { return $false }
        $body = $resp.Content
        if ($body -is [byte[]]) { $body = [System.Text.Encoding]::UTF8.GetString($body) }
        return ([string]$body -match '"status"\s*:\s*"UP"')
    } catch { return $false }
}
function Test-PortListening([int]$Port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
        return ($null -ne $conn)
    } catch { return $false }
}
function Wait-Until([scriptblock]$Probe, [int]$TimeoutSec, [string]$What) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (& $Probe) { return $true }
        Start-Sleep -Seconds 1
    }
    Write-Host "       等待「$What」超时（$TimeoutSec s）" -ForegroundColor Yellow
    return $false
}
function Show-LogTail([string]$Path, [int]$Lines = 15) {
    if (Test-Path -LiteralPath $Path) {
        Write-Host "       ---- $Path 末尾 $Lines 行 ----" -ForegroundColor DarkGray
        Get-Content -LiteralPath $Path -Tail $Lines | ForEach-Object { Write-Host "       $_" -ForegroundColor DarkGray }
    }
}
# 所有 harness 管理端点统一走这里：带 key、校验信封 code=00000。
function Invoke-HarnessApi([string]$BaseUrl, [string]$Key, [string]$Path, [string]$Method = 'Get', $Body = $null) {
    $headers = @{}
    if ($Key) { $headers['X-API-Key'] = $Key }
    $params = New-WebParams -Url "$BaseUrl$Path" -TimeoutSec 20 -Headers $headers
    $params.Method = $Method
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    return Invoke-RestMethod @params
}
function Assert-Envelope($Response, [string]$What) {
    if ($null -eq $Response -or $Response.code -ne '00000') {
        $code = '空响应'
        $info = ''
        if ($null -ne $Response) { $code = $Response.code; $info = $Response.info }
        Fail "$What 失败：code=$code info=$info" @(
            '看 /api/harness/plugins 的 status/runtimeState 与 logs/harness.log',
            '插件安装/激活失败常见原因：entrypoint 不是主类全名、jar 缺 plugin.yaml 或 SPI 文件、sourcePath 不是绝对路径'
        )
    }
}
# runtime.json 是 stop.ps1 的唯一依据：每拉起一个进程就落盘一次，
# 这样启动中途失败（插件/应用起不来）时，stop.ps1 仍能按 PID 收尾，不会留孤儿进程。
function Save-State([string]$Status) {
    $state['status'] = $Status
    $state['savedAt'] = (Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz')
    $state | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $RuntimeJson -Encoding UTF8
}

# ===== 0. 校验 =====
Step '0/6 校验产品目录与运行产物'
if (-not (Test-Path -LiteralPath $ProductRoot)) { Fail "产品目录不存在：$ProductRoot" }
$ProductRoot = (Resolve-Path -LiteralPath $ProductRoot).Path
if ($HarnessPort -eq $AppPort) { Fail "harness 端口与应用端口不能相同（$HarnessPort）" }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Fail '找不到 java（需要 JDK 17+）' @('安装：winget install EclipseAdoptium.Temurin.17.JDK（装完重开终端）')
}

$HarnessJar = Join-Path $ProductRoot $HarnessJarRel
$PluginJar = Join-Path $ProductRoot $PluginJarRel
$AppJar = Join-Path $ProductRoot $AppJarRel
$DataDir = Join-Path $ProductRoot 'data'
$LogDir = Join-Path $ProductRoot 'logs'
$RuntimeJson = Join-Path $DataDir 'runtime.json'
$StopScript = Join-Path $ProductRoot 'scripts\stop.ps1'
# H2 连接串里统一用正斜杠（Windows 反斜杠在 URL 风格字符串里易踩坑）
$RootFwd = $ProductRoot.Replace('\', '/')

$missing = @()
$hints = @()
if (-not (Test-Path -LiteralPath $HarnessJar)) {
    $missing += $HarnessJarRel
    $hints += '构建 harness：在 <harness> 仓库执行 mvn -o -B -DskipTests package，把 jnf-harness-app/target/jnf-harness-app.jar 复制到 runtime/'
    $hints += '或构建分发包：node <harness>/scripts/package-local.mjs，整体复制 dist/jnf-harness-local/ 到 runtime/'
}
if (-not (Test-Path -LiteralPath $PluginJar)) {
    $missing += $PluginJarRel
    $hints += '构建插件：在插件模块执行 mvn -o -B -DskipTests package，把 target/<plugin>.jar 复制到 runtime/plugins/'
}
if (-not (Test-Path -LiteralPath $AppJar)) {
    $missing += $AppJarRel
    $hints += '构建应用：在应用模块执行 mvn -o -B -DskipTests package，把 target/<app>.jar 复制到 runtime/app/'
}
if ($missing.Count -gt 0) {
    Fail ("缺少运行产物：`n       " + ($missing -join "`n       ")) $hints
}

$dirs = @($DataDir, (Join-Path $DataDir 'harness'), (Join-Path $DataDir 'events'), $LogDir)
New-Item -ItemType Directory -Force -Path $dirs | Out-Null

# API Key：显式传入 > runtime.json 复用 > 新生成（持久化在 runtime.json，下次启动复用）
if (-not $ApiKey -and (Test-Path -LiteralPath $RuntimeJson)) {
    try {
        $prev = Get-Content -LiteralPath $RuntimeJson -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($prev -and $prev.apiKey) {
            $ApiKey = [string]$prev.apiKey
            Write-Host '[INFO] 复用 data/runtime.json 中的 API Key'
        }
    } catch {
        Write-Host '[WARN] 读取 data/runtime.json 失败，将生成新 key' -ForegroundColor Yellow
    }
}
if (-not $ApiKey) {
    $ApiKey = (New-Guid).ToString('N')
    Write-Host '[INFO] 已生成新的 harness API Key（写入 data/runtime.json；该文件含密钥，勿外传）'
}

$HarnessBase = "http://127.0.0.1:$HarnessPort"
$AppBase = "http://127.0.0.1:$AppPort"

# 运行状态（早建、分步落盘；Save-State 依赖它）
$state = [ordered]@{
    productRoot = $ProductRoot
    status = 'STARTING'
    startedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz')
    apiKey = $ApiKey
    harness = [ordered]@{
        port = $HarnessPort; baseUrl = $HarnessBase; pid = $null; reused = $false
        jar = $HarnessJarRel; log = 'logs/harness.log'
    }
    app = [ordered]@{
        port = $AppPort; baseUrl = $AppBase; pid = $null; reused = $false
        jar = $AppJarRel; log = 'logs/app.log'
    }
    plugin = [ordered]@{
        id = $PluginId; jar = $PluginJarRel; entrypoint = $PluginEntrypoint; version = $PluginVersion
    }
}

# ===== 1. harness =====
Step "1/6 启动 harness（端口 $HarnessPort）"
$HarnessPid = $null
$HarnessReused = $false
if (Test-HarnessHealth -BaseUrl $HarnessBase -Key $ApiKey) {
    Skip "harness 已健康，复用 $HarnessBase（不是本脚本启动，无 PID）"
    $HarnessReused = $true
} else {
    if (Test-PortListening -Port $HarnessPort) {
        Fail "端口 $HarnessPort 已被占用，但 /actuator/health 不是 UP" @(
            "定位占用：Get-NetTCPConnection -LocalPort $HarnessPort -State Listen | Select-Object OwningProcess",
            '如果占用者不是本产品（开发共享底座在 8090，不该占用 18100），换端口：-HarnessPort 18120',
            '确认是遗留进程后：Stop-Process -Id <pid>（先核对命令行含产品路径）'
        )
    }
    $HarnessOut = Join-Path $LogDir 'harness.log'
    $HarnessErr = Join-Path $LogDir 'harness.err.log'
    $DsUrl = "jdbc:h2:file:$RootFwd/data/harness/jnf-harness;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
    $EventsDir = "$RootFwd/data/events"
    $argLine = (@(
        '-jar', (Quote-Arg $HarnessJar),
        (Quote-Arg "--server.port=$HarnessPort"),
        (Quote-Arg "--spring.datasource.url=$DsUrl"),
        (Quote-Arg "--harness.events.dir=$EventsDir"),
        (Quote-Arg "--harness.auth.api-keys=$ApiKey")
    ) -join ' ')
    Write-Host "[INFO] java $argLine" -ForegroundColor DarkGray
    $sp = @{
        FilePath = 'java'; ArgumentList = $argLine; WorkingDirectory = $ProductRoot
        RedirectStandardOutput = $HarnessOut; RedirectStandardError = $HarnessErr; PassThru = $true
    }
    if ($env:OS -eq 'Windows_NT') { $sp.WindowStyle = 'Hidden' }
    try {
        $proc = Start-Process @sp
    } catch {
        Fail "无法启动 harness 进程：$($_.Exception.Message)" @("检查 java 是否可执行、jar 路径是否可读：$HarnessJar")
    }
    $HarnessPid = $proc.Id
    Write-Host "[INFO] harness PID=$HarnessPid，等待健康检查（最长 90s）..."
    $healthy = Wait-Until -Probe { Test-HarnessHealth -BaseUrl $HarnessBase -Key $ApiKey } -TimeoutSec 90 -What 'harness /actuator/health=UP'
    if (-not $healthy) {
        Show-LogTail -Path $HarnessErr -Lines 20
        Show-LogTail -Path $HarnessOut -Lines 20
        Fail "harness 启动失败（PID $HarnessPid 可能仍在启动中）" @(
            "看日志：$HarnessOut / $HarnessErr",
            '常见原因：H2 文件被另一个实例占用（data/ 不隔离）、端口被 SERVER_PORT 劫持、jar 损坏',
            "确认放弃后停止它：pwsh -NoProfile -File `"$StopScript`"，或 Stop-Process -Id $HarnessPid"
        )
    }
}
Ok "harness UP：$HarnessBase"
$state['harness']['pid'] = $HarnessPid
$state['harness']['reused'] = $HarnessReused
Save-State 'STARTING'   # 先落盘：后续任一步失败，stop.ps1 仍能按 PID 收尾

# ===== 2. 插件 install / activate =====
Step "2/6 安装并激活插件 $PluginId"
$installBody = @{
    pluginId = $PluginId; displayName = $PluginId; pluginVersion = $PluginVersion
    runtimeType = 'JAVA_NATIVE'; sourcePath = $PluginJar; entrypoint = $PluginEntrypoint
}
$installResp = Invoke-HarnessApi -BaseUrl $HarnessBase -Key $ApiKey -Path '/api/harness/plugins/install' -Method 'Post' -Body $installBody
Assert-Envelope $installResp '登记插件'
# install 会把记录重置为 REGISTERED，所以必须再 activate（宿主对已激活幂等）
$activateResp = Invoke-HarnessApi -BaseUrl $HarnessBase -Key $ApiKey -Path '/api/harness/plugins/activate' -Method 'Post' -Body @{ pluginId = $PluginId }
Assert-Envelope $activateResp '激活插件'
Ok "插件已 ACTIVE"

# ===== 3. 插件配置 + contributions =====
Step "3/6 写插件配置（应用地址 + 插件数据目录）"
$pluginDataDir = Join-Path $DataDir $PluginId
New-Item -ItemType Directory -Force -Path $pluginDataDir | Out-Null
$config = @{
    'app.base-url' = "http://127.0.0.1:$AppPort"
    'dataDir' = "$RootFwd/data/$PluginId"
}
$configResp = Invoke-HarnessApi -BaseUrl $HarnessBase -Key $ApiKey -Path "/api/harness/plugins/$PluginId/config" -Method 'Put' -Body $config
Assert-Envelope $configResp '写插件配置'
$contribResp = Invoke-HarnessApi -BaseUrl $HarnessBase -Key $ApiKey -Path "/api/harness/plugins/$PluginId/contributions"
Assert-Envelope $contribResp '读取插件贡献'
$tools = @($contribResp.data.tools)
if ($tools.Count -eq 0) {
    Fail '插件 ACTIVE 但没有任何工具贡献' @('检查插件 configure() 是否先 super.configure(context) 再注册工具；看 logs/harness.log 的插件异常')
}
$toolNames = ($tools | ForEach-Object { $_.name }) -join ', '
Ok "工具 $($tools.Count) 个：$toolNames"

# ===== 4. 应用 =====
Step "4/6 启动应用（端口 $AppPort）"
$AppPid = $null
$AppReused = $false
if (Test-Url -Url "$AppBase/") {
    Skip "应用已可访问，复用 $AppBase/"
    $AppReused = $true
} else {
    if (Test-PortListening -Port $AppPort) {
        Fail "端口 $AppPort 已被占用，但应用首页不可访问" @(
            "定位占用：Get-NetTCPConnection -LocalPort $AppPort -State Listen | Select-Object OwningProcess",
            '多产品并存时换端口：-AppPort 18082（同时 start.ps1 会把新端口写进插件配置）'
        )
    }
    $AppOut = Join-Path $LogDir 'app.log'
    $AppErr = Join-Path $LogDir 'app.err.log'
    $appArgLine = (@(
        '-jar', (Quote-Arg $AppJar),
        (Quote-Arg "--server.port=$AppPort"),
        (Quote-Arg "--harness.base-url=$HarnessBase"),
        (Quote-Arg "--harness.api-key=$ApiKey")
    ) -join ' ')
    Write-Host "[INFO] java $appArgLine" -ForegroundColor DarkGray
    $sp2 = @{
        FilePath = 'java'; ArgumentList = $appArgLine; WorkingDirectory = $ProductRoot
        RedirectStandardOutput = $AppOut; RedirectStandardError = $AppErr; PassThru = $true
    }
    if ($env:OS -eq 'Windows_NT') { $sp2.WindowStyle = 'Hidden' }
    try {
        $appProc = Start-Process @sp2
    } catch {
        Fail "无法启动应用进程：$($_.Exception.Message)" @("检查 java 是否可执行、jar 路径是否可读：$AppJar")
    }
    $AppPid = $appProc.Id
    Write-Host "[INFO] 应用 PID=$AppPid，等待首页 200（最长 90s）..."
    $appOk = Wait-Until -Probe { Test-Url -Url "$AppBase/" } -TimeoutSec 90 -What '应用首页 200'
    if (-not $appOk) {
        Show-LogTail -Path $AppErr -Lines 20
        Show-LogTail -Path $AppOut -Lines 20
        Fail "应用启动失败（PID $AppPid 可能仍在启动中）" @(
            "看日志：$AppOut / $AppErr",
            '常见原因：端口被占、应用 jar 不是可执行 fat jar（缺 spring-boot repackage）、启动类配置错',
            "确认放弃后停止它：pwsh -NoProfile -File `"$StopScript`""
        )
    }
}
Ok "应用可访问：$AppBase/"
$state['app']['pid'] = $AppPid
$state['app']['reused'] = $AppReused
Save-State 'STARTING'

# ===== 5. 落盘 runtime.json =====
Step '5/6 更新 data/runtime.json（stop.ps1 与下次 start 依赖它）'
Save-State 'RUNNING'
Ok "已写入 $RuntimeJson（含 API Key，勿提交/外传）"

# ===== 6. 完成 =====
Step '6/6 启动完成'
Write-Host ''
Write-Host "  应用：    $AppBase/          （业务界面 + 右下角 AI 助手）" -ForegroundColor Green
Write-Host "  harness： $HarnessBase/       （控制台；未配模型时 AI 会报错）" -ForegroundColor Green
Write-Host ''
Write-Host '  体验：打开应用 → 右下角 AI 助手 → 问「列出条目并汇总库存」' -ForegroundColor Cyan
Write-Host '  模型：首次使用先到 harness 控制台「设置 → 模型」添加渠道并点测试，否则对话必报错' -ForegroundColor DarkGray
Write-Host "  停止：pwsh -NoProfile -File `"$StopScript`"" -ForegroundColor DarkGray
Write-Host ''
exit 0
