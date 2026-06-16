# 仅打包（不上传）— 用于 WinSCP / 阿里云 Workbench 文件上传
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root
$out = Join-Path $Root "deploy\hearmind-deploy.tar.gz"
if (Test-Path $out) { Remove-Item $out -Force }
tar -czf $out `
    --exclude=.git `
    --exclude=backend/target `
    --exclude=./backend/storage `
    --exclude=frontend/node_modules `
    --exclude=frontend/dist `
    --exclude=config/cookies.txt `
    --exclude=.env `
    --exclude=.env.deploy `
    -C $Root .
Write-Host "Created: $out ($([math]::Round((Get-Item $out).Length/1MB,1)) MB)"
