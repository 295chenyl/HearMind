# One-time: install local SSH public key on ECS for passwordless deploy.
# Usage: .\deploy\setup-ssh-key.ps1

param(
    [string]$EcsIp = "120.55.181.0",
    [string]$SshUser = "root",
    [string]$PrivateKey = "",
    [string]$PublicKeyFile = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PrivateKey)) {
    $PrivateKey = Join-Path $env:USERPROFILE ".ssh\id_ed25519"
}
if ([string]::IsNullOrWhiteSpace($PublicKeyFile)) {
    $PublicKeyFile = "$PrivateKey.pub"
}

if (-not (Test-Path $PublicKeyFile)) {
    Write-Host "ERROR: public key not found: $PublicKeyFile" -ForegroundColor Red
    Write-Host "Run: ssh-keygen -t ed25519 -f `$env:USERPROFILE\.ssh\id_ed25519" -ForegroundColor Yellow
    exit 1
}

$Remote = "${SshUser}@${EcsIp}"

Write-Host ">>> Install public key on $Remote" -ForegroundColor Cyan
Write-Host ">>> Key file: $PublicKeyFile" -ForegroundColor DarkGray
Write-Host ">>> Enter ECS password once..." -ForegroundColor Yellow

$installCmd = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && cat >> ~/.ssh/authorized_keys"

Get-Content -Path $PublicKeyFile -Raw | & ssh.exe -o ConnectTimeout=15 -o StrictHostKeyChecking=no $Remote $installCmd
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: failed to write authorized_keys" -ForegroundColor Red
    exit 1
}

Write-Host ">>> Verify key login..." -ForegroundColor Cyan
& ssh.exe -i $PrivateKey -o BatchMode=yes -o ConnectTimeout=10 -o StrictHostKeyChecking=no $Remote "echo SSH_KEY_OK"
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: key login failed, check /root/.ssh/authorized_keys on ECS" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Passwordless SSH is ready." -ForegroundColor Green
Write-Host "  Next: .\deploy\deploy.ps1" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
