# HearMind - pack, upload, deploy to Alibaba Cloud ECS
# Usage: .\deploy\upload-to-ecs.ps1

param(
    [string]$EcsIp = "120.55.181.0",
    [string]$SshUser = "root",
    [string]$RemoteDir = "/opt/HearMind",
    [string]$SshKey = "",
    [switch]$FullRebuild,
    [switch]$FreshEnv,
    [switch]$SkipSecrets
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if ([string]::IsNullOrWhiteSpace($SshKey)) {
    $defaultKey = Join-Path $env:USERPROFILE ".ssh\id_ed25519"
    if (Test-Path $defaultKey) {
        $SshKey = $defaultKey
    }
}

$Remote = "${SshUser}@${EcsIp}"
$UseKey = (-not [string]::IsNullOrWhiteSpace($SshKey)) -and (Test-Path $SshKey)

function Invoke-Ssh {
    param([string]$RemoteCommand)
    if ($UseKey) {
        & ssh.exe -i $SshKey -o ConnectTimeout=15 -o StrictHostKeyChecking=no -o BatchMode=yes $Remote $RemoteCommand
    } else {
        & ssh.exe -o ConnectTimeout=15 -o StrictHostKeyChecking=no $Remote $RemoteCommand
    }
    if ($LASTEXITCODE -ne 0) {
        throw "SSH failed: $RemoteCommand"
    }
}

function Invoke-Scp {
    param([string]$LocalPath, [string]$RemotePath)
    if ($UseKey) {
        & scp.exe -i $SshKey -o ConnectTimeout=60 -o StrictHostKeyChecking=no -o BatchMode=yes $LocalPath "${Remote}:${RemotePath}"
    } else {
        & scp.exe -o ConnectTimeout=60 -o StrictHostKeyChecking=no $LocalPath "${Remote}:${RemotePath}"
    }
    if ($LASTEXITCODE -ne 0) {
        throw "SCP failed: $LocalPath -> $RemotePath"
    }
}

function Test-RemoteFile {
    param([string]$RemotePath)
    if ($UseKey) {
        & ssh.exe -i $SshKey -o ConnectTimeout=15 -o StrictHostKeyChecking=no -o BatchMode=yes $Remote "test -f $RemotePath" 2>$null | Out-Null
    } else {
        & ssh.exe -o ConnectTimeout=15 -o StrictHostKeyChecking=no $Remote "test -f $RemotePath" 2>$null | Out-Null
    }
    return ($LASTEXITCODE -eq 0)
}

Write-Host ("[Deploy] Target: " + $Remote + $RemoteDir) -ForegroundColor Cyan

if ($UseKey) {
    Write-Host ("[Deploy] SSH key: " + $SshKey) -ForegroundColor DarkGray
    & ssh.exe -i $SshKey -o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=no $Remote "echo ok" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "[ERROR] Key login failed. Run once: .\deploy\setup-ssh-key.ps1" -ForegroundColor Red
        exit 1
    }
    Write-Host "[Deploy] SSH key auth OK" -ForegroundColor DarkGray
} else {
    Write-Host "[Deploy] Password mode (multiple prompts)" -ForegroundColor DarkGray
    Write-Host "[Deploy] Tip: run setup-ssh-key.ps1 for passwordless deploy" -ForegroundColor DarkGray
}

$envFile = Join-Path $Root ".env.deploy"
if ($FreshEnv -or -not (Test-Path $envFile)) {
    $dashKey = ""
    $keyPath = Join-Path $Root "config\dashscope.key"
    if (Test-Path $keyPath) {
        foreach ($line in Get-Content $keyPath) {
            $t = $line.Trim()
            if ($t -and -not $t.StartsWith("#") -and $t.StartsWith("sk-")) {
                $dashKey = $t
                break
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($dashKey)) {
        Write-Host "[WARN] no sk- key in config/dashscope.key" -ForegroundColor Yellow
    }
    $mysqlPass = "Hm_" + [guid]::NewGuid().ToString("N").Substring(0, 16)
    $jwtSecret = "hearmind-jwt-" + [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")
    $envLines = @(
        "MYSQL_ROOT_PASSWORD=$mysqlPass",
        "DASHSCOPE_API_KEY=$dashKey",
        "AUTH_JWT_SECRET=$jwtSecret",
        "HTTP_PORT=80"
    )
    [System.IO.File]::WriteAllLines($envFile, $envLines, [System.Text.UTF8Encoding]::new($false))
    Write-Host "[Deploy] Generated env.deploy" -ForegroundColor Green
} else {
    Write-Host "[Deploy] Reusing env.deploy (-FreshEnv to regenerate)" -ForegroundColor DarkGray
}

$archive = Join-Path $env:TEMP "hearmind-deploy.tar.gz"
if (Test-Path $archive) { Remove-Item $archive -Force }

Write-Host "[Deploy] Packing..."
tar -czf $archive `
    --exclude=.git `
    --exclude=backend/target `
    --exclude=./backend/storage `
    --exclude=frontend/node_modules `
    --exclude=frontend/dist `
    --exclude=config/cookies.txt `
    --exclude=.env `
    --exclude=.env.deploy `
    -C $Root .

$sizeMb = [math]::Round((Get-Item $archive).Length / 1MB, 1)
Write-Host ("[Deploy] Archive " + $sizeMb + " MB")

Write-Host "[Deploy] Testing SSH..."
Invoke-Ssh "mkdir -p $RemoteDir/config"

Write-Host "[Deploy] Uploading code..."
Invoke-Scp $archive "/tmp/hearmind-deploy.tar.gz"

$remoteHasEnv = Test-RemoteFile "${RemoteDir}/.env"
if (-not $remoteHasEnv -or $FreshEnv) {
    Write-Host "[Deploy] Uploading env..."
    Invoke-Scp $envFile "${RemoteDir}/.env"
} else {
    Write-Host "[Deploy] Keeping remote env" -ForegroundColor DarkGray
}

if (-not $SkipSecrets) {
    if (Test-Path "config\dashscope.key") {
        Write-Host "[Deploy] Uploading dashscope.key..."
        Invoke-Scp "config\dashscope.key" "${RemoteDir}/config/dashscope.key"
    }
    if (Test-Path "config\bilibili.cookies.txt") {
        Write-Host "[Deploy] Uploading bilibili.cookies.txt..."
        Invoke-Scp "config\bilibili.cookies.txt" "${RemoteDir}/config/bilibili.cookies.txt"
    }
}

$fixSh = "find $RemoteDir/deploy -name '*.sh' -exec sed -i 's/\r$//' {} +; chmod +x $RemoteDir/deploy/*.sh"
if ($FullRebuild) {
    Write-Host "[Deploy] Remote full rebuild, about 5-15 minutes..."
    $remoteCmd = "set -e; tar -xzf /tmp/hearmind-deploy.tar.gz -C $RemoteDir; $fixSh; bash $RemoteDir/deploy/ecs-rebuild.sh $RemoteDir"
} else {
    Write-Host "[Deploy] Remote build and start, about 5-15 minutes..."
    $remoteCmd = "set -e; tar -xzf /tmp/hearmind-deploy.tar.gz -C $RemoteDir; $fixSh; bash $RemoteDir/deploy/ecs-docker-up.sh"
}

Invoke-Ssh $remoteCmd

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host ("  Open: http://" + $EcsIp) -ForegroundColor Green
Write-Host ("  Env:  " + $envFile) -ForegroundColor Green
Write-Host ("  Logs: ssh " + $Remote + " 'cd " + $RemoteDir + " && docker compose logs -f backend'") -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
