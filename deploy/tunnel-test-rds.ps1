# 本机经测试 ECS 转发到「测试」RDS 内网（避免误连生产）
# 用法：另开一个终端保持运行，再启动 BackEnd（profile=local）
#   .\deploy\tunnel-test-rds.ps1
param(
  [string]$Identity = (Join-Path $env:USERPROFILE '.ssh\xby-test-ecs'),
  [string]$EcsHost = '121.40.170.82',
  [string]$EcsUser = 'root',
  [int]$LocalPort = 13306,
  [string]$RdsInternalHost = 'rm-bp15f2a3k5b93293k.mysql.rds.aliyuncs.com',
  [int]$RdsPort = 3306
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Identity)) {
  throw "找不到 SSH 私钥: $Identity"
}

Write-Host "==> 本机 127.0.0.1:${LocalPort} -> ${EcsHost} -> ${RdsInternalHost}:${RdsPort}"
Write-Host "    保持此窗口运行；BackEnd application-local.yml 已指向该端口"
Write-Host "    按 Ctrl+C 结束隧道"
& ssh `
  -i $Identity `
  -o BatchMode=yes `
  -o StrictHostKeyChecking=accept-new `
  -o IdentitiesOnly=yes `
  -o ServerAliveInterval=30 `
  -N `
  -L "${LocalPort}:${RdsInternalHost}:${RdsPort}" `
  "${EcsUser}@${EcsHost}"
