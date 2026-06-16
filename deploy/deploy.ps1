# Shortcut: deploy to ECS
param(
    [string]$EcsIp = "120.55.181.0",
    [string]$SshUser = "root",
    [string]$RemoteDir = "/opt/HearMind",
    [string]$SshKey = "",
    [switch]$FullRebuild,
    [switch]$FreshEnv,
    [switch]$SkipSecrets
)

& "$PSScriptRoot\upload-to-ecs.ps1" @PSBoundParameters
